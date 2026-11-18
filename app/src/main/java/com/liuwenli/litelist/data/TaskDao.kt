package com.liuwenli.litelist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM dataset_pointer WHERE singletonId = 1")
    suspend fun activePointer(): DatasetPointer?

    @Query("SELECT * FROM dataset_pointer WHERE singletonId = 1")
    fun observePointer(): Flow<DatasetPointer?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGeneration(generation: DatasetGeneration)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPointer(pointer: DatasetPointer)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplate(template: RepeatTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstance(instance: RepeatInstanceEntity)

    @Query("SELECT * FROM repeat_template WHERE generationId = :generationId AND status = 'ACTIVE' AND isDeleted = 0")
    suspend fun activeTemplates(generationId: String): List<RepeatTemplateEntity>

    @Query("SELECT * FROM repeat_template WHERE generationId = :generationId AND isDeleted = 0 ORDER BY createdAt, templateId")
    fun observeTemplates(generationId: String): Flow<List<RepeatTemplateEntity>>

    @Query("SELECT * FROM repeat_template WHERE generationId = :generationId AND templateId = :templateId")
    suspend fun findTemplate(generationId: String, templateId: String): RepeatTemplateEntity?

    @Query("UPDATE repeat_template SET title = :title, note = :note, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND templateId = :templateId AND dataVersion = :expectedVersion AND status IN ('ACTIVE', 'PAUSED') AND isDeleted = 0")
    suspend fun editTemplate(generationId: String, templateId: String, expectedVersion: Long, title: String, note: String?): Int

    @Query("UPDATE repeat_template SET status = :newStatus, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND templateId = :templateId AND dataVersion = :expectedVersion AND status = :expectedStatus AND isDeleted = 0")
    suspend fun changeTemplateStatus(generationId: String, templateId: String, expectedVersion: Long, expectedStatus: String, newStatus: String): Int

    @Query("SELECT * FROM repeat_instance WHERE generationId = :generationId AND templateId = :templateId AND originalDate > :operationDate AND lifecycleStatus = 'ACTIVE'")
    suspend fun activeFutureInstances(generationId: String, templateId: String, operationDate: String): List<RepeatInstanceEntity>

    @Query("SELECT * FROM repeat_instance WHERE generationId = :generationId AND templateId = :templateId AND originalDate > :today AND originalDate <= :lastDate AND lifecycleStatus = 'SYSTEM_CANCELLED' AND cancellationReason = 'PAUSE'")
    suspend fun pausedWindowInstances(generationId: String, templateId: String, today: String, lastDate: String): List<RepeatInstanceEntity>

    @Query("UPDATE repeat_instance SET lifecycleStatus = 'SYSTEM_CANCELLED', cancellationReason = 'PAUSE', lifecycleVersion = lifecycleVersion + 1 WHERE generationId = :generationId AND instanceId = :instanceId AND lifecycleVersion = :expectedVersion AND lifecycleStatus = 'ACTIVE'")
    suspend fun cancelInstanceForPause(generationId: String, instanceId: String, expectedVersion: Long): Int

    @Query("UPDATE repeat_instance SET lifecycleStatus = 'ACTIVE', cancellationReason = NULL, lifecycleVersion = lifecycleVersion + 1 WHERE generationId = :generationId AND instanceId = :instanceId AND lifecycleVersion = :expectedVersion AND lifecycleStatus = 'SYSTEM_CANCELLED' AND cancellationReason = 'PAUSE'")
    suspend fun resumePausedInstance(generationId: String, instanceId: String, expectedVersion: Long): Int

    @Query("SELECT * FROM repeat_instance WHERE generationId = :generationId AND templateId = :templateId AND originalDate > :operationDate")
    suspend fun futureTemplateInstances(generationId: String, templateId: String, operationDate: String): List<RepeatInstanceEntity>

    @Query("UPDATE task SET title = :title, note = :note, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND status IN ('PENDING', 'COMPLETED')")
    suspend fun applyTemplateContentToTask(generationId: String, taskId: String, title: String, note: String?): Int

    @Query("SELECT originalDate FROM repeat_instance WHERE generationId = :generationId AND templateId = :templateId AND originalDate BETWEEN :firstDate AND :lastDate")
    suspend fun occupiedOriginalDates(generationId: String, templateId: String, firstDate: String, lastDate: String): List<String>

    @Query("SELECT * FROM repeat_instance WHERE generationId = :generationId AND taskId = :taskId")
    suspend fun findInstanceForTask(generationId: String, taskId: String): RepeatInstanceEntity?

    @Query("SELECT * FROM task WHERE generationId = :generationId AND taskId = :taskId")
    suspend fun findTask(generationId: String, taskId: String): TaskEntity?

    @Query("SELECT task.* FROM task LEFT JOIN repeat_instance ON task.generationId = repeat_instance.generationId AND task.taskId = repeat_instance.taskId WHERE task.generationId = :generationId AND task.status != 'DELETED' AND (repeat_instance.instanceId IS NULL OR repeat_instance.lifecycleStatus = 'ACTIVE') ORDER BY task.plannedDate, task.createdAt, task.taskId")
    fun observeActiveTasks(generationId: String): Flow<List<TaskEntity>>

    @Query("UPDATE task SET status = :status, completedAt = :completedAt, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND dataVersion = :expectedVersion AND status = :expectedStatus")
    suspend fun transitionTask(generationId: String, taskId: String, expectedVersion: Long, expectedStatus: String, status: String, completedAt: String?): Int

    @Query("UPDATE task SET title = :title, note = :note, plannedDate = :plannedDate, deadlineLocalDateTime = :deadline, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND dataVersion = :expectedVersion AND status != 'DELETED'")
    suspend fun editTask(generationId: String, taskId: String, expectedVersion: Long, title: String, note: String?, plannedDate: String, deadline: String?): Int

    @Query("UPDATE repeat_instance SET isCustomized = 1, lifecycleVersion = lifecycleVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND lifecycleStatus = 'ACTIVE' AND lifecycleVersion = :expectedVersion")
    suspend fun customizeInstance(generationId: String, taskId: String, expectedVersion: Long): Int

    @Query("UPDATE repeat_instance SET lifecycleStatus = 'USER_DELETED', lifecycleVersion = lifecycleVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND lifecycleStatus = 'ACTIVE' AND lifecycleVersion = :expectedVersion")
    suspend fun userDeleteInstance(generationId: String, taskId: String, expectedVersion: Long): Int

    @Query("UPDATE dataset_pointer SET contentRevision = contentRevision + 1 WHERE singletonId = 1 AND generationId = :generationId")
    suspend fun bumpRevision(generationId: String): Int

    @Query("UPDATE dataset_pointer SET themeMode = :mode WHERE singletonId = 1 AND generationId = :generationId")
    suspend fun setTheme(generationId: String, mode: String): Int
}

