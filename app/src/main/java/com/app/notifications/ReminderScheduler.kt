package com.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.app.habits.TrackerItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    fun scheduleForItem(context: Context, item: TrackerItem, targetDate: LocalDate = LocalDate.now()) {
        val parts = item.schedule.scheduledTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val zone = ZoneId.systemDefault()
        var triggerAt = targetDate.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (triggerAt <= now) {
            triggerAt += 24L * 60L * 60L * 1000L
        }

        val reqCode = (item.id.hashCode() and 0x7FFFFFFF) % 900000 + 1000
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "${item.type.name}: ${item.title}")
            putExtra(
                ReminderAlarmReceiver.EXTRA_BODY,
                item.notes.ifBlank { "${item.category} • Scheduled ${item.schedule.scheduledTime}" }
            )
            putExtra(ReminderAlarmReceiver.EXTRA_IS_ALARM, item.schedule.alarmEnabled)
            putExtra(ReminderAlarmReceiver.EXTRA_NOTIF_ID, reqCode)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (item.schedule.alarmEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            }
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun triggerImmediateTestNotification(context: Context, isAlarm: Boolean) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(
                ReminderAlarmReceiver.EXTRA_TITLE,
                if (isAlarm) "HabitLair Alarm Test" else "HabitLair Notification Test"
            )
            putExtra(
                ReminderAlarmReceiver.EXTRA_BODY,
                "Alert pipeline verified on device."
            )
            putExtra(ReminderAlarmReceiver.EXTRA_IS_ALARM, isAlarm)
            putExtra(ReminderAlarmReceiver.EXTRA_NOTIF_ID, if (isAlarm) 7702 else 7701)
        }
        context.sendBroadcast(intent)
    }
}
