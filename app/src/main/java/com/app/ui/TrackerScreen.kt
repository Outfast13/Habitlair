package com.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.habits.*
import com.app.notifications.ReminderScheduler
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun TrackerScreen(
    fixedType: TrackerItemType,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    settings: AiSettingsState,
    initialState: HabitState,
    storage: HabitStorage,
    onOpenDrawer: () -> Unit,
    onStateChanged: (HabitState) -> Unit
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    var state by remember(initialState) { mutableStateOf(initialState) }
    var titleInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("General") }
    var notesInput by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf(ScheduleCadence.DAILY) }
    var scheduledTime by remember { mutableStateOf("08:00") }
    var alarmEnabled by remember { mutableStateOf(fixedType == TrackerItemType.REMINDER) }
    var customEveryText by remember { mutableStateOf("1") }
    var customUnit by remember { mutableStateOf(CustomIntervalUnit.MONTHS) }
    var selectedDays by remember {
        mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
    }
    var draftMedia by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }
    var showOnlyScheduledOnDate by remember { mutableStateOf(true) }

    // Completion logging modal / inline form state
    var loggingItem by remember { mutableStateOf<TrackerItem?>(null) }
    var logTimeInput by remember { mutableStateOf("") }
    var logNoteInput by remember { mutableStateOf("") }
    var logProofPath by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val proofPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importUriToDirectory(context, uri, storage.getMediaDirectory())
                .onSuccess { att -> logProofPath = att.localPath }
                .onFailure { err -> statusMessage = err.message ?: "Photo attach failed." }
        }
    }

    val epochDay = selectedDate.toEpochDay()
    val completedItemIdsOnDate = remember(state.logs, epochDay) {
        state.logs.filter { it.dateEpochDay == epochDay }.map { it.itemId }.toSet()
    }

    val matchingItems = remember(state.items, fixedType, selectedDate, showOnlyScheduledOnDate) {
        state.items.filter { item ->
            item.type == fixedType &&
                (!showOnlyScheduledOnDate || fixedType == TrackerItemType.TASK || item.schedule.isScheduledForDate(selectedDate))
        }
    }

    val sectionMeta = when (fixedType) {
        TrackerItemType.HABIT -> Triple("HABITS", "Timed logs, proof photos & cadence streaks", "Habit")
        TrackerItemType.TASK -> Triple("TASKS", "Tasks, media notes & date completions", "Task")
        TrackerItemType.REMINDER -> Triple("REMINDERS", "Daily, weekly, alternate, custom & alarms", "Reminder")
    }

    fun commit(updated: HabitState) {
        state = updated
        onStateChanged(updated)
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
            sectionTitle = sectionMeta.first,
            sectionSubtitle = sectionMeta.second,
            badgeText = "${completedItemIdsOnDate.count { id -> matchingItems.any { it.id == id } }}/${matchingItems.size} done",
            onOpenDrawer = onOpenDrawer
        )

        if (settings.showDateWheel) {
            TinyDateWheelBar(
                selectedDate = selectedDate,
                onSelectDate = onSelectDate
            )
        }

        if (fixedType != TrackerItemType.TASK) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter to schedule on ${selectedDate.monthValue}/${selectedDate.dayOfMonth}",
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = showOnlyScheduledOnDate,
                    onCheckedChange = { showOnlyScheduledOnDate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onPrimary,
                        checkedTrackColor = primary
                    )
                )
            }
        }

        // Creation card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Create ${sectionMeta.third}",
                color = LairPalette.TextPrimary,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("${sectionMeta.third} title", color = LairPalette.TextSecondary) },
                singleLine = true,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = categoryInput,
                    onValueChange = { categoryInput = it },
                    label = { Text("Category", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = scheduledTime,
                    onValueChange = { scheduledTime = it },
                    label = { Text("Time (HH:mm)", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Notes / details", color = LairPalette.TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary,
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Repeat Schedule", color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)

            ScheduleCadence.entries.chunked(3).forEach { rowCadences ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowCadences.forEach { c ->
                        val active = cadence == c
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale { cadence = c }
                                .background(
                                    if (active) primary else LairPalette.SurfaceElevated,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) primary else LairPalette.BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = c.label,
                                color = if (active) onPrimary else LairPalette.TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (cadence == ScheduleCadence.SPECIFIC_DAYS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.entries.forEach { dow ->
                        val picked = selectedDays.contains(dow)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale {
                                    selectedDays = if (picked) selectedDays - dow else selectedDays + dow
                                }
                                .background(
                                    if (picked) primary else LairPalette.SurfaceElevated,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dow.name.take(2),
                                color = if (picked) onPrimary else LairPalette.TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (cadence == ScheduleCadence.CUSTOM_INTERVAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customEveryText,
                        onValueChange = { customEveryText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Every N", color = LairPalette.TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LairPalette.TextPrimary,
                            unfocusedTextColor = LairPalette.TextPrimary,
                            focusedBorderColor = primary,
                            unfocusedBorderColor = LairPalette.BorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    CustomIntervalUnit.entries.forEach { unit ->
                        val active = customUnit == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale { customUnit = unit }
                                .background(
                                    if (active) primary else LairPalette.SurfaceElevated,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unit.label,
                                color = if (active) onPrimary else LairPalette.TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set Alarm / High-Priority Alert",
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = alarmEnabled,
                    onCheckedChange = { alarmEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onPrimary,
                        checkedTrackColor = primary
                    )
                )
            }

            MediaPickerStrip(
                targetDir = storage.getMediaDirectory(),
                onMediaAdded = { draftMedia = draftMedia + it },
                onError = { statusMessage = it }
            )

            draftMedia.forEach { att ->
                MediaAttachmentCard(
                    att = att,
                    onOpen = { openMediaAttachmentIntent(context, it) { err -> statusMessage = err } },
                    onRemove = {
                        File(att.localPath).delete()
                        draftMedia = draftMedia.filterNot { m -> m.id == att.id }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale {
                        val clean = titleInput.trim()
                        if (clean.isNotEmpty()) {
                            val rule = ScheduleRule(
                                cadence = cadence,
                                specificDays = selectedDays.toList().ifEmpty { listOf(selectedDate.dayOfWeek) },
                                customEvery = (customEveryText.toIntOrNull() ?: 1).coerceAtLeast(1),
                                customUnit = customUnit,
                                anchorEpochDay = selectedDate.toEpochDay(),
                                scheduledTime = scheduledTime.trim().ifBlank { "08:00" },
                                alarmEnabled = alarmEnabled
                            )
                            val newItem = TrackerItem(
                                id = UUID.randomUUID().toString(),
                                title = clean,
                                type = fixedType,
                                category = categoryInput.trim().ifEmpty { "General" },
                                notes = notesInput.trim(),
                                schedule = rule,
                                attachments = draftMedia
                            )
                            commit(state.copy(items = listOf(newItem) + state.items))
                            ReminderScheduler.scheduleForItem(context, newItem, selectedDate)
                            titleInput = ""
                            notesInput = ""
                            draftMedia = emptyList()
                            statusMessage = "Saved ${sectionMeta.third} & scheduled reminder at ${rule.scheduledTime}."
                        }
                    }
                    .background(primary, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save ${sectionMeta.third}",
                    color = onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        statusMessage?.let { msg ->
            Text(
                text = msg,
                color = primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Timed Completion Logger Card when user taps Log / Done
        loggingItem?.let { target ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(16.dp))
                    .border(1.5.dp, primary, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Log Completion: ${target.title}",
                    color = LairPalette.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = logTimeInput,
                    onValueChange = { logTimeInput = it },
                    label = { Text("Completion Time (e.g. 21:15)", color = LairPalette.TextSecondary) },
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
                    value = logNoteInput,
                    onValueChange = { logNoteInput = it },
                    label = { Text("Optional log note", color = LairPalette.TextSecondary) },
                    singleLine = true,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale { proofPhotoPicker.launch(arrayOf("image/*")) }
                            .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (logProofPath != null) "Proof Photo Attached ✓" else "+ Add Proof Photo",
                            color = LairPalette.TextPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (logProofPath != null) {
                        Box(
                            modifier = Modifier
                                .pressScale { logProofPath = null }
                                .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Text("✕", color = LairPalette.DangerRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                logProofPath?.let { path ->
                    InlineMediaImage(localPath = path, heightDp = 120)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale {
                                val timeStr = logTimeInput.trim().ifEmpty {
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                                }
                                val logEntry = CompletionLog(
                                    id = UUID.randomUUID().toString(),
                                    itemId = target.id,
                                    itemTitle = target.title,
                                    itemType = target.type,
                                    dateEpochDay = epochDay,
                                    completedAtTime = timeStr,
                                    completedAtMillis = System.currentTimeMillis(),
                                    proofPhotoPath = logProofPath,
                                    note = logNoteInput.trim()
                                )
                                val updatedItems = state.items.map { existing ->
                                    if (existing.id == target.id) existing.copy(streak = existing.streak + 1) else existing
                                }
                                commit(
                                    state.copy(
                                        items = updatedItems,
                                        logs = listOf(logEntry) + state.logs
                                    )
                                )
                                loggingItem = null
                                logProofPath = null
                                logNoteInput = ""
                                statusMessage = "Logged '${target.title}' at $timeStr."
                            }
                            .background(primary, RoundedCornerShape(10.dp))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save Log", color = onPrimary, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale {
                                loggingItem = null
                                logProofPath = null
                            }
                            .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = LairPalette.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Items List
        if (matchingItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${sectionMeta.first.lowercase()} match this date filter.",
                    color = LairPalette.TextSecondary
                )
            }
        } else {
            matchingItems.forEach { item ->
                val dayLogs = state.logs.filter { it.itemId == item.id && it.dateEpochDay == epochDay }
                val isDoneToday = dayLogs.isNotEmpty()
                val badgeBg by animateColorAsState(
                    targetValue = if (isDoneToday) primary else LairPalette.SurfaceElevated,
                    animationSpec = tween(200),
                    label = "itemBadgeBg"
                )
                val badgeFg by animateColorAsState(
                    targetValue = if (isDoneToday) onPrimary else LairPalette.TextPrimary,
                    animationSpec = tween(200),
                    label = "itemBadgeFg"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = LairPalette.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.category} • ${item.schedule.cadence.label} @ ${item.schedule.scheduledTime}${if (item.schedule.alarmEnabled) " • Alarm" else ""} • Streak ${item.streak}d",
                                color = primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    color = LairPalette.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (dayLogs.isNotEmpty()) {
                                Text(
                                    text = "Logged @ ${dayLogs.joinToString(", ") { it.completedAtTime }}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .pressScale {
                                        loggingItem = item
                                        logTimeInput = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                                        logNoteInput = ""
                                        logProofPath = null
                                    }
                                    .background(badgeBg, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isDoneToday) "Log +1" else "Log",
                                    color = badgeFg,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .pressScale {
                                        item.attachments.forEach { File(it.localPath).delete() }
                                        commit(
                                            state.copy(
                                                items = state.items.filterNot { it.id == item.id },
                                                logs = state.logs.filterNot { it.itemId == item.id }
                                            )
                                        )
                                    }
                                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 9.dp, vertical = 8.dp)
                            ) {
                                Text("✕", color = LairPalette.DangerRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    dayLogs.mapNotNull { it.proofPhotoPath }.forEach { proofPath ->
                        InlineMediaImage(localPath = proofPath, heightDp = 120)
                    }

                    item.attachments.forEach { att ->
                        MediaAttachmentCard(
                            att = att,
                            onOpen = { openMediaAttachmentIntent(context, it) { err -> statusMessage = err } }
                        )
                    }
                }
            }
        }
    }
}
