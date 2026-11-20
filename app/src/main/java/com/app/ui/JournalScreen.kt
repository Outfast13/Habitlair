package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.habits.MediaAttachment
import com.app.journal.JournalEntry
import com.app.journal.JournalState
import com.app.journal.JournalStorage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val MOOD_OPTIONS = listOf("Focused", "Calm", "Driven", "Tired", "Reflective")

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
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary

    var state by remember(initialState) { mutableStateOf(initialState) }
    var showEditor by remember { mutableStateOf(false) }

    val selectedEpoch = selectedDate.toEpochDay()
    val zone = remember { ZoneId.systemDefault() }

    val filteredEntries = remember(state.entries, selectedEpoch, zone) {
        state.entries.filter { entry ->
            val entryDay = Instant.ofEpochMilli(entry.timestampMs).atZone(zone).toLocalDate().toEpochDay()
            entryDay == selectedEpoch
        }.sortedByDescending { it.timestampMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HabitLairTopBar(
                sectionTitle = "Journal",
                sectionSubtitle = "Daily notes & media logs",
                badgeText = "${filteredEntries.size} entries",
                onOpenDrawer = onOpenDrawer,
                onOpenSettings = onOpenSettings
            )

            if (settings.showDateWheel) {
                TinyDateWheelBar(selectedDate = selectedDate, onSelectDate = onSelectDate)
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No journal entries on this day yet. Tap + to capture notes or media.",
                        color = LairPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val timeText = remember(entry.timestampMs) {
                            Instant.ofEpochMilli(entry.timestampMs)
                                .atZone(zone)
                                .format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.title.ifBlank { "Entry" },
                                    color = LairPalette.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${entry.mood} • $timeText",
                                    color = secondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.padding(start = 6.dp))
                                Box(
                                    modifier = Modifier.pressScale {
                                        val updated = state.copy(entries = state.entries.filterNot { it.id == entry.id })
                                        state = updated
                                        onStateChanged(updated)
                                    }
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = LairPalette.TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (entry.body.isNotBlank()) {
                                Text(entry.body, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                            AttachmentPreviewStrip(attachments = entry.attachments)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showEditor = true },
            containerColor = primary,
            contentColor = onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "New journal entry")
        }
    }

    if (showEditor) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var mood by remember { mutableStateOf(MOOD_OPTIONS.first()) }
        var attachments by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { showEditor = false },
            containerColor = LairPalette.SurfaceCard,
            title = { Text("New Journal Entry", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = LairPalette.BorderSubtle,
                            focusedTextColor = LairPalette.TextPrimary,
                            unfocusedTextColor = LairPalette.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MOOD_OPTIONS, key = { it }) { m ->
                            val active = mood == m
                            Box(
                                modifier = Modifier
                                    .pressScale { mood = m }
                                    .background(if (active) primary else LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(m, color = if (active) onPrimary else LairPalette.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Thoughts / Notes") },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = LairPalette.BorderSubtle,
                            focusedTextColor = LairPalette.TextPrimary,
                            unfocusedTextColor = LairPalette.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    MediaPickerActionRow(
                        targetDir = storage.getMediaDirectory(),
                        onMediaAdded = { att: MediaAttachment -> attachments = attachments + att }
                    )
                    AttachmentPreviewStrip(
                        attachments = attachments,
                        onRemove = { rem: MediaAttachment -> attachments = attachments.filterNot { it.id == rem.id } }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() || body.isNotBlank() || attachments.isNotEmpty()) {
                            val ts = if (selectedDate == LocalDate.now()) {
                                System.currentTimeMillis()
                            } else {
                                selectedDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                            }
                            val entry = JournalEntry(
                                id = UUID.randomUUID().toString(),
                                title = title.trim().ifBlank { "Reflection" },
                                body = body.trim(),
                                mood = mood,
                                timestampMs = ts,
                                attachments = attachments
                            )
                            val updated = state.copy(entries = listOf(entry) + state.entries)
                            state = updated
                            onStateChanged(updated)
                            showEditor = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditor = false }) {
                    Text("Cancel", color = LairPalette.TextSecondary)
                }
            }
        )
    }
}
