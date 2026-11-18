package com.liuwenli.litelist.ui

import com.liuwenli.litelist.data.TaskEntity
import com.liuwenli.litelist.domain.TaskRules
import java.time.LocalDateTime
import java.time.ZoneId

object TaskOrdering {
    fun pending(zone: ZoneId): Comparator<TaskEntity> =
        compareBy<TaskEntity> { it.deadlineLocalDateTime == null }
            .thenBy { it.deadlineLocalDateTime?.let { local -> TaskRules.resolve(LocalDateTime.parse(local), zone) } }
            .thenBy { it.createdAt }
            .thenBy { it.taskId }

    val completed: Comparator<TaskEntity> = compareByDescending<TaskEntity> { it.completedAt }
        .thenBy { it.taskId }
}

