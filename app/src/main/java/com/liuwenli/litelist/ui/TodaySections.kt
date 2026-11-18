package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.domain.TaskRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class TodaySections(
    val pending: List<TaskEntity>,
    val completed: List<TaskEntity>,
    val pastPending: List<TaskEntity>,
) {
    val progressDone: Int get() = completed.size
    val progressTotal: Int get() = pending.size + completed.size
}

/** A single sorting rule for Today's pending tasks and each historical day. */
object TodayTaskSections {
    fun from(tasks: List<TaskEntity>, today: LocalDate, zone: ZoneId): TodaySections {
        val active = tasks.filter { it.status == "PENDING" || it.status == "COMPLETED" }
        val pendingOrder = compareBy<TaskEntity> { it.deadlineLocalDateTime == null }
            .thenBy { it.deadlineLocalDateTime?.let { local -> TaskRules.resolve(LocalDateTime.parse(local), zone) } }
            .thenBy { it.createdAt }
            .thenBy { it.taskId }
        val completedOrder = compareByDescending<TaskEntity> { it.completedAt }
            .thenBy { it.taskId }
        val pastOrder = compareByDescending<TaskEntity> { it.plannedDate }
            .then(pendingOrder)

        return TodaySections(
            pending = active.filter { it.status == "PENDING" && it.plannedDate == today.toString() }.sortedWith(pendingOrder),
            completed = active.filter { it.status == "COMPLETED" && it.plannedDate == today.toString() }.sortedWith(completedOrder),
            pastPending = active.filter { it.status == "PENDING" && LocalDate.parse(it.plannedDate).isBefore(today) }.sortedWith(pastOrder),
        )
    }
}

