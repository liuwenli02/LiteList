package com.liuwenli.litelist.data

import androidx.room.withTransaction
import com.liuwenli.litelist.domain.AlarmScheduleKind
import com.liuwenli.litelist.domain.ReminderCallbackRules
import com.liuwenli.litelist.domain.ReminderRules
import com.liuwenli.litelist.domain.ReminderScheduleCandidate
import com.liuwenli.litelist.domain.TaskRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReminderClaim(
    val generationId: String,
    val taskId: String,
    val eventKey: String,
    val scheduledAt: Instant,
    val claimedAtEpochMillis: Long,
    val title: String,
    val hasDeadline: Boolean,
    val deadlineVersion: Long?,
    val normalVersions: Map<String, Long>,
)

class ReminderRepository(private val database: LiteListDatabase, private val clock: Clock) {
    private val dao = database.taskDao()

    fun observeRevision(): Flow<Long> = dao.observePointer().map { it?.contentRevision ?: 0L }

    suspend fun allAlarmIdentities(): List<ReminderScheduleCandidate> = database.withTransaction {
        val generationId = dao.activePointer()?.generationId ?: return@withTransaction emptyList()
        val tasks = dao.allGenerationTasks(generationId).map { task ->
            ReminderScheduleCandidate(generationId, task.taskId, null, "DEADLINE", Instant.EPOCH, task.deadlineScheduleVersion)
        }
        val normal = dao.allGenerationNormalReminders(generationId).map { reminder ->
            ReminderScheduleCandidate(generationId, reminder.taskId, reminder.reminderId, "NORMAL", Instant.EPOCH, reminder.scheduleVersion)
        }
        tasks + normal
    }

    suspend fun recordScheduleResult(candidate: ReminderScheduleCandidate, kind: AlarmScheduleKind) {
        database.withTransaction {
            if (dao.activePointer()?.generationId != candidate.generationId) return@withTransaction
            val status = if (kind == AlarmScheduleKind.NOT_SCHEDULED) "SYNC_FAILED" else "SYNCED"
            if (candidate.kind == "DEADLINE") {
                dao.markDeadlineScheduleResult(candidate.generationId, candidate.taskId, candidate.scheduleVersion, status, kind.name)
            } else {
                dao.markNormalScheduleResult(candidate.generationId, candidate.reminderId ?: return@withTransaction,
                    candidate.scheduleVersion, status, kind.name)
            }
        }
    }

    suspend fun claim(candidate: ReminderScheduleCandidate): ReminderClaim? = database.withTransaction {
        val now = clock.instant()
        if (!ReminderCallbackRules.withinDeliveryWindow(candidate.scheduledAt, now)) return@withTransaction null
        if (dao.activePointer()?.generationId != candidate.generationId) return@withTransaction null
        val task = dao.findTask(candidate.generationId, candidate.taskId) ?: return@withTransaction null
        val instance = dao.findInstanceForTask(candidate.generationId, candidate.taskId)
        if (!ReminderRules.canNotify(task.status, task.reminderMode, instance?.lifecycleStatus)) return@withTransaction null
        val members = members(task, candidate.scheduledAt)
        val matching = if (candidate.kind == "DEADLINE") {
            members.first == candidate.scheduleVersion
        } else {
            members.second[candidate.reminderId] == candidate.scheduleVersion
        }
        if (!matching) return@withTransaction null
        val eventKey = "${task.taskId}@${candidate.scheduledAt.toEpochMilli()}"
        dao.insertReminderEvent(ReminderEventEntity(task.generationId, eventKey, task.taskId, candidate.scheduledAt.toEpochMilli()))
        val claimedAt = now.toEpochMilli()
        if (dao.claimReminderEvent(task.generationId, eventKey, claimedAt, claimedAt - 30_000) != 1) return@withTransaction null
        ReminderClaim(task.generationId, task.taskId, eventKey, candidate.scheduledAt, claimedAt,
            task.title, members.first != null, members.first, members.second)
    }

    suspend fun markPublished(claim: ReminderClaim): Boolean = database.withTransaction {
        if (dao.activePointer()?.generationId != claim.generationId) return@withTransaction false
        val event = dao.findReminderEvent(claim.generationId, claim.eventKey) ?: return@withTransaction false
        if (event.status != "PENDING" || event.claimedAtEpochMillis != claim.claimedAtEpochMillis) return@withTransaction false
        val task = dao.findTask(claim.generationId, claim.taskId) ?: return@withTransaction false
        val instance = dao.findInstanceForTask(claim.generationId, claim.taskId)
        if (!ReminderRules.canNotify(task.status, task.reminderMode, instance?.lifecycleStatus)) return@withTransaction false
        val members = members(task, claim.scheduledAt)
        if (members.first != claim.deadlineVersion || members.second != claim.normalVersions) return@withTransaction false
        claim.deadlineVersion?.let { check(dao.markDeadlineTriggered(claim.generationId, claim.taskId, it) == 1) }
        claim.normalVersions.forEach { (id, version) ->
            check(dao.markNormalTriggered(claim.generationId, id, version) == 1)
        }
        check(dao.publishReminderEvent(claim.generationId, claim.eventKey, claim.claimedAtEpochMillis) == 1)
        check(dao.bumpRevision(claim.generationId) == 1)
        true
    }

    suspend fun releaseClaim(claim: ReminderClaim) {
        dao.releaseReminderEvent(claim.generationId, claim.eventKey, claim.claimedAtEpochMillis)
    }

    suspend fun muteAfterDismiss(generationId: String, taskId: String, eventKey: String) {
        database.withTransaction {
            if (dao.activePointer()?.generationId != generationId) return@withTransaction
            val event = dao.findReminderEvent(generationId, eventKey) ?: return@withTransaction
            if (event.taskId != taskId || event.status != "PUBLISHED") return@withTransaction
            val task = dao.findTask(generationId, taskId) ?: return@withTransaction
            if (task.status == "DELETED" || task.reminderMode == "MUTED") return@withTransaction
            if (dao.setReminderMode(generationId, taskId, task.dataVersion, "MUTED") != 1) return@withTransaction
            dao.invalidateNormalSchedules(generationId, taskId)
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    private suspend fun members(task: TaskEntity, scheduledAt: Instant): Pair<Long?, Map<String, Long>> {
        val deadlineVersion = task.deadlineLocalDateTime?.let(LocalDateTime::parse)
            ?.takeIf { !task.deadlineReminderDisabled && task.deadlineTriggeredVersion != task.deadlineScheduleVersion }
            ?.takeIf { TaskRules.resolve(it, clock.zone) == scheduledAt }
            ?.let { task.deadlineScheduleVersion }
        val normalVersions = dao.allNormalReminders(task.generationId, task.taskId)
            .asSequence()
            .filter { it.logicalStatus == "ENABLED" }
            .filter { TaskRules.resolve(LocalDateTime.parse(it.localDateTime), clock.zone) == scheduledAt }
            .associate { it.reminderId to it.scheduleVersion }
        return deadlineVersion to normalVersions
    }
}

