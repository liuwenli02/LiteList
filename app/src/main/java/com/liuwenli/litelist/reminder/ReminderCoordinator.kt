package com.liuwenli.litelist.reminder

import android.content.Context
import com.liuwenli.litelist.data.ReminderRepository
import com.liuwenli.litelist.data.TaskRepository
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderCoordinator(
    context: Context,
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
) {
    private val scheduler = AndroidAlarmScheduler(context.applicationContext)
    private val mutex = Mutex()

    suspend fun reconcile() = mutex.withLock {
        reminders.allAlarmIdentities().forEach { scheduler.cancel(it, ReminderAlarmReceiver::class.java) }
        tasks.futureReminderCandidates(ZoneId.systemDefault()).forEach { candidate ->
            val kind = scheduler.schedule(candidate, ReminderAlarmReceiver::class.java)
            reminders.recordScheduleResult(candidate, kind)
        }
    }
}

