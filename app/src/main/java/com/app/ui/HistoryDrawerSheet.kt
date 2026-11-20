package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.habits.HabitState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryDrawerContent(
    habitState: HabitState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAi: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val recentLogs = remember(habitState.logs) {
        habitState.logs.sortedByDescending { it.completedAtMillis }.take(10)
    }

    Column(
        modifier = Modifier
            .width(308.dp)
            .fillMaxHeight()
            .background(LairPalette.SurfaceCard)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = primary)
                Text("HabitLair", color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.pressScale(onClose)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close drawer", tint = LairPalette.TextSecondary)
            }
        }

        HorizontalDivider(color = DividerDefaults.color)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(onOpenAi)
                .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = primary)
            Text("AI Coach & Overuse Guard", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(onOpenSettings)
                .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text("Settings & Backups", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
        }

        Text("Completion History", color = LairPalette.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (recentLogs.isEmpty()) {
            Text("No completion logs recorded yet.", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            recentLogs.forEach { log ->
                val dayLabel = remember(log.dateEpochDay) {
                    LocalDate.ofEpochDay(log.dateEpochDay)
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(log.itemTitle, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("$dayLabel • ${log.completedAtTime}", color = LairPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    if (log.note.isNotBlank()) {
                        Text(log.note, color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    val proof = log.proofPhotoPath
                    if (!proof.isNullOrBlank()) {
                        InlineMediaImage(path = proof, contentDescription = "Proof")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
