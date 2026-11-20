package com.liuwenli.litelist.domain

enum class AlarmScheduleKind { EXACT, INEXACT, NOT_SCHEDULED }

object AlarmSchedulePolicy {
    fun choose(canPostNotifications: Boolean, canScheduleExact: Boolean): AlarmScheduleKind = when {
        !canPostNotifications -> AlarmScheduleKind.NOT_SCHEDULED
        canScheduleExact -> AlarmScheduleKind.EXACT
        else -> AlarmScheduleKind.INEXACT
    }
}

