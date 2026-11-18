package com.liuwenli.litelist.domain

import java.time.LocalDate

object RepeatLifecycleRules {
    fun canPause(
        originalDate: LocalDate,
        operationDate: LocalDate,
        lifecycleStatus: String,
        taskStatus: String,
        isCustomized: Boolean,
        retentionProtection: Boolean,
        postponeCount: Int,
    ): Boolean = originalDate.isAfter(operationDate) && lifecycleStatus == "ACTIVE" && taskStatus == "PENDING" &&
        !isCustomized && !retentionProtection && postponeCount == 0

    fun canResume(
        originalDate: LocalDate,
        today: LocalDate,
        lifecycleStatus: String,
        cancellationReason: String?,
        taskStatus: String,
        isCustomized: Boolean,
        retentionProtection: Boolean,
    ): Boolean = originalDate.isAfter(today) && !originalDate.isAfter(today.plusDays(6)) &&
        lifecycleStatus == "SYSTEM_CANCELLED" && cancellationReason == "PAUSE" &&
        taskStatus == "PENDING" && !isCustomized && !retentionProtection
}

