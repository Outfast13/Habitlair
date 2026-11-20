package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.habits.HabitState
import com.app.habits.TrackerItemType
import java.time.LocalDate

@Composable
fun HistoryDrawerContent(
    habitState: HabitState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAi: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val habitCount = habitState.items.count { it.type == TrackerItemType.HABIT }
    val taskCount = habitState.items.count { it.type == TrackerItemType.TASK }
    val reminderCount = habitState.items.count { it.type == TrackerItemType.REMINDER }
    val bestStreak = habitState.items.maxOfOrNull { it.streak } ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 270.dp, max = 330.dp)
            .background(LairPalette.SurfaceCard)
            .border(1.dp, LairPalette.BorderSubtle),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "drawer_top") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HABITLAIR",
                    color = primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .pressScale { onClose() }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = LairPalette.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item(key = "drawer_shortcuts") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { onOpenSettings() }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text("Settings", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { onOpenAi() }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = secondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text("AI Studio", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item(key = "drawer_summary") {
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
        }

        item(key = "streaks_header") {
            Text("Active Streaks", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        }

        if (habitState.items.isEmpty()) {
            item(key = "no_streaks") {
                Text("No tracked items yet.", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(habitState.items, key = { "streak_${it.id}" }) { item ->
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

        item(key = "history_header") {
            Text("Completion History", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        }

        if (habitState.logs.isEmpty()) {
            item(key = "no_logs") {
                Text("No completed items logged yet.", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(habitState.logs.take(30), key = { "log_${it.id}" }) { log ->
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
