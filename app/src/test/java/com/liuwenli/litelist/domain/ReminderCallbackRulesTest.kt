package com.liuwenli.litelist.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderCallbackRulesTest {
    @Test fun boundariesUseElapsedMinutes() {
        val at = Instant.parse("2026-11-20T10:00:00Z")
        assertFalse(ReminderCallbackRules.withinDeliveryWindow(at, at.minusMillis(1)))
        assertTrue(ReminderCallbackRules.withinDeliveryWindow(at, at))
        assertTrue(ReminderCallbackRules.withinDeliveryWindow(at, at.plusSeconds(3_600)))
        assertFalse(ReminderCallbackRules.withinDeliveryWindow(at, at.plusSeconds(3_600).plusMillis(1)))
    }
}

