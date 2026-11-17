package com.liuwenli.litelist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRulesTest {
    @Test fun unicodeBoundaries() {
        assertEquals(1, TaskRules.graphemeCount("e\u0301"))
        assertEquals(1, TaskRules.graphemeCount("👨‍👩‍👧‍👦"))
        assertEquals("标题", TaskRules.normalizedTitle("  标题  "))
        assertEquals(100, TaskRules.graphemeCount("字".repeat(99) + "👨‍👩‍👧‍👦"))
        TaskRules.normalizedTitle("字".repeat(100))
        assertRejects { TaskRules.normalizedTitle("字".repeat(101)) }
        assertRejects { TaskRules.normalizedTitle(" \n ") }
        TaskRules.validateNote("字".repeat(2_000))
        assertRejects { TaskRules.validateNote("字".repeat(2_001)) }
    }

    @Test fun deadlineAndDst() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), TaskRules.resolve(LocalDateTime.parse("2026-03-08T02:30:00"), zone))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), TaskRules.resolve(LocalDateTime.parse("2026-11-01T01:30:00"), zone))
        val deadline = LocalDateTime.parse("2026-11-01T01:30:00")
        assertFalse(TaskRules.isOverdue(TaskStatus.PENDING, deadline, zone, Instant.parse("2026-11-01T05:30:00Z")))
        assertTrue(TaskRules.isOverdue(TaskStatus.PENDING, deadline, zone, Instant.parse("2026-11-01T05:30:01Z")))
        assertFalse(TaskRules.isOverdue(TaskStatus.COMPLETED, deadline, zone, Instant.parse("2026-11-01T06:30:00Z")))
        assertRejects { TaskRules.validateDeadline(LocalDate.parse("2026-11-02"), deadline) }
    }

    private fun assertRejects(block: () -> Unit) {
        try { block(); throw AssertionError("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }
}
