package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarTasksTest {
    private val date = LocalDate.of(2026, 11, 18)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-18T02:00:00Z")

    @Test
    fun markerPriorityAndNoDeadlineHistory() {
        val completed = task("done", "2026-11-18", "COMPLETED")
        val pending = task("todo", "2026-11-18")
        val overdue = task("late", "2026-11-18", deadline = "2026-11-18T09:00")

        assertNull(CalendarTasks.marker(emptyList(), date, zone, now))
        assertEquals(DateMarker.COMPLETED, CalendarTasks.marker(listOf(completed), date, zone, now))
        assertEquals(DateMarker.PENDING, CalendarTasks.marker(listOf(completed, pending), date, zone, now))
        assertEquals(DateMarker.OVERDUE, CalendarTasks.marker(listOf(completed, pending, overdue), date, zone, now))
        assertEquals(DateMarker.PENDING, CalendarTasks.marker(listOf(task("old", "2026-11-17")), date.minusDays(1), zone, now))
        assertNull(CalendarTasks.marker(listOf(task("deleted", "2026-11-18", "DELETED")), date, zone, now))
    }

    @Test
    fun dateOwnershipAndOrderFollowCurrentPlannedDate() {
        val tasks = listOf(
            task("completed", "2026-11-18", "COMPLETED"),
            task("no-ddl", "2026-11-18"),
            task("with-ddl", "2026-11-18", deadline = "2026-11-18T09:00"),
            task("moved", "2026-11-19", deadline = "2026-11-19T09:00"),
        )

        assertEquals(listOf("with-ddl", "no-ddl", "completed"), CalendarTasks.forDate(tasks, date, zone).map { it.taskId })
        assertEquals(listOf("moved"), CalendarTasks.forDate(tasks, date.plusDays(1), zone).map { it.taskId })
    }

    private fun task(id: String, plannedDate: String, status: String = "PENDING", deadline: String? = null) = TaskEntity(
        generationId = "g",
        taskId = id,
        title = id,
        note = null,
        plannedDate = plannedDate,
        deadlineLocalDateTime = deadline,
        status = status,
        completedAt = if (status == "COMPLETED") "2026-11-18T00:00:00Z" else null,
        createdAt = "2026-11-01T00:00:00Z",
    )
}

