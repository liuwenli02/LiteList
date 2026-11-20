package com.liuwenli.litelist.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderTaskInput(
    val generationId: String,
    val taskId: String,
    val status: String,
    val reminderMode: String,
    val deadline: LocalDateTime?,
    val deadlineDisabled: Boolean,
    val deadlineScheduleVersion: Long,
    val deadlineTriggeredVersion: Long = 0,
)

data class NormalReminderInput(
    val generationId: String,
    val taskId: String,
    val reminderId: String,
    val localDateTime: LocalDateTime,
    val scheduleVersion: Long,
)

data class ReminderScheduleCandidate(
    val generationId: String,
    val taskId: String,
    val reminderId: String?,
    val kind: String,
    val scheduledAt: Instant,
    val scheduleVersion: Long,
)

object ReminderScheduleRules {
    fun futureCandidates(
        tasks: List<ReminderTaskInput>,
        normalReminders: List<NormalReminderInput>,
        now: Instant,
        zone: ZoneId,
    ): List<ReminderScheduleCandidate> {
        val eligible = tasks.filter { ReminderRules.canNotify(it.status, it.reminderMode) }
            .associateBy { it.generationId to it.taskId }
        val result = mutableListOf<ReminderScheduleCandidate>()
        for (task in eligible.values) {
            if (ReminderRules.deadlineEnabled(task.deadline, task.deadlineDisabled) &&
                task.deadlineTriggeredVersion != task.deadlineScheduleVersion) {
                val at = TaskRules.resolve(task.deadline!!, zone)
                if (at.isAfter(now)) result += ReminderScheduleCandidate(
                    task.generationId, task.taskId, null, "DEADLINE", at, task.deadlineScheduleVersion,
                )
            }
        }
        for (reminder in normalReminders) {
            if (reminder.generationId to reminder.taskId !in eligible) continue
            val at = TaskRules.resolve(reminder.localDateTime, zone)
            if (at.isAfter(now)) result += ReminderScheduleCandidate(
                reminder.generationId, reminder.taskId, reminder.reminderId,
                "NORMAL", at, reminder.scheduleVersion,
            )
        }
        return result.sortedWith(compareBy({ it.scheduledAt }, { it.taskId }, { it.kind }, { it.reminderId.orEmpty() }))
    }
}

