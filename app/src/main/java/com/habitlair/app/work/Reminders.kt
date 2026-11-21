package com.habitlair.app.work

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitlair.app.HabitLairApp
import com.habitlair.app.MainActivity
import com.habitlair.app.R
import com.habitlair.app.data.*
import com.habitlair.app.widget.HabitWidget
import kotlinx.coroutines.launch
import java.time.*

const val EXTRA_TYPE = "type"
const val EXTRA_ID = "id"
const val TYPE_POMODORO = "pomodoro"
const val TYPE_TEST = "test"

object Notifier {
    fun channelId(sound: Boolean, vibration: Boolean) = "reminders_${if (sound) "s" else "x"}${if (vibration) "v" else "x"}"

    /** Channel sound/vibration can't change after creation, so one channel exists per combination. */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (sound in listOf(true, false)) for (vib in listOf(true, false)) {
            val ch = NotificationChannel(
                channelId(sound, vib),
                "Reminders (${if (sound) "sound" else "silent"}${if (vib) " + vibration" else ""})",
                if (sound) NotificationManager.IMPORTANCE_HIGH else if (vib) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW
            )
            ch.enableVibration(vib)
            if (sound) ch.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            ) else ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, s: AppSettings, notifId: Int, title: String, text: String, type: String? = null, id: Long = 0) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, channelId(s.sound, s.vibration))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open)
        if (type == Owner.HABIT || type == Owner.TASK) {
            b.addAction(0, "Done", actionIntent(ctx, ActionReceiver.DONE, type, id, notifId))
            b.addAction(0, "Snooze 10 min", actionIntent(ctx, ActionReceiver.SNOOZE, type, id, notifId))
        }
        NotificationManagerCompat.from(ctx).notify(notifId, b.build())
    }

    private fun actionIntent(ctx: Context, action: String, type: String, id: Long, notifId: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id).putExtra("notif", notifId)
        return PendingIntent.getBroadcast(ctx, notifId * 3 + if (action == ActionReceiver.DONE) 1 else 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showTest(ctx: Context, s: AppSettings): Boolean {
        if (!canPost(ctx)) return false
        show(
            ctx,
            s,
            9_999,
            "HabitLair test",
            "Notifications are working${if (s.sound) " (sound on)" else ""}${if (s.vibration) " (vibration on)" else ""}."
        )
        return true
    }
}

class ReminderScheduler(private val ctx: Context, private val db: AppDatabase, private val settings: SettingsStore) {
    private val am = ctx.getSystemService(AlarmManager::class.java)

    fun requestCode(type: String, id: Long): Int = when (type) {
        Owner.HABIT -> id.toInt()
        Owner.TASK -> 0x40000000 + id.toInt()
        TYPE_POMODORO -> 0x7FFFFFF0
        else -> 0x7FFFFFF1
    }

    fun notifId(type: String, id: Long) = requestCode(type, id)

    private fun pi(type: String, id: Long, flags: Int = 0): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("com.habitlair.REMINDER").putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, requestCode(type, id), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun cancel(type: String, id: Long) { am.cancel(pi(type, id, PendingIntent.FLAG_UPDATE_CURRENT)) }

    fun setAt(type: String, id: Long, at: Long) {
        val p = pi(type, id, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun snooze(type: String, id: Long, minutes: Int = 10) = setAt(type, id, System.currentTimeMillis() + minutes * 60_000L)

    fun nextHabitTime(h: Habit, from: Long = System.currentTimeMillis()): Long? {
        val t = h.timeMinutes ?: return null
        if (h.daysMask == 0) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone)
        for (i in 0..7) {
            val d = start.plusDays(i.toLong())
            if ((h.daysMask and (1 shl (d.dayOfWeek.value - 1))) == 0) continue
            val at = LocalDateTime.of(d, LocalTime.of(t / 60, t % 60)).atZone(zone).toInstant().toEpochMilli()
            if (at > from + 1000) return at
        }
        return null
    }

    suspend fun syncHabit(id: Long) {
        val s = settings.current()
        val h = db.habits().get(id)
        cancel(Owner.HABIT, id)
        if (h == null || !s.remindersEnabled || !h.reminder || h.archived) return
        nextHabitTime(h)?.let { setAt(Owner.HABIT, id, it) }
    }

    suspend fun syncTask(id: Long) {
        val s = settings.current()
        val t = db.tasks().get(id)
        cancel(Owner.TASK, id)
        if (t == null || t.done || !t.reminder || !s.remindersEnabled) return
        val day = t.dueEpochDay ?: return
        val m = t.dueMinutes ?: s.defaultReminderMinutes
        val at = LocalDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(m / 60, m % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at > System.currentTimeMillis()) setAt(Owner.TASK, id, at)
    }

    suspend fun rescheduleAll() {
        db.habits().all().forEach { syncHabit(it.id) }
        db.tasks().all().forEach { syncTask(it.id) }
    }
}

private fun BroadcastReceiver.async(ctx: Context, block: suspend (HabitLairApp) -> Unit) {
    val app = ctx.applicationContext as HabitLairApp
    val pending = goAsync()
    app.container.scope.launch {
        try { block(app) } finally { pending.finish() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        async(ctx) { app ->
            val c = app.container
            val s = c.settings.current()
            val nid = c.scheduler.notifId(type, id)
            when (type) {
                Owner.HABIT -> {
                    val h = c.db.habits().get(id)
                    if (h != null) {
                        if (s.remindersEnabled && c.db.habits().isDone(id, Dates.today()) == 0)
                            Notifier.show(ctx, s, nid, "Time for ${h.name}", h.notes.ifBlank { "Tap to open HabitLair" }, type, id)
                        c.scheduler.syncHabit(id)
                    }
                }
                Owner.TASK -> {
                    val t = c.db.tasks().get(id)
                    if (t != null && !t.done) Notifier.show(ctx, s, nid, "Task due: ${t.title}", t.notes.ifBlank { "Tap to open HabitLair" }, type, id)
                }
                TYPE_POMODORO -> Notifier.show(ctx, s, nid, "Pomodoro finished", "Open HabitLair to continue your session.")
                else -> Notifier.show(ctx, s, nid, "HabitLair test alarm", "Your alarm fired on time.")
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notif = intent.getIntExtra("notif", 0)
        val action = intent.action
        async(ctx) { app ->
            val c = app.container
            when (action) {
                DONE -> if (type == Owner.HABIT) c.repo.setHabitDone(id, Dates.today(), true) else if (type == Owner.TASK) c.repo.setTaskDone(id, true)
                SNOOZE -> c.scheduler.snooze(type, id)
            }
            NotificationManagerCompat.from(ctx).cancel(notif)
        }
    }

    companion object { const val DONE = "com.habitlair.DONE"; const val SNOOZE = "com.habitlair.SNOOZE" }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        async(ctx) { app ->
            app.container.scheduler.rescheduleAll()
            runCatching { HabitWidget().updateAll(ctx) }
        }
    }
}

class MaintenanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as HabitLairApp).container
        c.scheduler.rescheduleAll()
        runCatching { HabitWidget().updateAll(applicationContext) }
        return Result.success()
    }
}
