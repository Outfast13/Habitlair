package com.app.notifications

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

data class ScreenTimeSnapshot(
    val hasUsagePermission: Boolean,
    val todayMinutes: Int,
    val topAppName: String
)

object ScreenTimeMonitor {
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryTodayScreenTime(context: Context): ScreenTimeSnapshot {
        if (!hasUsageStatsPermission(context)) {
            return ScreenTimeSnapshot(
                hasUsagePermission = false,
                todayMinutes = 0,
                topAppName = "Permission needed"
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeSnapshot(
                hasUsagePermission = true,
                todayMinutes = 0,
                topAppName = "Unavailable"
            )

        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val events = usm.queryEvents(startOfDay, now)
        val activeStarts = mutableMapOf<String, Long>()
        val perPackageMs = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    activeStarts[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startedAt = activeStarts.remove(pkg)
                    if (startedAt != null && event.timeStamp > startedAt) {
                        perPackageMs[pkg] = (perPackageMs[pkg] ?: 0L) + (event.timeStamp - startedAt)
                    }
                }
            }
        }

        activeStarts.forEach { (pkg, startedAt) ->
            if (now > startedAt) {
                perPackageMs[pkg] = (perPackageMs[pkg] ?: 0L) + (now - startedAt)
            }
        }

        val totalMs = perPackageMs.values.sum()
        val topPkg = perPackageMs.maxByOrNull { it.value }?.key
        val topLabel = topPkg?.let { pkg ->
            runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg.substringAfterLast('.'))
        } ?: "None"

        return ScreenTimeSnapshot(
            hasUsagePermission = true,
            todayMinutes = (totalMs / 60_000L).toInt().coerceAtLeast(0),
            topAppName = topLabel
        )
    }
}
