package com.liuwenli.litelist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderRulesTest {
    @Test fun normalTimesSortAndRejectDuplicatesButMayBeAfterDeadline() {
        val first = LocalDateTime.parse("2026-11-20T09:00")
        val later = LocalDateTime.parse("2026-11-20T18:00")
        assertEquals(listOf(first, later), ReminderRules.normalizeNormalTimes(listOf(later, first)))
        assertThrows(IllegalArgumentException::class.java) {
            ReminderRules.normalizeNormalTimes(listOf(first, first))
        }
    }

    @Test fun deadlineDefaultsOnAndMuteSuppressesEveryKind() {
        val deadline = LocalDateTime.parse("2026-11-20T09:00")
        assertTrue(ReminderRules.deadlineEnabled(deadline, false))
        assertFalse(ReminderRules.deadlineEnabled(deadline, true))
        assertFalse(ReminderRules.deadlineEnabled(null, false))
        assertFalse(ReminderRules.canNotify("PENDING", "MUTED"))
        assertFalse(ReminderRules.canNotify("COMPLETED", "ENABLED"))
        assertFalse(ReminderRules.canNotify("PENDING", "ENABLED", "SYSTEM_CANCELLED"))
        assertTrue(ReminderRules.canNotify("PENDING", "ENABLED", "ACTIVE"))
    }
}

