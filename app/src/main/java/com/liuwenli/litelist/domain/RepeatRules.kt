package com.liuwenli.litelist.domain

import java.time.LocalDate

object RepeatRules {
    fun validateRange(startDate: LocalDate?, endDate: LocalDate?) {
        require(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
            "结束日期不能早于开始日期"
        }
    }

    fun windowDates(today: LocalDate, startDate: LocalDate?, endDate: LocalDate?): List<LocalDate> {
        validateRange(startDate, endDate)
        return (0L..6L).map(today::plusDays).filter { date ->
            (startDate == null || !date.isBefore(startDate)) &&
                (endDate == null || !date.isAfter(endDate))
        }
    }

    fun missingDates(today: LocalDate, startDate: LocalDate?, endDate: LocalDate?, occupied: Set<LocalDate>): List<LocalDate> =
        windowDates(today, startDate, endDate).filterNot(occupied::contains)
}

