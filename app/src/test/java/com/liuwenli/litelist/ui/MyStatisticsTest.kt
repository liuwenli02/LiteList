package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyStatisticsTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun emptyEligibleSetHasNoRateButFutureCompletionStillCounts() {
        val stats = TaskStatistics.from(listOf(task("future", "2026-11-19", "COMPLETED")), today)

        assertNull(stats.completionPercent)
        assertEquals(0, stats.eligibleTotal)
        assertEquals(1, stats.totalCompleted)
    }

    @Test
    fun pastAndTodayCountForRateWhileDeletedAndFutureDoNot() {
        val stats = TaskStatistics.from(listOf(
            task("past-done", "2026-11-17", "COMPLETED"),
            task("today-pending", "2026-11-18", "PENDING"),
            task("future-done", "2026-11-19", "COMPLETED"),
            task("deleted", "2026-11-18", "DELETED"),
        ), today)

        assertEquals(1, stats.eligibleCompleted)
        assertEquals(2, stats.eligibleTotal)
        assertEquals(50, stats.completionPercent)
        assertEquals(2, stats.totalCompleted)
    }

    private fun task(id: String, date: String, status: String) = TaskEntity(
        generationId = "g",
        taskId = id,
        title = id,
        note = null,
        plannedDate = date,
        deadlineLocalDateTime = null,
        status = status,
        completedAt = if (status == "COMPLETED") "2026-11-18T00:00:00Z" else null,
        createdAt = "2026-11-01T00:00:00Z",
    )
}

