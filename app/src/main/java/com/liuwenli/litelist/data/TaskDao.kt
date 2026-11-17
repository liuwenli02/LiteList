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

    @Query("SELECT * FROM task WHERE generationId = :generationId AND taskId = :taskId")
    suspend fun findTask(generationId: String, taskId: String): TaskEntity?

    @Query("SELECT * FROM task WHERE generationId = :generationId AND status != 'DELETED' ORDER BY plannedDate, createdAt, taskId")
    fun observeActiveTasks(generationId: String): Flow<List<TaskEntity>>

    @Query("UPDATE task SET status = :status, completedAt = :completedAt, dataVersion = dataVersion + 1 WHERE generationId = :generationId AND taskId = :taskId AND dataVersion = :expectedVersion AND status = :expectedStatus")
    suspend fun transitionTask(generationId: String, taskId: String, expectedVersion: Long, expectedStatus: String, status: String, completedAt: String?): Int

    @Query("UPDATE dataset_pointer SET contentRevision = contentRevision + 1 WHERE singletonId = 1 AND generationId = :generationId")
    suspend fun bumpRevision(generationId: String): Int
}
