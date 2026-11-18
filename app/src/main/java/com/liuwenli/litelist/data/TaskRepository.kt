package com.liuwenli.litelist.data

import androidx.room.withTransaction
import com.liuwenli.litelist.domain.TaskRules
import com.liuwenli.litelist.domain.RepeatRules
import com.liuwenli.litelist.domain.TemplatePropagationRules
import com.liuwenli.litelist.domain.RepeatLifecycleRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class StaleTaskException : IllegalStateException("任务已变化，请重新加载")
class StaleTemplateException : IllegalStateException("计划已变化，请重新加载")

class TaskRepository(private val database: LiteListDatabase, private val clock: Clock) {
    private val dao = database.taskDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeTasks(): Flow<List<TaskEntity>> = dao.observePointer().flatMapLatest { pointer ->
        if (pointer == null) flowOf(emptyList()) else dao.observeActiveTasks(pointer.generationId)
    }

    fun observeTheme(): Flow<String> = dao.observePointer().map { it?.themeMode ?: "SYSTEM" }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeTemplates(): Flow<List<RepeatTemplateEntity>> = dao.observePointer().flatMapLatest { pointer ->
        if (pointer == null) flowOf(emptyList()) else dao.observeTemplates(pointer.generationId)
    }

    suspend fun setTheme(mode: String) {
        require(mode == "SYSTEM" || mode == "LIGHT" || mode == "DARK") { "无效主题" }
        database.withTransaction {
            val pointer = ensurePointer()
            if (pointer.themeMode != mode) {
                check(dao.setTheme(pointer.generationId, mode) == 1)
                check(dao.bumpRevision(pointer.generationId) == 1)
            }
        }
    }

    suspend fun create(title: String, note: String?, plannedDate: LocalDate, deadline: LocalDateTime?): TaskEntity {
        val cleanTitle = TaskRules.validateTask(title, note, plannedDate, deadline)
        return database.withTransaction {
            val pointer = ensurePointer()
            val task = TaskEntity(
                generationId = pointer.generationId,
                taskId = UUID.randomUUID().toString(),
                title = cleanTitle,
                note = note,
                plannedDate = plannedDate.toString(),
                deadlineLocalDateTime = deadline?.toString(),
                status = "PENDING",
                completedAt = null,
                createdAt = clock.instant().toString(),
            )
            dao.insertTask(task)
            check(dao.bumpRevision(pointer.generationId) == 1)
            task
        }
    }

    suspend fun createDailyTemplate(title: String, note: String?, startDate: LocalDate?, endDate: LocalDate?): RepeatTemplateEntity {
        val cleanTitle = TaskRules.normalizedTitle(title)
        TaskRules.validateNote(note)
        RepeatRules.validateRange(startDate, endDate)
        return database.withTransaction {
            val pointer = ensurePointer()
            val template = RepeatTemplateEntity(
                generationId = pointer.generationId,
                templateId = UUID.randomUUID().toString(),
                title = cleanTitle,
                note = note,
                startDate = startDate?.toString(),
                endDate = endDate?.toString(),
                createdAt = clock.instant().toString(),
            )
            dao.insertTemplate(template)
            generateMissing(template, LocalDate.now(clock))
            check(dao.bumpRevision(pointer.generationId) == 1)
            template
        }
    }

    suspend fun editDailyTemplate(
        generationId: String,
        templateId: String,
        expectedVersion: Long,
        title: String,
        note: String?,
    ): Int = database.withTransaction {
        val pointer = dao.activePointer() ?: throw StaleTemplateException()
        if (pointer.generationId != generationId) throw StaleTemplateException()
        val template = dao.findTemplate(generationId, templateId) ?: throw StaleTemplateException()
        if (template.isDeleted || template.status == "ENDED" || template.dataVersion != expectedVersion) throw StaleTemplateException()
        val cleanTitle = TaskRules.normalizedTitle(title)
        TaskRules.validateNote(note)
        if (template.title == cleanTitle && template.note == note) return@withTransaction 0
        if (dao.editTemplate(generationId, templateId, expectedVersion, cleanTitle, note) != 1) throw StaleTemplateException()
        val operationDate = LocalDate.now(clock)
        val eligible = dao.futureTemplateInstances(generationId, templateId, operationDate.toString())
            .filter { TemplatePropagationRules.canReceive(LocalDate.parse(it.originalDate), operationDate, it.lifecycleStatus, it.isCustomized) }
        eligible.forEach { instance ->
            check(dao.applyTemplateContentToTask(generationId, instance.taskId, cleanTitle, note) == 1)
        }
        check(dao.bumpRevision(generationId) == 1)
        eligible.size
    }

