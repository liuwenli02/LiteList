package com.liuwenli.litelist.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePropagationRulesTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun onlyFutureActiveUncustomizedInstancesReceiveTemplateContent() {
        assertTrue(TemplatePropagationRules.canReceive(today.plusDays(1), today, "ACTIVE", false))
        assertFalse(TemplatePropagationRules.canReceive(today, today, "ACTIVE", false))
        assertFalse(TemplatePropagationRules.canReceive(today.minusDays(1), today, "ACTIVE", false))
        assertFalse(TemplatePropagationRules.canReceive(today.plusDays(1), today, "ACTIVE", true))
        assertFalse(TemplatePropagationRules.canReceive(today.plusDays(1), today, "SYSTEM_CANCELLED", false))
        assertFalse(TemplatePropagationRules.canReceive(today.plusDays(1), today, "USER_DELETED", false))
    }
}

