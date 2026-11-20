package com.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.app.MainActivity
import com.app.R
import com.app.ai.AiSettingsStorage
import com.app.habits.HabitStorage
import com.app.habits.TrackerItemType
import com.app.habits.isScheduledForDate
import com.app.notifications.ScreenTimeMonitor
import com.app.pomodoro.PomodoroStorage
import java.time.LocalDate
import java.util.Locale

enum class WidgetVariety(val layoutRes: Int, val tag: String) {
    OVERVIEW(R.layout.habitlair_widget, "TODAY"),
    HABITS(R.layout.habitlair_widget_habits, "HABITS"),
    TASKS(R.layout.habitlair_widget_tasks, "TASKS"),
    STREAK(R.layout.habitlair_widget_streak, "STREAK"),
    POMODORO(R.layout.habitlair_widget_pomodoro, "FOCUS"),
    SCREEN(R.layout.habitlair_widget_screentime, "SCREEN")
}

abstract class BaseHabitLairWidgetProvider(internal val variety: WidgetVariety) : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            HabitLairWidgetProvider.renderVariety(context, appWidgetManager, id, variety)
        }
    }
}

class HabitPulseWidgetProvider : BaseHabitLairWidgetProvider(WidgetVariety.HABITS)
class TaskQueueWidgetProvider : BaseHabitLairWidgetProvider(WidgetVariety.TASKS)
class StreakVaultWidgetProvider : BaseHabitLairWidgetProvider(WidgetVariety.STREAK)
class PomodoroPulseWidgetProvider : BaseHabitLairWidgetProvider(WidgetVariety.POMODORO)
class ScreenGuardWidgetProvider : BaseHabitLairWidgetProvider(WidgetVariety.SCREEN)

class HabitLairWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            renderVariety(context, appWidgetManager, appWidgetId, WidgetVariety.OVERVIEW)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val targets: List<Pair<Class<out AppWidgetProvider>, WidgetVariety>> = listOf(
                HabitLairWidgetProvider::class.java to WidgetVariety.OVERVIEW,
                HabitPulseWidgetProvider::class.java to WidgetVariety.HABITS,
                TaskQueueWidgetProvider::class.java to WidgetVariety.TASKS,
                StreakVaultWidgetProvider::class.java to WidgetVariety.STREAK,
                PomodoroPulseWidgetProvider::class.java to WidgetVariety.POMODORO,
                ScreenGuardWidgetProvider::class.java to WidgetVariety.SCREEN
            )
            targets.forEach { (clazz, v) ->
                val ids = manager.getAppWidgetIds(ComponentName(context, clazz))
                ids.forEach { id -> renderVariety(context, manager, id, v) }
            }
        }

        internal fun renderVariety(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            variety: WidgetVariety
        ) {
            val state = HabitStorage(context).load()
            val today = LocalDate.now()
            val todayEpoch = today.toEpochDay()
            val scheduledToday = state.items.filter { it.schedule.isScheduledForDate(today) }
            val completedIds = state.logs.filter { it.dateEpochDay == todayEpoch }.map { it.itemId }.toSet()

            val (header, summary, detail) = when (variety) {
                WidgetVariety.OVERVIEW -> {
                    val done = scheduledToday.count { completedIds.contains(it.id) }
                    val maxStreak = state.items.maxOfOrNull { it.streak } ?: 0
                    Triple("HABITLAIR • TODAY", "$done / ${scheduledToday.size} done", "Streak ${maxStreak}d • ${state.logs.size} logs")
                }
                WidgetVariety.HABITS -> {
                    val habits = scheduledToday.filter { it.type == TrackerItemType.HABIT }
                    val doneHabits = habits.count { completedIds.contains(it.id) }
                    val nextHabit = habits.firstOrNull { !completedIds.contains(it.id) }?.title ?: "All habits done"
                    Triple("HABITLAIR • HABITS", "$doneHabits / ${habits.size} habits", nextHabit)
                }
                WidgetVariety.TASKS -> {
                    val tasks = scheduledToday.filter { it.type == TrackerItemType.TASK }
                    val doneTasks = tasks.count { completedIds.contains(it.id) }
                    val nextTask = tasks.firstOrNull { !completedIds.contains(it.id) }?.title ?: "Queue clear"
                    Triple("HABITLAIR • TASKS", "$doneTasks / ${tasks.size} tasks", nextTask)
                }
                WidgetVariety.STREAK -> {
                    val topItem = state.items.maxByOrNull { it.streak }
                    val topStreak = topItem?.streak ?: 0
                    val label = topItem?.title ?: "No active streak"
                    Triple("HABITLAIR • STREAK", "${topStreak}d best", label)
                }
                WidgetVariety.POMODORO -> {
                    val pom = PomodoroStorage(context).load()
                    val mins = (pom.remainingSeconds / 60).coerceAtLeast(0)
                    val secs = (pom.remainingSeconds % 60).coerceAtLeast(0)
                    val clock = String.format(Locale.ROOT, "%02d:%02d", mins, secs)
                    Triple("HABITLAIR • FOCUS", "$clock • ${pom.phase.name}", "${pom.completedFocusCount} blocks completed")
                }
                WidgetVariety.SCREEN -> {
                    val ai = AiSettingsStorage(context).load()
                    val snap = ScreenTimeMonitor.queryTodayScreenTime(context)
                    val limit = ai.dailyScreenLimitMinutes
                    val status = if (snap.todayMinutes >= limit) "LIMIT EXCEEDED" else "WITHIN LIMIT"
                    Triple("HABITLAIR • SCREEN", "${snap.todayMinutes}m / ${limit}m", "$status • ${snap.topAppName}")
                }
            }

            val views = RemoteViews(context.packageName, variety.layoutRes).apply {
                setTextViewText(R.id.widget_header, header)
                setTextViewText(R.id.widget_summary, summary)
                setTextViewText(R.id.widget_detail, detail)
            }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + variety.ordinal * 1000,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
