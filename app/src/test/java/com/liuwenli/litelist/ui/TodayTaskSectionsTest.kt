package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTaskSectionsTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun pendingUsesDeadlineThenCreationThenIdentityAndPastDoesNotCount() {
        val tasks = listOf(
            task("z", "2026-11-18", deadline = null),
            task("b", "2026-11-18", deadline = "2026-11-18T09:00", created = "2026-11-01T00:00:00Z"),
            task("a", "2026-11-18", deadline = "2026-11-18T09:00", created = "2026-11-01T00:00:00Z"),
            task("c", "2026-11-18", deadline = "2026-11-18T08:00"),
            task("old", "2026-11-17"),
            task("future", "2026-11-19"),
        )

        val sections = TodayTaskSections.from(tasks.reversed(), today, zone)

        assertEquals(listOf("c", "a", "b", "z"), sections.pending.map { it.taskId })
        assertEquals(listOf("old"), sections.pastPending.map { it.taskId })
        assertEquals(0, sections.progressDone)
        assertEquals(4, sections.progressTotal)
        assertEquals(sections.pending.map { it.taskId }, TodayTaskSections.from(tasks, today, zone).pending.map { it.taskId })
    }

    @Test
    fun completedAndHistoryHaveSeparateOrderAndProgress() {
        val tasks = listOf(
            task("done-old", "2026-11-18", status = "COMPLETED", completed = "2026-11-18T08:00:00Z"),
            task("done-b", "2026-11-18", status = "COMPLETED", completed = "2026-11-18T09:00:00Z"),
            task("done-a", "2026-11-18", status = "COMPLETED", completed = "2026-11-18T09:00:00Z"),
            task("old-b", "2026-11-17", deadline = null),
            task("older", "2026-11-16", deadline = "2026-11-16T07:00"),
            task("old-a", "2026-11-17", deadline = "2026-11-17T12:00"),
            task("deleted", "2026-11-18", status = "DELETED"),
            task("future-done", "2026-11-19", status = "COMPLETED", completed = "2026-11-18T10:00:00Z"),
        )

        val sections = TodayTaskSections.from(tasks, today, zone)

        assertEquals(listOf("done-a", "done-b", "done-old"), sections.completed.map { it.taskId })
        assertEquals(listOf("old-a", "old-b", "older"), sections.pastPending.map { it.taskId })
        assertEquals(3, sections.progressDone)
        assertEquals(3, sections.progressTotal)
    }

    @Test
    fun deadlineSortUsesResolvedInstantAcrossDstGap() {
        val spring = LocalDate.of(2026, 3, 8)
        val tasks = listOf(
            task("after", "2026-03-08", deadline = "2026-03-08T03:01"),
            task("gap", "2026-03-08", deadline = "2026-03-08T02:30", created = "2026-11-01T00:00:00Z"),
            task("first", "2026-03-08", deadline = "2026-03-08T03:00", created = "2026-10-01T00:00:00Z"),
        )

        val sections = TodayTaskSections.from(tasks, spring, ZoneId.of("America/New_York"))

        assertEquals(listOf("first", "gap", "after"), sections.pending.map { it.taskId })
    }

    private fun task(
        id: String,
        date: String,
        deadline: String? = null,
        created: String = "2026-11-02T00:00:00Z",
        status: String = "PENDING",
        completed: String? = null,
    ) = TaskEntity(
        generationId = "g",
        taskId = id,
        title = id,
        note = null,
        plannedDate = date,
        deadlineLocalDateTime = deadline,
        status = status,
        completedAt = completed,
        createdAt = created,
    )
}

