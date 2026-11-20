package com.liuwenli.litelist.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSchedulePolicyTest {
    @Test fun permissionLossDoesNotChangeSavedReminder() {
        assertEquals(AlarmScheduleKind.EXACT, AlarmSchedulePolicy.choose(true, true))
        assertEquals(AlarmScheduleKind.INEXACT, AlarmSchedulePolicy.choose(true, false))
        assertEquals(AlarmScheduleKind.NOT_SCHEDULED, AlarmSchedulePolicy.choose(false, true))
        assertEquals(AlarmScheduleKind.NOT_SCHEDULED, AlarmSchedulePolicy.choose(false, false))
    }
}

