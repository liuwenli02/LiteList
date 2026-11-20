package com.liuwenli.litelist.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.liuwenli.litelist.data.LiteListDatabase
import com.liuwenli.litelist.data.ReminderRepository
import com.liuwenli.litelist.data.TaskRepository
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            )) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var database: LiteListDatabase? = null
            try {
                val opened = LiteListDatabase.open(context)
                database = opened
                val clock = Clock.systemDefaultZone()
                ReminderCoordinator(context, ReminderRepository(opened, clock), TaskRepository(opened, clock)).reconcile()
            } catch (error: Exception) {
                Log.e("LiteListReminder", "Reminder resync failed", error)
            } finally {
                database?.close()
                pending.finish()
            }
        }
    }
}

