package com.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.app.ai.AiThemePreset
import com.app.ai.UiDensityMode
import com.app.backup.BackupManager
import com.app.notifications.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppSettingsScreen(
    aiState: AiSettingsState,
    backupManager: BackupManager,
    onSaveAiState: (AiSettingsState) -> Unit,
    onBackupRestored: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenAiStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    var providerName by remember(aiState) { mutableStateOf(aiState.providerName) }
    var endpointUrl by remember(aiState) { mutableStateOf(aiState.endpointUrl) }
    var apiKey by remember(aiState) { mutableStateOf(aiState.apiKey) }
    var modelId by remember(aiState) { mutableStateOf(aiState.modelId) }
    var enableFailover by remember(aiState) { mutableStateOf(aiState.enableFailover) }
    var fallbackModelId by remember(aiState) { mutableStateOf(aiState.fallbackModelId) }
    var themePreset by remember(aiState) { mutableStateOf(aiState.themePreset) }
    var useDynamicMaterialYou by remember(aiState) { mutableStateOf(aiState.useDynamicMaterialYou) }
    var useCustomColors by remember(aiState) { mutableStateOf(aiState.useCustomColors) }
    var customPrimaryHex by remember(aiState) { mutableStateOf(aiState.customPrimaryHex) }
    var customSecondaryHex by remember(aiState) { mutableStateOf(aiState.customSecondaryHex) }
    var densityMode by remember(aiState) { mutableStateOf(aiState.densityMode) }
    var showDateWheel by remember(aiState) { mutableStateOf(aiState.showDateWheel) }
    var notificationsEnabled by remember(aiState) { mutableStateOf(aiState.notificationsEnabled) }
    var alarmSoundEnabled by remember(aiState) { mutableStateOf(aiState.alarmSoundEnabled) }
    var vibrateEnabled by remember(aiState) { mutableStateOf(aiState.vibrateEnabled) }
    var autoBackupEnabled by remember(aiState) { mutableStateOf(aiState.autoBackupEnabled) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            backupManager.exportToUri(uri)
                .onSuccess { statusBanner = it }
                .onFailure { statusBanner = it.message ?: "Export failed." }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupManager.restoreFromUri(uri)
                .onSuccess { msg ->
                    statusBanner = msg
                    onBackupRestored()
                }
                .onFailure { statusBanner = it.message ?: "Restore failed." }
        }
    }

    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val lastBackupMs = backupManager.lastBackupMillis()
    val snapshots = remember(statusBanner) { backupManager.listSnapshotFiles() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitLairTopBar(
            sectionTitle = "SETTINGS",
            sectionSubtitle = "Theme, UI, AI, notifications, alarms & backups",
            badgeText = themePreset.name.take(6),
            onOpenDrawer = onOpenDrawer,
            onOpenSettings = {}
        )

        // 1. Theme & UI Customization
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Theme & UI Customization", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Material You Dynamic Colors", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = useDynamicMaterialYou,
                    onCheckedChange = { useDynamicMaterialYou = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = onPrimary, checkedTrackColor = primary)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Show Date Wheel Strip", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = showDateWheel,
                    onCheckedChange = { showDateWheel = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = onPrimary, checkedTrackColor = primary)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiDensityMode.entries.forEach { mode ->
                    val active = densityMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale { densityMode = mode }
                            .background(if (active) primary else LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mode.label, color = if (active) onPrimary else LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AiThemePreset.entries.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { p ->
                        val active = themePreset == p && !useCustomColors
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale {
                                    themePreset = p
                                    useCustomColors = false
                                }
                                .background(if (active) primary else LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                                .padding(vertical = 9.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.label,
                                color = if (active) onPrimary else LairPalette.TextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Custom Hex Accent Override", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = useCustomColors,
                    onCheckedChange = { useCustomColors = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = onPrimary, checkedTrackColor = primary)
                )
            }

            if (useCustomColors) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customPrimaryHex,
                        onValueChange = { customPrimaryHex = it },
                        label = { Text("Primary #HEX", color = LairPalette.TextSecondary) },
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
                        value = customSecondaryHex,
                        onValueChange = { customSecondaryHex = it },
                        label = { Text("Secondary #HEX", color = LairPalette.TextSecondary) },
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
            }
        }

        // 2. Notifications & Alarms
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Notifications & Alarms", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Reminder Notifications", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Alert Sound", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = alarmSoundEnabled, onCheckedChange = { alarmSoundEnabled = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibration", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = vibrateEnabled, onCheckedChange = { vibrateEnabled = it })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { ReminderScheduler.triggerImmediateTestNotification(context, isAlarm = false) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Test Notification", color = primary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { ReminderScheduler.triggerImmediateTestNotification(context, isAlarm = true) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Test Alarm", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. AI Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("AI Provider & Models", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text("Provider name", color = LairPalette.TextSecondary) },
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
                value = endpointUrl,
                onValueChange = { endpointUrl = it },
                label = { Text("Endpoint URL", color = LairPalette.TextSecondary) },
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
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key", color = LairPalette.TextSecondary) },
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
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Primary Model ID", color = LairPalette.TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary,
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Fallback Model Failover", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enableFailover, onCheckedChange = { enableFailover = it })
            }
            if (enableFailover) {
                OutlinedTextField(
                    value = fallbackModelId,
                    onValueChange = { fallbackModelId = it },
                    label = { Text("Fallback Model ID", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Backups & Restore
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Backups & Restore", color = LairPalette.TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                text = if (lastBackupMs > 0L) "Last backup: ${fmt.format(Date(lastBackupMs))} • ${snapshots.size} local snapshot(s)" else "No local snapshot taken yet.",
                color = LairPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Periodic Auto-Snapshot", color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = autoBackupEnabled, onCheckedChange = { autoBackupEnabled = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale {
                            val snap = backupManager.createLocalSnapshot()
                            statusBanner = "Saved snapshot: ${snap.name}"
                        }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Snapshot Now", color = primary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { exportBackupLauncher.launch("habitlair_backup.json") }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Export JSON", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { importBackupLauncher.launch(arrayOf("application/json", "text/*")) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Import JSON", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        statusBanner?.let { msg ->
            Text(msg, color = primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale {
                    val updated = AiSettingsState(
                        providerName = providerName.trim().ifEmpty { "Custom" },
                        endpointUrl = endpointUrl.trim(),
                        apiKey = apiKey.trim(),
                        modelId = modelId.trim(),
                        enableFailover = enableFailover,
                        fallbackModelId = fallbackModelId.trim(),
                        themePreset = themePreset,
                        useDynamicMaterialYou = useDynamicMaterialYou,
                        customPrimaryHex = customPrimaryHex.trim(),
                        customSecondaryHex = customSecondaryHex.trim(),
                        useCustomColors = useCustomColors,
                        densityMode = densityMode,
                        showDateWheel = showDateWheel,
                        notificationsEnabled = notificationsEnabled,
                        alarmSoundEnabled = alarmSoundEnabled,
                        vibrateEnabled = vibrateEnabled,
                        autoBackupEnabled = autoBackupEnabled
                    )
                    onSaveAiState(updated)
                    statusBanner = "All settings saved."
                }
                .background(primary, RoundedCornerShape(12.dp))
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Save Settings", color = onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
