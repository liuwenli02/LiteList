package com.liuwenli.litelist.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatLifecycleRulesTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun pauseOnlyCancelsOrdinaryFuturePendingInstance() {
        fun canPause(date: LocalDate = today.plusDays(1), state: String = "ACTIVE", status: String = "PENDING",
                     customized: Boolean = false, protected: Boolean = false, postponed: Int = 0) =
            RepeatLifecycleRules.canPause(date, today, state, status, customized, protected, postponed)

        assertTrue(canPause())
        assertFalse(canPause(date = today))
        assertFalse(canPause(date = today.minusDays(1)))
        assertFalse(canPause(state = "SYSTEM_CANCELLED"))
        assertFalse(canPause(status = "COMPLETED"))
        assertFalse(canPause(customized = true))
        assertFalse(canPause(protected = true))
        assertFalse(canPause(postponed = 1))
    }

    @Test
    fun resumeRequiresPauseReasonAndCurrentFutureWindow() {
        fun canResume(date: LocalDate = today.plusDays(1), state: String = "SYSTEM_CANCELLED", reason: String? = "PAUSE",
                      status: String = "PENDING", customized: Boolean = false, protected: Boolean = false) =
            RepeatLifecycleRules.canResume(date, today, state, reason, status, customized, protected)

        assertTrue(canResume())
        assertTrue(canResume(date = today.plusDays(6)))
        assertFalse(canResume(date = today))
        assertFalse(canResume(date = today.plusDays(7)))
        assertFalse(canResume(state = "USER_DELETED"))
        assertFalse(canResume(reason = "STOP"))
        assertFalse(canResume(reason = "PLAN_DELETE"))
        assertFalse(canResume(status = "COMPLETED"))
        assertFalse(canResume(customized = true))
        assertFalse(canResume(protected = true))
    }

    @Test
    fun futureCompletedInstanceGetsProtectionWithoutCustomizationRequirement() {
        assertTrue(RepeatLifecycleRules.needsPrecompletedProtection(today.plusDays(1), today, "ACTIVE", "COMPLETED", false))
        assertFalse(RepeatLifecycleRules.needsPrecompletedProtection(today, today, "ACTIVE", "COMPLETED", false))
        assertFalse(RepeatLifecycleRules.needsPrecompletedProtection(today.plusDays(1), today, "ACTIVE", "PENDING", false))
        assertFalse(RepeatLifecycleRules.needsPrecompletedProtection(today.plusDays(1), today, "USER_DELETED", "DELETED", false))
        assertFalse(RepeatLifecycleRules.needsPrecompletedProtection(today.plusDays(1), today, "ACTIVE", "COMPLETED", true))
    }

    @Test
    fun allPauseCancellationReasonsNeedTerminalConversionRegardlessOfDate() {
        assertTrue(RepeatLifecycleRules.needsTerminalReasonConversion("SYSTEM_CANCELLED", "PAUSE"))
        assertFalse(RepeatLifecycleRules.needsTerminalReasonConversion("SYSTEM_CANCELLED", "STOP"))
        assertFalse(RepeatLifecycleRules.needsTerminalReasonConversion("USER_DELETED", null))
    }
}

