package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.domain.TaskRules
import com.liuwenli.litelist.domain.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class DateMarker { OVERDUE, PENDING, COMPLETED }

object CalendarTasks {
    fun forDate(tasks: List<TaskEntity>, date: LocalDate, zone: ZoneId): List<TaskEntity> {
        val sameDate = tasks.filter { it.plannedDate == date.toString() }
        return sameDate.filter { it.status == "PENDING" }.sortedWith(TaskOrdering.pending(zone)) +
            sameDate.filter { it.status == "COMPLETED" }.sortedWith(TaskOrdering.completed)
    }

    fun marker(tasks: List<TaskEntity>, date: LocalDate, zone: ZoneId, now: Instant): DateMarker? {
        val sameDate = tasks.filter { it.plannedDate == date.toString() }
        if (sameDate.any { it.status == "PENDING" && TaskRules.isOverdue(
                TaskStatus.PENDING,
                it.deadlineLocalDateTime?.let(LocalDateTime::parse),
                zone,
                now,
            ) }) return DateMarker.OVERDUE
        if (sameDate.any { it.status == "PENDING" }) return DateMarker.PENDING
        if (sameDate.any { it.status == "COMPLETED" }) return DateMarker.COMPLETED
        return null
    }
}

