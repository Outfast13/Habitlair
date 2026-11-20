package com.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.app.ai.AiSettingsState
import com.app.habits.*
import com.app.notifications.ReminderScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
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
    onOpenSettings: () -> Unit,
    onStateChanged: (HabitState) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
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

    var loggingItem by remember { mutableStateOf<TrackerItem?>(null) }
    var logTimeInput by remember { mutableStateOf("") }
    var logNoteInput by remember { mutableStateOf("") }
    var logProofPath by remember { mutableStateOf<String?>(null) }
    var pendingProofFile by remember { mutableStateOf<File?>(null) }
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

    val proofCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingProofFile
        pendingProofFile = null
        if (saved && file != null && file.exists() && file.length() > 0L) {
            logProofPath = file.absolutePath
        } else {
            file?.delete()
            statusMessage = "Camera proof capture cancelled."
        }
    }

    fun captureProofPhoto() {
        runCatching {
            val dir = storage.getMediaDirectory()
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "PROOF_${stamp}_${UUID.randomUUID().toString().take(6)}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingProofFile = file
            proofCameraLauncher.launch(uri)
        }.onFailure { err ->
            statusMessage = err.message ?: "Unable to start camera."
        }
    }

    val epochDay = selectedDate.toEpochDay()
    val logsByItemId = remember(state.logs, epochDay) {
        state.logs.filter { it.dateEpochDay == epochDay }.groupBy { it.itemId }
    }

    val matchingItems = remember(state.items, fixedType, selectedDate, showOnlyScheduledOnDate) {
        state.items.filter { item ->
            item.type == fixedType &&
                (!showOnlyScheduledOnDate || fixedType == TrackerItemType.TASK || item.schedule.isScheduledForDate(selectedDate))
        }
    }

    val doneCountOnScreen = remember(matchingItems, logsByItemId) {
        matchingItems.count { !logsByItemId[it.id].isNullOrEmpty() }
    }

    val sectionMeta = when (fixedType) {
        TrackerItemType.HABIT -> Triple("HABITS", "Daily cadence, timed logs & streaks", "Habit")
        TrackerItemType.TASK -> Triple("TASKS", "Tasks, media notes & completions", "Task")
        TrackerItemType.REMINDER -> Triple("REMINDERS", "Schedules & alarm triggers", "Reminder")
    }

    fun commit(updated: HabitState) {
        state = updated
        onStateChanged(updated)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            HabitLairTopBar(
                sectionTitle = sectionMeta.first,
                sectionSubtitle = sectionMeta.second,
                badgeText = "$doneCountOnScreen/${matchingItems.size}",
                onOpenDrawer = onOpenDrawer,
                onOpenSettings = onOpenSettings
            )
        }

        if (settings.showDateWheel) {
            item(key = "date_wheel") {
                TinyDateWheelBar(
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate
                )
            }
        }

        if (fixedType != TrackerItemType.TASK) {
            item(key = "schedule_filter") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scheduled for ${selectedDate.monthValue}/${selectedDate.dayOfMonth}",
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
        }

        statusMessage?.let { msg ->
            item(key = "status_msg") {
                Text(
                    text = msg,
                    color = primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        loggingItem?.let { target ->
            item(key = "completion_logger_${target.id}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(14.dp))
                        .border(1.dp, primary, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Log: ${target.title}",
                        color = LairPalette.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = logTimeInput,
                        onValueChange = { logTimeInput = it },
                        label = { Text("Time (HH:mm)", color = LairPalette.TextSecondary) },
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
                        label = { Text("Note (optional)", color = LairPalette.TextSecondary) },
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
                                .pressScale(haptic = true) { captureProofPhoto() }
                                .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Take Photo",
                                    color = LairPalette.TextPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale { proofPhotoPicker.launch(arrayOf("image/*")) }
                                .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (logProofPath != null) "Photo Set" else "Gallery",
                                    color = LairPalette.TextPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (logProofPath != null) {
                            Box(
                                modifier = Modifier
                                    .pressScale { logProofPath = null }
                                    .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear photo",
                                    tint = LairPalette.DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
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
                                .pressScale(haptic = true) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
        }

        if (matchingItems.isEmpty()) {
            item(key = "empty_list") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${sectionMeta.first.lowercase()} for this date.",
                        color = LairPalette.TextSecondary
                    )
                }
            }
        } else {
            items(matchingItems, key = { it.id }) { item ->
                val dayLogs = logsByItemId[item.id].orEmpty()
                val isDoneToday = dayLogs.isNotEmpty()
                val (catIcon, catTint) = remember(item.category) { categoryBadgeIcon(item.category) }

                val checkBg by animateColorAsState(
                    targetValue = if (isDoneToday) primary else LairPalette.SurfaceElevated,
                    animationSpec = tween(durationMillis = 180),
                    label = "habitCheckBg"
                )
                val checkScale by animateFloatAsState(
                    targetValue = if (isDoneToday) 1.05f else 1f,
                    animationSpec = tween(durationMillis = 160),
                    label = "habitCheckScale"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(catTint.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = item.category,
                                tint = catTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = LairPalette.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(catTint.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.category,
                                        color = LairPalette.TextPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }

                                Icon(
                                    imageVector = if (item.schedule.alarmEnabled) Icons.Outlined.Alarm else Icons.Outlined.NotificationsNone,
                                    contentDescription = null,
                                    tint = LairPalette.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = item.schedule.scheduledTime,
                                    color = LairPalette.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (item.streak > 0) {
                                    Text(
                                        text = "• ${item.streak}d",
                                        color = primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    color = LairPalette.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            if (dayLogs.isNotEmpty()) {
                                Text(
                                    text = "Done @ ${dayLogs.joinToString(", ") { it.completedAtTime }}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                                .pressScale(haptic = true) {
                                    loggingItem = item
                                    logTimeInput = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                                    logNoteInput = ""
                                    logProofPath = null
                                }
                                .clip(CircleShape)
                                .background(checkBg)
                                .border(
                                    width = if (isDoneToday) 0.dp else 1.5.dp,
                                    color = if (isDoneToday) primary else LairPalette.BorderSubtle,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDoneToday) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Completed",
                                    tint = onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .pressScale {
                                    item.attachments.forEach { File(it.localPath).delete() }
                                    commit(
                                        state.copy(
                                            items = state.items.filterNot { it.id == item.id },
                                            logs = state.logs.filterNot { it.itemId == item.id }
                                        )
                                    )
                                }
                                .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Delete",
                                tint = LairPalette.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
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

        // Creation card below list for clean scanability
        item(key = "create_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "New ${sectionMeta.third}",
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
                    label = { Text("Notes", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

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
                        text = "Alarm Alert",
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
                        .pressScale(haptic = true) {
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
                                statusMessage = "Saved ${sectionMeta.third}."
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
        }
    }
}
