package com.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiProviderPreset
import com.app.ai.AiSettingsState
import com.app.ai.ProposedTrackerItem
import com.app.habits.HabitState
import com.app.habits.TrackerItemType
import com.app.habits.isScheduledForDate
import com.app.notifications.ScreenTimeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID

private data class QuickCoachPreset(
    val chipLabel: String,
    val title: String,
    val type: TrackerItemType,
    val category: String,
    val time: String,
    val evalMode: String,
    val reason: String,
    val prompt: String
)

@Composable
fun AiWorkspaceScreen(
    aiState: AiSettingsState,
    habitState: HabitState,
    screenTimeSnapshot: ScreenTimeSnapshot,
    onApproveProposedItem: (ProposedTrackerItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary

    var promptText by remember { mutableStateOf("") }
    var coachSummary by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var proposals by remember { mutableStateOf<List<ProposedTrackerItem>>(emptyList()) }

    val quickChips = remember {
        listOf(
            QuickCoachPreset(
                chipLabel = "Audit Now",
                title = "15m Priority Block",
                type = TrackerItemType.TASK,
                category = "Focus",
                time = "18:30",
                evalMode = "TIMER",
                reason = "Instant deep-focus sprint on open items.",
                prompt = "Audit my bad habits, missed items, and screen time overuse right now."
            ),
            QuickCoachPreset(
                chipLabel = "Cut Screen Time",
                title = "Phone-Free Reset (20m)",
                type = TrackerItemType.REMINDER,
                category = "Screen Guard",
                time = "21:00",
                evalMode = "TIMER",
                reason = "Breaks high screen-time loops before night.",
                prompt = "Create an anti-overuse screen reset routine for today."
            ),
            QuickCoachPreset(
                chipLabel = "Streak Rescue",
                title = "Minimum Viable Habit Check",
                type = TrackerItemType.HABIT,
                category = "Consistency",
                time = "19:30",
                evalMode = "YES_NO",
                reason = "Keeps momentum alive with a low-friction win.",
                prompt = "Rescue my zero-streak or lagging habits with a low-friction habit."
            ),
            QuickCoachPreset(
                chipLabel = "Focus Sprint",
                title = "Pomodoro Deep Block",
                type = TrackerItemType.TASK,
                category = "Study",
                time = "20:00",
                evalMode = "TIMER",
                reason = "Focused study/work block without distractions.",
                prompt = "Build a high-focus task block for my pending workload."
            )
        )
    }

    val today = remember { LocalDate.now() }
    val todayEpoch = today.toEpochDay()
    val scheduledToday = remember(habitState.items, today) {
        habitState.items.filter { it.schedule.isScheduledForDate(today) }
    }
    val completedTodayIds = remember(habitState.logs, todayEpoch) {
        habitState.logs.filter { it.dateEpochDay == todayEpoch }.map { it.itemId }.toSet()
    }
    val missedOrPendingToday = remember(scheduledToday, completedTodayIds) {
        scheduledToday.filterNot { completedTodayIds.contains(it.id) }
    }
    val overScreenLimit = aiState.screenTimeGuardEnabled &&
        screenTimeSnapshot.hasUsagePermission &&
        screenTimeSnapshot.todayMinutes >= aiState.dailyScreenLimitMinutes

    fun buildInstantDiagnostics(customGoal: String, presetAction: QuickCoachPreset? = null): Pair<String, List<ProposedTrackerItem>> {
        val alerts = buildList {
            if (overScreenLimit) {
                add("Screen overuse: ${screenTimeSnapshot.todayMinutes}m / ${aiState.dailyScreenLimitMinutes}m (${screenTimeSnapshot.topAppName}).")
            }
            if (missedOrPendingToday.isNotEmpty()) {
                add("Open today (${missedOrPendingToday.size}): ${missedOrPendingToday.take(3).joinToString { it.title }}.")
            }
            val zeroStreak = habitState.items.filter { it.type == TrackerItemType.HABIT && it.streak == 0 }
            if (zeroStreak.isNotEmpty()) {
                add("Zero streak: ${zeroStreak.take(2).joinToString { it.title }}.")
            }
        }
        val summary = if (alerts.isEmpty()) {
            "All clear. Tap a quick action below or drop a 1-line goal."
        } else {
            alerts.joinToString("\n• ", prefix = "• ")
        }

        val items = buildList {
            if (presetAction != null) {
                add(
                    ProposedTrackerItem(
                        id = UUID.randomUUID().toString(),
                        title = presetAction.title,
                        type = presetAction.type,
                        category = presetAction.category,
                        reason = presetAction.reason,
                        scheduledTime = presetAction.time,
                        evaluationMode = presetAction.evalMode
                    )
                )
            }
            if (customGoal.isNotBlank()) {
                add(
                    ProposedTrackerItem(
                        id = UUID.randomUUID().toString(),
                        title = customGoal.trim().take(48),
                        type = TrackerItemType.TASK,
                        category = "Quick Plan",
                        reason = "Generated from your command.",
                        scheduledTime = "19:00",
                        evaluationMode = "YES_NO"
                    )
                )
            }
            if (overScreenLimit) {
                add(
                    ProposedTrackerItem(
                        id = UUID.randomUUID().toString(),
                        title = "Screen-Free Reset (20m)",
                        type = TrackerItemType.REMINDER,
                        category = "Screen Guard",
                        reason = "Screen usage at ${screenTimeSnapshot.todayMinutes}m.",
                        scheduledTime = "21:00",
                        evaluationMode = "TIMER"
                    )
                )
            }
        }
        return summary to items
    }

    fun triggerCoach(customGoal: String, presetAction: QuickCoachPreset? = null) {
        val (instantSummary, instantProposals) = buildInstantDiagnostics(customGoal, presetAction)
        coachSummary = instantSummary
        proposals = instantProposals
        errorMessage = null

        val keys = aiState.allActiveKeys()
        val preset = AiProviderPreset.fromProviderNameOrId(aiState.providerId.ifBlank { aiState.providerName })
        val needsKey = preset != AiProviderPreset.OLLAMA && preset != AiProviderPreset.LM_STUDIO

        if (needsKey && keys.isEmpty()) {
            return
        }

        isLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val goalLine = customGoal.ifBlank { presetAction?.prompt ?: "Audit habits, pending items, and screen time." }
                val contextSnapshot = "Goal: $goalLine | Screen: ${screenTimeSnapshot.todayMinutes}m/${aiState.dailyScreenLimitMinutes}m (${screenTimeSnapshot.topAppName}) | Pending: ${missedOrPendingToday.take(5).joinToString { it.title }.ifBlank { "None" }}"
                val systemPrompt = "Direct routine coach. Reply ONLY JSON: {"coach_note":"short blunt note","proposals":[{"title":"...","type":"HABIT|TASK|REMINDER","category":"...","scheduled_time":"HH:mm","evaluation_mode":"YES_NO|NUMERIC|TIMER|CHECKLIST","reason":"..."}]}"

                val activeKey = if (needsKey) keys.first() else ""
                val call = executeAiChatCompletion(
                    preset = preset,
                    endpoint = aiState.endpointUrl,
                    apiKey = activeKey,
                    model = aiState.modelId,
                    systemPrompt = systemPrompt,
                    userPrompt = contextSnapshot
                )
                if (call.isSuccess) {
                    Result.success(call.getOrThrow())
                } else if (aiState.enableFailover && aiState.fallbackModelId.isNotBlank() && aiState.fallbackModelId != aiState.modelId) {
                    executeAiChatCompletion(
                        preset = preset,
                        endpoint = aiState.endpointUrl,
                        apiKey = activeKey,
                        model = aiState.fallbackModelId,
                        systemPrompt = systemPrompt,
                        userPrompt = contextSnapshot
                    )
                } else {
                    call
                }
            }

            isLoading = false
            result.onSuccess { rawContent ->
                val cleanedJson = extractJsonObject(rawContent)
                runCatching {
                    val obj = JSONObject(cleanedJson)
                    val note = obj.optString("coach_note", "").trim()
                    if (note.isNotEmpty()) coachSummary = note
                    val arr = obj.optJSONArray("proposals") ?: JSONArray()
                    val parsed = buildList {
                        for (i in 0 until arr.length()) {
                            val p = arr.optJSONObject(i) ?: continue
                            val pTitle = p.optString("title", "").trim()
                            if (pTitle.isEmpty()) continue
                            val pType = runCatching {
                                TrackerItemType.valueOf(p.optString("type", "HABIT").uppercase())
                            }.getOrDefault(TrackerItemType.HABIT)
                            add(
                                ProposedTrackerItem(
                                    id = UUID.randomUUID().toString(),
                                    title = pTitle,
                                    type = pType,
                                    category = p.optString("category", "AI Coach").ifBlank { "AI Coach" },
                                    reason = p.optString("reason", "AI suggestion"),
                                    scheduledTime = p.optString("scheduled_time", "18:00").ifBlank { "18:00" },
                                    evaluationMode = p.optString("evaluation_mode", "YES_NO")
                                )
                            )
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        proposals = parsed + instantProposals
                    }
                }
            }.onFailure { err ->
                errorMessage = err.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HabitLairTopBar(
            sectionTitle = "AI Coach",
            sectionSubtitle = "${aiState.providerName} • ${aiState.modelId}",
            badgeText = if (overScreenLimit) "Screen High" else "${screenTimeSnapshot.todayMinutes}m screen",
            onOpenDrawer = onOpenDrawer,
            onOpenSettings = onOpenSettings
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (overScreenLimit) LairPalette.DangerRed.copy(alpha = 0.16f) else LairPalette.SurfaceCard,
                    RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    if (overScreenLimit) LairPalette.DangerRed else LairPalette.BorderSubtle,
                    RoundedCornerShape(12.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (overScreenLimit) Icons.Outlined.WarningAmber else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (overScreenLimit) LairPalette.DangerRed else secondary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!screenTimeSnapshot.hasUsagePermission) {
                        "Usage Access off • ${missedOrPendingToday.size} open today"
                    } else {
                        "Screen ${screenTimeSnapshot.todayMinutes}m / ${aiState.dailyScreenLimitMinutes}m • ${missedOrPendingToday.size} open"
                    },
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // One-tap quick actions (zero typing needed)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                Text("One-Tap Actions", color = LairPalette.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickChips, key = { it.chipLabel }) { chip ->
                    Box(
                        modifier = Modifier
                            .pressScale { triggerCoach(promptText, chip) }
                            .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                            .border(1.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = chip.chipLabel,
                            color = LairPalette.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Optional quick input box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                Text("Quick Goal / Override", color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("Optional: quick task or goal...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle,
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { triggerCoach(promptText, null) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Syncing..." else "Run Now", fontWeight = FontWeight.Bold)
            }
        }

        errorMessage?.let { err ->
            Text(err, color = LairPalette.AccentAmber, style = MaterialTheme.typography.labelSmall)
        }

        coachSummary?.let { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Assessment", color = primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(note, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (proposals.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Add Items", color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                proposals.forEach { draft ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(draft.title, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${draft.type.name} • ${format24hToAmPm(draft.scheduledTime)}", color = secondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(draft.reason, color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onApproveProposedItem(draft)
                                    proposals = proposals.filterNot { it.id == draft.id }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Add", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { proposals = proposals.filterNot { it.id == draft.id } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Skip", color = LairPalette.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun extractJsonObject(raw: String): String {
    val trimmed = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
}

private fun executeAiChatCompletion(
    preset: AiProviderPreset,
    endpoint: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    userPrompt: String
): Result<String> = runCatching {
    val targetUrl = endpoint.trim().ifBlank { preset.defaultEndpoint }
    val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 8_000
        readTimeout = 12_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            if (preset == AiProviderPreset.ANTHROPIC) {
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
    }

    val payload = if (preset == AiProviderPreset.ANTHROPIC) {
        JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put("system", systemPrompt)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                )
            )
        }
    } else {
        JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    .put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
            )
        }
    }

    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
        writer.write(payload.toString())
        writer.flush()
    }

    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val bodyText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    conn.disconnect()

    if (code !in 200..299) {
        throw IllegalStateException("HTTP $code: ${bodyText.take(140)}")
    }

    val respObj = JSONObject(bodyText)
    if (preset == AiProviderPreset.ANTHROPIC) {
        val contentArr = respObj.optJSONArray("content") ?: JSONArray()
        contentArr.optJSONObject(0)?.optString("text").orEmpty()
    } else {
        val choices = respObj.optJSONArray("choices") ?: JSONArray()
        choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }
}
