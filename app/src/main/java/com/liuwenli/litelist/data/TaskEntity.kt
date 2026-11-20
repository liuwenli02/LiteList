package com.liuwenli.litelist.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dataset_generation")
data class DatasetGeneration(
    @PrimaryKey val generationId: String,
    val createdAt: String,
)

@Entity(tableName = "dataset_pointer")
data class DatasetPointer(
    @PrimaryKey val singletonId: Int = 1,
    val generationId: String,
    val switchVersion: Long = 0,
    val contentRevision: Long = 0,
    @ColumnInfo(defaultValue = "'SYSTEM'") val themeMode: String = "SYSTEM",
)

@Entity(
    tableName = "task",
    primaryKeys = ["generationId", "taskId"],
    indices = [Index(value = ["generationId", "plannedDate", "status"])],
    foreignKeys = [ForeignKey(
        entity = DatasetGeneration::class,
        parentColumns = ["generationId"],
        childColumns = ["generationId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class TaskEntity(
    val generationId: String,
    val taskId: String,
    val title: String,
    val note: String?,
    val plannedDate: String,
    val deadlineLocalDateTime: String?,
    val status: String,
    val completedAt: String?,
    val createdAt: String,
    val dataVersion: Long = 1,
    val postponeCount: Int = 0,
    val reminderMode: String = "ENABLED",
    @ColumnInfo(defaultValue = "0") val deadlineReminderDisabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val deadlineScheduleVersion: Long = 1,
    @ColumnInfo(defaultValue = "'SYNC_REQUIRED'") val deadlineSyncStatus: String = "SYNC_REQUIRED",
    @ColumnInfo(defaultValue = "'NOT_SCHEDULED'") val deadlineScheduleKind: String = "NOT_SCHEDULED",
    @ColumnInfo(defaultValue = "0") val deadlineTriggeredVersion: Long = 0,
)

