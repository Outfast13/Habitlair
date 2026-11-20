package com.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.app.MainActivity
import com.app.ai.AiSettingsStorage

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AiSettingsStorage(context).load()
        if (!settings.notificationsEnabled) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "HabitLair Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Scheduled habit or task check-in."
        val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, (System.currentTimeMillis() % 100000).toInt())

        val channelId = if (isAlarm) "habitlair_alarms_v4" else "habitlair_reminders_v4"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = if (isAlarm) "HabitLair Alarms" else "HabitLair Reminders"
            val importance = if (isAlarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                enableVibration(settings.vibrateEnabled)
                if (!settings.alarmSoundEnabled) {
                    setSound(null, null)
                }
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        if (settings.alarmSoundEnabled) {
            val soundUri = if (isAlarm) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            builder.setSound(soundUri)
        }

        if (settings.vibrateEnabled) {
            builder.setVibrate(longArrayOf(0L, 250L, 150L, 250L))
        }

        manager.notify(notifId, builder.build())
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_IS_ALARM = "extra_is_alarm"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
