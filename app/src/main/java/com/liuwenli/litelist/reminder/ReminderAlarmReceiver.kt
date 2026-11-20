package com.liuwenli.litelist.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.liuwenli.litelist.MainActivity
import com.liuwenli.litelist.data.LiteListDatabase
import com.liuwenli.litelist.data.ReminderRepository
import com.liuwenli.litelist.domain.ReminderScheduleCandidate
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM && intent.action != ACTION_DISMISS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var database: LiteListDatabase? = null
            try {
                val opened = LiteListDatabase.open(context)
                database = opened
                val repository = ReminderRepository(opened, Clock.systemDefaultZone())
                when (intent.action) {
                    ACTION_ALARM -> handleAlarm(context, intent, repository)
                    ACTION_DISMISS -> repository.muteAfterDismiss(
                        intent.getStringExtra("generationId") ?: return@launch,
                        intent.getStringExtra("taskId") ?: return@launch,
                        intent.getStringExtra("eventKey") ?: return@launch,
                    )
                }
            } catch (error: Exception) {
                Log.e("LiteListReminder", "Reminder broadcast failed", error)
            } finally {
                database?.close()
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, intent: Intent, repository: ReminderRepository) {
        val generationId = intent.getStringExtra("generationId") ?: return
        val taskId = intent.getStringExtra("taskId") ?: return
        val kind = intent.getStringExtra("kind") ?: return
        if (kind != "NORMAL" && kind != "DEADLINE") return
        val version = intent.getLongExtra("scheduleVersion", -1)
        val scheduledAt = intent.getLongExtra("scheduledAt", -1)
        if (version < 1 || scheduledAt < 0) return
        val candidate = ReminderScheduleCandidate(
            generationId, taskId, intent.getStringExtra("reminderId"), kind,
            Instant.ofEpochMilli(scheduledAt), version,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "task_reminders"
        manager.createNotificationChannel(NotificationChannel(channelId, "任务提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val claim = repository.claim(candidate) ?: return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS
                data = Uri.parse("litelist://dismiss/${Uri.encode(claim.generationId)}/${Uri.encode(claim.eventKey)}")
                putExtra("generationId", claim.generationId)
                putExtra("taskId", claim.taskId)
                putExtra("eventKey", claim.eventKey)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (claim.hasDeadline) "DDL 已到" else "任务提醒")
            .setContentText(claim.title)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setAutoCancel(false)
            .build()
        try {
            manager.notify(claim.eventKey, 0, notification)
        } catch (_: SecurityException) {
            repository.releaseClaim(claim)
            return
        } catch (_: RuntimeException) {
            repository.releaseClaim(claim)
            return
        }
        repository.markPublished(claim)
    }

    companion object {
        const val ACTION_ALARM = "com.liuwenli.litelist.REMINDER_ALARM"
        const val ACTION_DISMISS = "com.liuwenli.litelist.REMINDER_DISMISS"
    }
}

