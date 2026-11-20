package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.habits.HabitState
import com.app.habits.TrackerItemType
import java.time.LocalDate

@Composable
fun HistoryDrawerContent(
    habitState: HabitState,
    onClose: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val habitCount = habitState.items.count { it.type == TrackerItemType.HABIT }
    val taskCount = habitState.items.count { it.type == TrackerItemType.TASK }
    val reminderCount = habitState.items.count { it.type == TrackerItemType.REMINDER }
    val bestStreak = habitState.items.maxOfOrNull { it.streak } ?: 0

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 270.dp, max = 330.dp)
            .background(LairPalette.SurfaceCard)
            .border(1.dp, LairPalette.BorderSubtle)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "HABITLAIR LOGS",
                color = primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .pressScale { onClose() }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✕", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        // Streaks & Totals summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceElevated, RoundedCornerShape(12.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Streaks & Totals", color = secondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text("Best Streak: ${bestStreak}d", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("Total Logs: ${habitState.logs.size}", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("Habits: $habitCount • Tasks: $taskCount • Reminders: $reminderCount", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        // Active Streaks list
        Text("Active Streaks", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        if (habitState.items.isEmpty()) {
            Text("No tracked items yet.", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            habitState.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.title} (${item.type.name.lowercase()})",
                        color = LairPalette.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.streak}d",
                        color = primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Completion & Logging History
        Text("Completion History", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        if (habitState.logs.isEmpty()) {
            Text("No completed items logged yet.", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            habitState.logs.take(30).forEach { log ->
                val dateStr = LocalDate.ofEpochDay(log.dateEpochDay).toString()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${log.itemTitle} • ${log.itemType.name}",
                        color = LairPalette.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$dateStr @ ${log.completedAtTime}",
                        color = primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (log.note.isNotBlank()) {
                        Text(log.note, color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (log.proofPhotoPath != null) {
                        InlineMediaImage(localPath = log.proofPhotoPath, heightDp = 95)
                    }
                }
            }
        }
    }
}