    suspend fun pauseDailyTemplate(generationId: String, templateId: String, expectedVersion: Long): Int = database.withTransaction {
        val pointer = dao.activePointer() ?: throw StaleTemplateException()
        if (pointer.generationId != generationId) throw StaleTemplateException()
        val template = dao.findTemplate(generationId, templateId) ?: throw StaleTemplateException()
        if (template.isDeleted || template.status != "ACTIVE" || template.dataVersion != expectedVersion) throw StaleTemplateException()
        val operationDate = LocalDate.now(clock)
        if (dao.changeTemplateStatus(generationId, templateId, expectedVersion, "ACTIVE", "PAUSED") != 1) throw StaleTemplateException()
        var cancelled = 0
        for (instance in dao.activeFutureInstances(generationId, templateId, operationDate.toString())) {
            val task = dao.findTask(generationId, instance.taskId) ?: error("重复实例缺少任务")
            if (RepeatLifecycleRules.canPause(
                    LocalDate.parse(instance.originalDate), operationDate, instance.lifecycleStatus,
                    task.status, instance.isCustomized, instance.retentionProtection, task.postponeCount,
                )) {
                check(dao.cancelInstanceForPause(generationId, instance.instanceId, instance.lifecycleVersion) == 1)
                cancelled++
            }
        }
        check(dao.bumpRevision(generationId) == 1)
        cancelled
    }

    suspend fun resumeDailyTemplate(generationId: String, templateId: String, expectedVersion: Long): Int = database.withTransaction {
        val pointer = dao.activePointer() ?: throw StaleTemplateException()
        if (pointer.generationId != generationId) throw StaleTemplateException()
        val template = dao.findTemplate(generationId, templateId) ?: throw StaleTemplateException()
        if (template.isDeleted || template.status != "PAUSED" || template.dataVersion != expectedVersion) throw StaleTemplateException()
        val today = LocalDate.now(clock)
        val allowedDates = RepeatRules.windowDates(
            today, template.startDate?.let(LocalDate::parse), template.endDate?.let(LocalDate::parse),
        ).toSet()
        if (dao.changeTemplateStatus(generationId, templateId, expectedVersion, "PAUSED", "ACTIVE") != 1) throw StaleTemplateException()
        var resumed = 0
        for (instance in dao.pausedWindowInstances(generationId, templateId, today.toString(), today.plusDays(6).toString())) {
            val task = dao.findTask(generationId, instance.taskId) ?: error("重复实例缺少任务")
            val originalDate = LocalDate.parse(instance.originalDate)
            if (originalDate in allowedDates && RepeatLifecycleRules.canResume(
                    originalDate, today, instance.lifecycleStatus, instance.cancellationReason,
                    task.status, instance.isCustomized, instance.retentionProtection,
                )) {
                check(dao.applyTemplateContentToTask(generationId, instance.taskId, template.title, template.note) == 1)
                check(dao.resumePausedInstance(generationId, instance.instanceId, instance.lifecycleVersion) == 1)
                resumed++
            }
        }
        val created = generateMissing(template, today)
        check(dao.bumpRevision(generationId) == 1)
        resumed + created
    }

    suspend fun endDailyTemplate(generationId: String, templateId: String, expectedVersion: Long, deletePlan: Boolean): Int = database.withTransaction {
        val pointer = dao.activePointer() ?: throw StaleTemplateException()
        if (pointer.generationId != generationId) throw StaleTemplateException()
        val template = dao.findTemplate(generationId, templateId) ?: throw StaleTemplateException()
        if (template.isDeleted || template.status == "ENDED" || template.dataVersion != expectedVersion) throw StaleTemplateException()
        val operationDate = LocalDate.now(clock)
        val reason = if (deletePlan) "PLAN_DELETE" else "STOP"
        if (dao.endTemplate(generationId, templateId, expectedVersion, deletePlan) != 1) throw StaleTemplateException()
        var affected = 0
        for (instance in dao.activeFutureInstances(generationId, templateId, operationDate.toString())) {
            val task = dao.findTask(generationId, instance.taskId) ?: error("重复实例缺少任务")
            val originalDate = LocalDate.parse(instance.originalDate)
            if (RepeatLifecycleRules.needsPrecompletedProtection(
                    originalDate, operationDate, instance.lifecycleStatus, task.status, instance.retentionProtection,
                )) {
                check(dao.protectPrecompletedInstance(generationId, instance.instanceId, instance.lifecycleVersion) == 1)
                affected++
            } else if (RepeatLifecycleRules.canPause(
                    originalDate, operationDate, instance.lifecycleStatus, task.status,
                    instance.isCustomized, instance.retentionProtection, task.postponeCount,
                )) {
                check(dao.cancelInstanceForTerminal(generationId, instance.instanceId, instance.lifecycleVersion, reason) == 1)
                affected++
            }
        }
        for (instance in dao.allPauseCancelledInstances(generationId, templateId)) {
            if (RepeatLifecycleRules.needsTerminalReasonConversion(instance.lifecycleStatus, instance.cancellationReason)) {
                check(dao.convertPauseToTerminalReason(generationId, instance.instanceId, instance.lifecycleVersion, reason) == 1)
                affected++
            }
        }
        check(dao.bumpRevision(generationId) == 1)
        affected
    }

