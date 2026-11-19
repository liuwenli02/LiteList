package com.liuwenli.litelist.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderScheduleRulesTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun futureOnlyAndMutedOrCompletedTasksExcluded() {
        val future = LocalDateTime.parse("2026-11-20T09:00")
        val past = LocalDateTime.parse("2026-11-18T09:00")
        val tasks = listOf(
            ReminderTaskInput("g", "a", "PENDING", "ENABLED", future, false, 3),
            ReminderTaskInput("g", "b", "PENDING", "MUTED", future, false, 1),
            ReminderTaskInput("g", "c", "COMPLETED", "ENABLED", future, false, 1),
        )
        val reminders = listOf(
            NormalReminderInput("g", "a", "r1", past, 1),
            NormalReminderInput("g", "a", "r2", future, 2),
            NormalReminderInput("g", "b", "r3", future, 1),
        )
        val candidates = ReminderScheduleRules.futureCandidates(tasks, reminders, Instant.parse("2026-11-19T00:00:00Z"), zone)
        assertEquals(listOf("DEADLINE", "NORMAL"), candidates.map { it.kind })
        assertEquals(listOf(3L, 2L), candidates.map { it.scheduleVersion })
    }

    @Test fun daylightSavingGapUsesResolvedInstant() {
        val gap = LocalDateTime.parse("2026-03-08T02:30")
        val task = ReminderTaskInput("g", "a", "PENDING", "ENABLED", gap, false, 1)
        val candidates = ReminderScheduleRules.futureCandidates(listOf(task), emptyList(), Instant.parse("2026-03-08T06:59:00Z"), zone)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), candidates.single().scheduledAt)
    }
}

