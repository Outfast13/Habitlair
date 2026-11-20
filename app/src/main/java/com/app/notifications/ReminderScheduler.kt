package com.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.app.MainActivity
import com.app.habits.TrackerItem
import com.app.habits.isScheduledForDate
import java.time.LocalDate
import java.time.ZoneId

object ReminderScheduler {
    const val CHANNEL_ID = "habitlair_reminders_v4"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_ITEM_TITLE = "extra_item_title"
    const val EXTRA_ITEM_TYPE = "extra_item_type"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HabitLair Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled habit, task, and reminder alerts"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    fun scheduleForItem(context: Context, item: TrackerItem, referenceDate: LocalDate = LocalDate.now()) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val parts = item.schedule.scheduledTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        var targetDate = referenceDate
        var triggerMillis = targetDate.atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val now = System.currentTimeMillis()
        for (offset in 0..366) {
            val candidateDate = referenceDate.plusDays(offset.toLong())
            if (!item.schedule.isScheduledForDate(candidateDate)) continue
            val candidateMillis = candidateDate.atTime(hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (candidateMillis > now) {
                targetDate = candidateDate
                triggerMillis = candidateMillis
                break
            }
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_ITEM_TITLE, item.title)
            putExtra(EXTRA_ITEM_TYPE, item.type.name)
        }
        val requestCode = item.id.hashCode()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pi)
        if (triggerMillis > now) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                }
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        }
    }

    fun rescheduleAll(context: Context, items: List<TrackerItem>, referenceDate: LocalDate = LocalDate.now()) {
        items.forEach { scheduleForItem(context, it, referenceDate) }
    }

    fun sendScreenTimeWarningNotification(context: Context, usedMinutes: Int, limitMinutes: Int) {
        ensureChannel(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            99001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Screen Time Limit Reached")
            .setContentText("Used ${usedMinutes}m today against your ${limitMinutes}m limit.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        mgr.notify(99001, notif)
    }
}
