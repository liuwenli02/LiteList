package com.liuwenli.litelist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "normal_reminder",
    primaryKeys = ["generationId", "reminderId"],
    indices = [
        Index(value = ["generationId", "taskId", "localDateTime"], unique = true),
    ],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["generationId", "taskId"],
        childColumns = ["generationId", "taskId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class NormalReminderEntity(
    val generationId: String,
    val reminderId: String,
    val taskId: String,
    val localDateTime: String,
    val logicalStatus: String = "ENABLED",
    val scheduleVersion: Long = 1,
    val syncStatus: String = "SYNC_REQUIRED",
    val scheduleKind: String = "NOT_SCHEDULED",
)

