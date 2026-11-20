package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.habits.MediaAttachment
import com.app.journal.JournalEntry
import com.app.journal.JournalState
import com.app.journal.JournalStorage
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

private val MOOD_OPTIONS = listOf("Focused", "Calm", "Neutral", "Tired", "Stressed")

@Composable
fun JournalScreen(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    settings: AiSettingsState,
    initialState: JournalState,
    storage: JournalStorage,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onStateChanged: (JournalState) -> Unit
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    var state by remember(initialState) { mutableStateOf(initialState) }
    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Focused") }
    var pendingMedia by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    fun commit(next: JournalState) {
        state = next
        onStateChanged(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitLairTopBar(
            sectionTitle = "JOURNAL",
            sectionSubtitle = "Photo, audio & video entries with moods",
            badgeText = "${state.entries.size} entries",
            onOpenDrawer = onOpenDrawer,
            onOpenSettings = onOpenSettings
        )

        if (settings.showDateWheel) {
            TinyDateWheelBar(
                selectedDate = selectedDate,
                onSelectDate = onSelectDate
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("New Journal Entry", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Title (optional)", color = LairPalette.TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary,
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bodyInput,
                onValueChange = { bodyInput = it },
                label = { Text("Write notes...", color = LairPalette.TextSecondary) },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary,
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MOOD_OPTIONS.forEach { mood ->
                    val active = mood == selectedMood
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale { selectedMood = mood }
                            .background(
                                if (active) primary else LairPalette.SurfaceElevated,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood,
                            color = if (active) onPrimary else LairPalette.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            MediaPickerStrip(
                targetDir = storage.getMediaDirectory(),
                onMediaAdded = { pendingMedia = pendingMedia + it },
                onError = { errorMsg = it }
            )

            pendingMedia.forEach { att ->
                MediaAttachmentCard(
                    att = att,
                    onOpen = { openMediaAttachmentIntent(context, it) { err -> errorMsg = err } },
                    onRemove = {
                        File(att.localPath).delete()
                        pendingMedia = pendingMedia.filterNot { m -> m.id == att.id }
                    }
                )
            }

            errorMsg?.let { err ->
                Text(err, color = LairPalette.DangerRed, style = MaterialTheme.typography.bodySmall)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale {
                        val t = titleInput.trim()
                        val b = bodyInput.trim()
                        if (t.isNotEmpty() || b.isNotEmpty() || pendingMedia.isNotEmpty()) {
                            val ts = selectedDate.atTime(java.time.LocalTime.now())
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val entry = JournalEntry(
                                id = UUID.randomUUID().toString(),
                                title = t.ifEmpty { formatter.format(Date(ts)) },
                                body = b,
                                mood = selectedMood,
                                timestampMs = ts,
                                attachments = pendingMedia
                            )
                            commit(state.copy(entries = listOf(entry) + state.entries))
                            titleInput = ""
                            bodyInput = ""
                            pendingMedia = emptyList()
                            errorMsg = null
                        }
                    }
                    .background(primary, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Save Entry", color = onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        state.entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${formatter.format(Date(entry.timestampMs))} • ${entry.mood}",
                            color = primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        modifier = Modifier
                            .pressScale {
                                entry.attachments.forEach { File(it.localPath).delete() }
                                commit(state.copy(entries = state.entries.filterNot { it.id == entry.id }))
                            }
                            .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Delete entry", tint = LairPalette.DangerRed, modifier = Modifier.size(16.dp))
                    }
                }
                if (entry.body.isNotBlank()) {
                    Text(entry.body, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                entry.attachments.forEach { att ->
                    MediaAttachmentCard(
                        att = att,
                        onOpen = { openMediaAttachmentIntent(context, it) { err -> errorMsg = err } }
                    )
                }
            }
        }
    }
}
