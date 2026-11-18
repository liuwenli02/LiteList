package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import java.time.LocalDate

data class MyStatistics(val eligibleCompleted: Int, val eligibleTotal: Int, val totalCompleted: Int) {
    val completionPercent: Int? get() = if (eligibleTotal == 0) null else eligibleCompleted * 100 / eligibleTotal
}

object TaskStatistics {
    fun from(tasks: List<TaskEntity>, today: LocalDate): MyStatistics {
        val active = tasks.filter { it.status == "PENDING" || it.status == "COMPLETED" }
        val eligible = active.filter { !LocalDate.parse(it.plannedDate).isAfter(today) }
        return MyStatistics(
            eligibleCompleted = eligible.count { it.status == "COMPLETED" },
            eligibleTotal = eligible.size,
            totalCompleted = active.count { it.status == "COMPLETED" },
        )
    }
}

