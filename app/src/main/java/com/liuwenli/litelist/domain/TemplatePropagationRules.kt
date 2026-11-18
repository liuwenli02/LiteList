package com.liuwenli.litelist.domain

import java.time.LocalDate

object TemplatePropagationRules {
    fun canReceive(originalDate: LocalDate, operationDate: LocalDate, lifecycleStatus: String, isCustomized: Boolean): Boolean =
        originalDate.isAfter(operationDate) && lifecycleStatus == "ACTIVE" && !isCustomized
}

