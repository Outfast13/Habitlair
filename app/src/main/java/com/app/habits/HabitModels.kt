package com.app.habits

import java.time.DayOfWeek
import java.time.LocalDate

enum class TrackerItemType { HABIT, TASK, REMINDER }

enum class EvaluationMode(val label: String, val subtitle: String) {
    YES_NO("Yes or No", "Record whether you succeed with the activity or not"),
    NUMERIC("Numeric Value", "Establish a value as a daily goal or limit"),
    TIMER("Timer", "Establish a time value as a daily goal or limit"),
    CHECKLIST("Checklist", "Evaluate your activity based on a set of sub-items")
}

enum class ScheduleCadence(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    SPECIFIC_DAYS("Specific Days"),
    ALTERNATE_DAYS("Alternate Days"),
    CUSTOM_INTERVAL("Custom Interval")
}

enum class CustomIntervalUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years")
}

data class ScheduleRule(
    val cadence: ScheduleCadence = ScheduleCadence.DAILY,
    val specificDays: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val customEvery: Int = 1,
    val customUnit: CustomIntervalUnit = CustomIntervalUnit.DAYS,
    val anchorEpochDay: Long = LocalDate.now().toEpochDay(),
    val scheduledTime: String = "08:00",
    val alarmEnabled: Boolean = false
)

data class MediaAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long
)

data class ChecklistSubItem(
    val id: String,
    val label: String,
    val checked: Boolean = false
)

data class CompletionLog(
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val itemType: TrackerItemType,
    val dateEpochDay: Long,
    val completedAtTime: String,
    val completedAtMillis: Long,
    val proofPhotoPath: String? = null,
    val note: String = "",
    val numericDelta: Double = 0.0,
    val elapsedSeconds: Int = 0
)

data class TrackerItem(
    val id: String,
    val title: String,
    val type: TrackerItemType,
    val category: String = "General",
    val notes: String = "",
    val schedule: ScheduleRule = ScheduleRule(),
    val attachments: List<MediaAttachment> = emptyList(),
    val streak: Int = 0,
    val evaluationMode: EvaluationMode = EvaluationMode.YES_NO,
    val targetValue: Double = 1.0,
    val targetUnit: String = "count",
    val targetSeconds: Int = 900,
    val checklistItems: List<ChecklistSubItem> = emptyList()
)

data class HabitState(
    val items: List<TrackerItem> = emptyList(),
    val logs: List<CompletionLog> = emptyList()
)

fun ScheduleRule.isScheduledForDate(targetDate: LocalDate): Boolean {
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val targetEpoch = targetDate.toEpochDay()
    return when (cadence) {
        ScheduleCadence.DAILY -> true
        ScheduleCadence.WEEKLY -> targetDate.dayOfWeek == anchor.dayOfWeek
        ScheduleCadence.SPECIFIC_DAYS -> specificDays.contains(targetDate.dayOfWeek)
        ScheduleCadence.ALTERNATE_DAYS -> {
            val diff = targetEpoch - anchorEpochDay
            diff >= 0 && (diff % 2L == 0L)
        }
        ScheduleCadence.CUSTOM_INTERVAL -> {
            val step = customEvery.coerceAtLeast(1)
            when (customUnit) {
                CustomIntervalUnit.DAYS -> {
                    val diff = targetEpoch - anchorEpochDay
                    diff >= 0 && (diff % step.toLong() == 0L)
                }
                CustomIntervalUnit.WEEKS -> {
                    val diff = targetEpoch - anchorEpochDay
                    diff >= 0 && (diff % (step * 7L) == 0L)
                }
                CustomIntervalUnit.MONTHS -> {
                    if (targetDate.isBefore(anchor)) false
                    else {
                        val monthsBetween =
                            (targetDate.year - anchor.year) * 12 + (targetDate.monthValue - anchor.monthValue)
                        monthsBetween >= 0 && (monthsBetween % step == 0) &&
                            targetDate.dayOfMonth == minOf(anchor.dayOfMonth, targetDate.lengthOfMonth())
                    }
                }
                CustomIntervalUnit.YEARS -> {
                    if (targetDate.isBefore(anchor)) false
                    else {
                        val yearsBetween = targetDate.year - anchor.year
                        yearsBetween >= 0 && (yearsBetween % step == 0) &&
                            targetDate.month == anchor.month &&
                            targetDate.dayOfMonth == minOf(anchor.dayOfMonth, targetDate.lengthOfMonth())
                    }
                }
            }
        }
    }
}
