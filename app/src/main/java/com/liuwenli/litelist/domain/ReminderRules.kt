package com.liuwenli.litelist.domain

import java.time.LocalDateTime

object ReminderRules {
    fun normalizeNormalTimes(times: List<LocalDateTime>): List<LocalDateTime> {
        require(times.size == times.distinct().size) { "同一时刻不能重复提醒" }
        return times.sorted()
    }

    fun deadlineEnabled(deadline: LocalDateTime?, disabled: Boolean): Boolean =
        deadline != null && !disabled

    fun canNotify(taskStatus: String, reminderMode: String, instanceStatus: String? = null): Boolean =
        taskStatus == "PENDING" && reminderMode == "ENABLED" &&
            (instanceStatus == null || instanceStatus == "ACTIVE")
}

