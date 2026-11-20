package com.liuwenli.litelist.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.liuwenli.litelist.domain.AlarmScheduleKind
import com.liuwenli.litelist.domain.AlarmSchedulePolicy
import com.liuwenli.litelist.domain.ReminderScheduleCandidate

/** Android scheduling is separate from the user's saved reminder configuration. */
class AndroidAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun permissionKind(): AlarmScheduleKind = AlarmSchedulePolicy.choose(
        canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        canScheduleExact = alarmManager.canScheduleExactAlarms(),
    )

    fun schedule(candidate: ReminderScheduleCandidate, receiver: Class<*>): AlarmScheduleKind {
        val preferred = permissionKind()
        if (preferred == AlarmScheduleKind.NOT_SCHEDULED) return preferred
        val operation = pendingIntent(candidate, receiver, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return AlarmScheduleKind.NOT_SCHEDULED
        return try {
            if (preferred == AlarmScheduleKind.EXACT) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, candidate.scheduledAt.toEpochMilli(), operation)
                AlarmScheduleKind.EXACT
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, candidate.scheduledAt.toEpochMilli(), operation)
                AlarmScheduleKind.INEXACT
            }
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, candidate.scheduledAt.toEpochMilli(), operation)
            }.fold(onSuccess = { AlarmScheduleKind.INEXACT }, onFailure = { AlarmScheduleKind.NOT_SCHEDULED })
        } catch (_: RuntimeException) {
            AlarmScheduleKind.NOT_SCHEDULED
        }
    }

    fun cancel(candidate: ReminderScheduleCandidate, receiver: Class<*>) {
        val operation = pendingIntent(candidate, receiver, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(candidate: ReminderScheduleCandidate, receiver: Class<*>, flags: Int): PendingIntent? {
        val identity = listOf(candidate.generationId, candidate.taskId, candidate.kind, candidate.reminderId.orEmpty())
            .joinToString("/") { Uri.encode(it) }
        val intent = Intent(context, receiver).apply {
            action = "com.liuwenli.litelist.REMINDER_ALARM"
            data = Uri.parse("litelist://alarm/$identity")
            putExtra("generationId", candidate.generationId)
            putExtra("taskId", candidate.taskId)
            putExtra("kind", candidate.kind)
            putExtra("reminderId", candidate.reminderId)
            putExtra("scheduleVersion", candidate.scheduleVersion)
            putExtra("scheduledAt", candidate.scheduledAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

