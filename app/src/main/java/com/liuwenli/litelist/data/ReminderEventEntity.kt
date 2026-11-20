package com.liuwenli.litelist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "reminder_event",
    primaryKeys = ["generationId", "eventKey"],
    indices = [Index(value = ["generationId", "taskId"])],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["generationId", "taskId"],
        childColumns = ["generationId", "taskId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class ReminderEventEntity(
    val generationId: String,
    val eventKey: String,
    val taskId: String,
    val scheduledAtEpochMillis: Long,
    val status: String = "PENDING",
    val attempts: Int = 0,
    val claimedAtEpochMillis: Long? = null,
)

