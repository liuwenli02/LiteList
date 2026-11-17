package com.liuwenli.litelist.data

import androidx.room.withTransaction
import com.liuwenli.litelist.domain.TaskRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class StaleTaskException : IllegalStateException("任务已变化，请重新加载")

class TaskRepository(private val database: LiteListDatabase, private val clock: Clock) {
    private val dao = database.taskDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeTasks(): Flow<List<TaskEntity>> = dao.observePointer().flatMapLatest { pointer ->
        if (pointer == null) flowOf(emptyList()) else dao.observeActiveTasks(pointer.generationId)
    }

    suspend fun create(title: String, note: String?, plannedDate: LocalDate, deadline: LocalDateTime?): TaskEntity {
        val cleanTitle = TaskRules.validateTask(title, note, plannedDate, deadline)
        return database.withTransaction {
            val pointer = ensurePointer()
            val task = TaskEntity(
                generationId = pointer.generationId,
                taskId = UUID.randomUUID().toString(),
                title = cleanTitle,
                note = note,
                plannedDate = plannedDate.toString(),
                deadlineLocalDateTime = deadline?.toString(),
                status = "PENDING",
                completedAt = null,
                createdAt = clock.instant().toString(),
            )
            dao.insertTask(task)
            check(dao.bumpRevision(pointer.generationId) == 1)
            task
        }
    }

    suspend fun complete(generationId: String, taskId: String, expectedVersion: Long) =
        transition(generationId, taskId, expectedVersion, "PENDING", "COMPLETED", clock.instant().toString())

    suspend fun reopen(generationId: String, taskId: String, expectedVersion: Long) =
        transition(generationId, taskId, expectedVersion, "COMPLETED", "PENDING", null)

    suspend fun delete(generationId: String, taskId: String, expectedVersion: Long) {
        database.withTransaction {
            val pointer = dao.activePointer() ?: throw StaleTaskException()
            if (pointer.generationId != generationId) throw StaleTaskException()
            val task = dao.findTask(generationId, taskId) ?: throw StaleTaskException()
            if (task.status == "DELETED" || task.dataVersion != expectedVersion) throw StaleTaskException()
            if (dao.transitionTask(generationId, taskId, expectedVersion, task.status, "DELETED", null) != 1) throw StaleTaskException()
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    private suspend fun transition(generationId: String, taskId: String, expectedVersion: Long, from: String, to: String, completedAt: String?) {
        database.withTransaction {
            val pointer = dao.activePointer() ?: throw StaleTaskException()
            if (pointer.generationId != generationId) throw StaleTaskException()
            if (dao.transitionTask(generationId, taskId, expectedVersion, from, to, completedAt) != 1) throw StaleTaskException()
            check(dao.bumpRevision(generationId) == 1)
        }
    }

    private suspend fun ensurePointer(): DatasetPointer {
        dao.activePointer()?.let { return it }
        val generation = DatasetGeneration(UUID.randomUUID().toString(), clock.instant().toString())
        dao.insertGeneration(generation)
        val pointer = DatasetPointer(generationId = generation.generationId)
        dao.insertPointer(pointer)
        return pointer
    }
}