    suspend fun maintainDailyWindow(): Int = database.withTransaction {
        val pointer = dao.activePointer() ?: return@withTransaction 0
        val today = LocalDate.now(clock)
        val count = dao.activeTemplates(pointer.generationId).sumOf { generateMissing(it, today) }
        if (count > 0) check(dao.bumpRevision(pointer.generationId) == 1)
        count
    }

    private suspend fun generateMissing(template: RepeatTemplateEntity, today: LocalDate): Int {
        var created = 0
        val occupied = dao.occupiedOriginalDates(
            template.generationId, template.templateId, today.toString(), today.plusDays(6).toString(),
        ).map(LocalDate::parse).toSet()
        val dates = RepeatRules.missingDates(
            today,
            template.startDate?.let(LocalDate::parse),
            template.endDate?.let(LocalDate::parse),
            occupied,
        )
        for (date in dates) {
            val id = UUID.randomUUID().toString()
            dao.insertTask(TaskEntity(
                generationId = template.generationId,
                taskId = id,
                title = template.title,
                note = template.note,
                plannedDate = date.toString(),
                deadlineLocalDateTime = null,
                status = "PENDING",
                completedAt = null,
                createdAt = clock.instant().toString(),
            ))
            dao.insertInstance(RepeatInstanceEntity(
                generationId = template.generationId,
                instanceId = id,
                taskId = id,
                templateId = template.templateId,
                originalDate = date.toString(),
            ))
            created++
        }
        return created
    }

    suspend fun complete(generationId: String, taskId: String, expectedVersion: Long) =
        transition(generationId, taskId, expectedVersion, "PENDING", "COMPLETED", clock.instant().toString())

    suspend fun edit(
        generationId: String,
        taskId: String,
        expectedVersion: Long,
        title: String,
        note: String?,
        plannedDate: LocalDate,
        deadline: LocalDateTime?,
    ) {
        database.withTransaction {
            val pointer = dao.activePointer() ?: throw StaleTaskException()
            if (pointer.generationId != generationId) throw StaleTaskException()
            val task = dao.findTask(generationId, taskId) ?: throw StaleTaskException()
            if (task.status == "DELETED" || task.dataVersion != expectedVersion) throw StaleTaskException()
            val instance = dao.findInstanceForTask(generationId, taskId)
            if (instance != null && instance.lifecycleStatus != "ACTIVE") throw StaleTaskException()
            val cleanTitle = TaskRules.validateTask(title, note, plannedDate, deadline)
            if (dao.editTask(generationId, taskId, expectedVersion, cleanTitle, note, plannedDate.toString(), deadline?.toString()) != 1) {
                throw StaleTaskException()
            }
            if (instance != null && dao.customizeInstance(generationId, taskId, instance.lifecycleVersion) != 1) throw StaleTaskException()
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    suspend fun reopen(generationId: String, taskId: String, expectedVersion: Long) =
        transition(generationId, taskId, expectedVersion, "COMPLETED", "PENDING", null)

    suspend fun delete(generationId: String, taskId: String, expectedVersion: Long) {
        database.withTransaction {
            val pointer = dao.activePointer() ?: throw StaleTaskException()
            if (pointer.generationId != generationId) throw StaleTaskException()
            val task = dao.findTask(generationId, taskId) ?: throw StaleTaskException()
            if (task.status == "DELETED" || task.dataVersion != expectedVersion) throw StaleTaskException()
            val instance = dao.findInstanceForTask(generationId, taskId)
            if (instance != null) {
                if (instance.lifecycleStatus != "ACTIVE") throw StaleTaskException()
                if (dao.userDeleteInstance(generationId, taskId, instance.lifecycleVersion) != 1) throw StaleTaskException()
            }
            if (dao.transitionTask(generationId, taskId, expectedVersion, task.status, "DELETED", null) != 1) throw StaleTaskException()
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    private suspend fun transition(generationId: String, taskId: String, expectedVersion: Long, from: String, to: String, completedAt: String?) {
        database.withTransaction {
            val pointer = dao.activePointer() ?: throw StaleTaskException()
            if (pointer.generationId != generationId) throw StaleTaskException()
            val instance = dao.findInstanceForTask(generationId, taskId)
            if (instance != null && instance.lifecycleStatus != "ACTIVE") throw StaleTaskException()
            if (dao.transitionTask(generationId, taskId, expectedVersion, from, to, completedAt) != 1) throw StaleTaskException()
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    private suspend fun ensurePointer(): DatasetPointer {
        dao.activePointer()?.let { return it }
        val generation = DatasetGeneration(UUID.randomUUID().toString(), clock.instant().toString())
        dao.insertGeneration(generation)
        val pointer = DatasetPointer(generationId = generation.generationId)
        dao.insertPointer(pointer)
        return pointer
    }
}

