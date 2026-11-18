package com.liuwenli.litelist.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepeatRulesTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun unboundedTemplateUsesOnlyCurrentSevenDayWindow() {
        val dates = RepeatRules.windowDates(today, null, null)

        assertEquals(7, dates.size)
        assertEquals(today, dates.first())
        assertEquals(today.plusDays(6), dates.last())
    }

    @Test
    fun boundsIntersectWindowWithoutBackfillingHistory() {
        assertEquals(
            listOf(today.plusDays(2), today.plusDays(3)),
            RepeatRules.windowDates(today, today.plusDays(2), today.plusDays(3)),
        )
        assertEquals(emptyList<LocalDate>(), RepeatRules.windowDates(today, null, today.minusDays(1)))
        assertEquals(emptyList<LocalDate>(), RepeatRules.windowDates(today, today.plusDays(7), null))
    }

    @Test
    fun invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RepeatRules.windowDates(today, today.plusDays(1), today)
        }
    }

    @Test
    fun anyOccupiedNaturalIdentityBlocksRegeneration() {
        val occupied = setOf(today, today.plusDays(2), today.plusDays(6))

        val missing = RepeatRules.missingDates(today, null, null, occupied)

        assertEquals(listOf(1L, 3L, 4L, 5L).map(today::plusDays), missing)
        assertEquals(emptyList<LocalDate>(), RepeatRules.missingDates(today, null, null, (0L..6L).map(today::plusDays).toSet()))
    }
}

