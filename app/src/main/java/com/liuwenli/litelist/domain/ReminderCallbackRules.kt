package com.liuwenli.litelist.domain

import java.time.Instant

object ReminderCallbackRules {
    fun withinDeliveryWindow(scheduledAt: Instant, now: Instant): Boolean =
        !now.isBefore(scheduledAt) && !now.isAfter(scheduledAt.plusSeconds(3_600))
}

