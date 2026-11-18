package com.liuwenli.litelist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "repeat_template",
    primaryKeys = ["generationId", "templateId"],
    indices = [Index(value = ["generationId", "status", "isDeleted"])],
    foreignKeys = [ForeignKey(
        entity = DatasetGeneration::class,
        parentColumns = ["generationId"],
        childColumns = ["generationId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class RepeatTemplateEntity(
    val generationId: String,
    val templateId: String,
    val title: String,
    val note: String?,
    val startDate: String?,
    val endDate: String?,
    val status: String = "ACTIVE",
    val isDeleted: Boolean = false,
    val createdAt: String,
    val dataVersion: Long = 1,
)

@Entity(
    tableName = "repeat_instance",
    primaryKeys = ["generationId", "instanceId"],
    indices = [
        Index(value = ["generationId", "templateId", "originalDate"], unique = true),
        Index(value = ["generationId", "taskId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DatasetGeneration::class,
            parentColumns = ["generationId"],
            childColumns = ["generationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RepeatTemplateEntity::class,
            parentColumns = ["generationId", "templateId"],
            childColumns = ["generationId", "templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["generationId", "taskId"],
            childColumns = ["generationId", "taskId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class RepeatInstanceEntity(
    val generationId: String,
    val instanceId: String,
    val taskId: String,
    val templateId: String,
    val originalDate: String,
    val lifecycleStatus: String = "ACTIVE",
    val cancellationReason: String? = null,
    val lifecycleVersion: Long = 1,
    val isCustomized: Boolean = false,
    val retentionProtection: Boolean = false,
    val protectionReason: String? = null,
)

