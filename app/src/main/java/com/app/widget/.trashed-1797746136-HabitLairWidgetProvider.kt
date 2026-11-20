package com.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.app.MainActivity
import com.app.R
import com.app.habits.HabitStorage
import com.app.habits.isScheduledForDate
import java.time.LocalDate

class HabitLairWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, HabitLairWidgetProvider::class.java)
            )
            ids.forEach { id -> updateSingleWidget(context, manager, id) }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val state = HabitStorage(context).load()
            val today = LocalDate.now()
            val todayEpoch = today.toEpochDay()
            val scheduledToday = state.items.filter { it.schedule.isScheduledForDate(today) }
            val completedIds = state.logs.filter { it.dateEpochDay == todayEpoch }.map { it.itemId }.toSet()
            val doneCount = scheduledToday.count { completedIds.contains(it.id) }
            val maxStreak = state.items.maxOfOrNull { it.streak } ?: 0

            val views = RemoteViews(context.packageName, R.layout.habitlair_widget).apply {
                setTextViewText(R.id.widget_header, "HABITLAIR • TODAY")
                setTextViewText(R.id.widget_summary, "$doneCount / ${scheduledToday.size} done")
                setTextViewText(R.id.widget_detail, "Top streak: ${maxStreak}d • Logs: ${state.logs.size}")
            }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
