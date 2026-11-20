package com.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun AiWorkspaceScreen(
    aiState: AiSettingsState,
    habitState: HabitState,
    screenTimeSnapshot: ScreenTimeSnapshot,
    onApproveProposedItem: (ProposedTrackerItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary

    var promptText by remember { mutableStateOf("") }
    var coachSummary by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var proposals by remember { mutableStateOf<List<ProposedTrackerItem>>(emptyList()) }

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

    fun buildLocalCoachFallback(customGoal: String): Pair<String, List<ProposedTrackerItem>> {
        val alerts = buildList {
            if (overScreenLimit) {
                add("Screen overuse alert: ${screenTimeSnapshot.todayMinutes}m used today vs ${aiState.dailyScreenLimitMinutes}m limit (top: ${screenTimeSnapshot.topAppName}). Put the phone away and knock out one high-value habit.")
            }
            if (missedOrPendingToday.isNotEmpty()) {
                add("Pending today (${missedOrPendingToday.size}): ${missedOrPendingToday.take(3).joinToString { it.title }}. Break large blocks into 15-minute starts.")
            }
            val zeroStreakHabits = habitState.items.filter { it.type == TrackerItemType.HABIT && it.streak == 0 }
            if (zeroStreakHabits.isNotEmpty()) {
                add("Habit friction detected on: ${zeroStreakHabits.take(2).joinToString { it.title }}. Pair it with an existing reminder or lower the minimum target.")
            }
        }
        val summary = if (alerts.isEmpty()) {
            "Routine looks steady. Give AI a goal or missed item below to build tailored habits, tasks, or reminders."
        } else {
            alerts.joinToString("
• ", prefix = "• ")
        }

        val generatedProposals = buildList {
            if (customGoal.isNotBlank()) {
                add(
                    ProposedTrackerItem(
                        id = UUID.randomUUID().toString(),
                        title = customGoal.trim().take(48),
                        type = TrackerItemType.TASK,
                        category = "AI Plan",
                        reason = "Created directly from your custom request.",
                        scheduledTime = "19:00",
                        evaluationMode = "YES_NO"
                    )
                )
            }
            if (overScreenLimit) {
                add(
                    ProposedTrackerItem(
                        id = UUID.randomUUID().toString(),
                        title = "Screen-Free Reset Block (20m)",
                        type = TrackerItemType.REMINDER,
                        category = "Break",
                        reason = "Triggered because screen usage hit ${screenTimeSnapshot.todayMinutes}m.",
                        scheduledTime = "21:00",
                        evaluationMode = "TIMER"
                    )
                )
            }
        }
        return summary to generatedProposals
    }

    fun runAiCoach(customGoal: String) {
        val keys = aiState.allActiveKeys()
        val preset = AiProviderPreset.fromProviderNameOrId(aiState.providerId.ifBlank { aiState.providerName })
        val needsKey = preset != AiProviderPreset.OLLAMA && preset != AiProviderPreset.LM_STUDIO

        if (needsKey && keys.isEmpty()) {
            val (fallbackMsg, fallbackProps) = buildLocalCoachFallback(customGoal)
            coachSummary = fallbackMsg
            proposals = fallbackProps
            errorMessage = "Add an API key in Settings for full live AI generation (showing local diagnostics)."
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val contextSnapshot = buildString {
                    appendLine("Goal/Prompt: ${customGoal.ifBlank { "Audit my current habits, missed items, bad habit friction, and screen time." }}")
                    appendLine("Screen Time Today: ${screenTimeSnapshot.todayMinutes}m (Limit: ${aiState.dailyScreenLimitMinutes}m, Top app: ${screenTimeSnapshot.topAppName})")
                    appendLine("Pending/Missed Today: ${missedOrPendingToday.joinToString { "${it.title} [${it.type.name}]" }.ifBlank { "None" }}")
                    append("All Items: ${habitState.items.joinToString { "${it.title} (${it.type.name}, streak=${it.streak}d, time=${it.schedule.scheduledTime})" }.ifBlank { "None" }}")
                }

                val systemPrompt = "You are HabitLair's strict, direct AI routine & habit coach. Call out bad habit friction, missed items, or excessive screen time plainly without fluff, and propose actionable habits, tasks, or reminders when helpful. Respond ONLY with valid JSON: {\"coach_note\":\"...\",\"proposals\":[{\"title\":\"...\",\"type\":\"HABIT|TASK|REMINDER\",\"category\":\"...\",\"scheduled_time\":\"HH:mm\",\"evaluation_mode\":\"YES_NO|NUMERIC|TIMER|CHECKLIST\",\"reason\":\"...\"}]}"

                val keyCandidates = if (needsKey) {
                    if (aiState.enableFailover) keys else listOf(keys.first())
                } else {
                    listOf("")
                }
                val modelCandidates = if (aiState.enableFailover && aiState.fallbackModelId.isNotBlank() && aiState.fallbackModelId != aiState.modelId) {
                    listOf(aiState.modelId, aiState.fallbackModelId)
                } else {
                    listOf(aiState.modelId)
                }

                var lastErr = "AI request failed"
                for (k in keyCandidates) {
                    for (m in modelCandidates) {
                        val call = executeAiChatCompletion(
                            preset = preset,
                            endpoint = aiState.endpointUrl,
                            apiKey = k,
                            model = m,
                            systemPrompt = systemPrompt,
                            userPrompt = contextSnapshot
                        )
                        if (call.isSuccess) {
                            return@withContext Result.success(call.getOrThrow())
                        } else {
                            lastErr = call.exceptionOrNull()?.message ?: lastErr
                        }
                    }
                }
                Result.failure(IllegalStateException(lastErr))
            }

            isLoading = false
            result.fold(
                onSuccess = { rawContent ->
                    val cleanedJson = extractJsonObject(rawContent)
                    val parsed = runCatching {
                        val obj = JSONObject(cleanedJson)
                        val note = obj.optString("coach_note", rawContent.trim())
                        val arr = obj.optJSONArray("proposals") ?: JSONArray()
                        val list = buildList {
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
                                        reason = p.optString("reason", "Suggested by AI Coach"),
                                        scheduledTime = p.optString("scheduled_time", "18:00").ifBlank { "18:00" },
                                        evaluationMode = p.optString("evaluation_mode", "YES_NO")
                                    )
                                )
                            }
                        }
                        note to list
                    }
                    parsed.fold(
                        onSuccess = { (note, list) ->
                            coachSummary = note
                            proposals = list
                        },
                        onFailure = {
                            coachSummary = rawContent.trim()
                        }
                    )
                },
                onFailure = { err ->
                    val (fallbackMsg, fallbackProps) = buildLocalCoachFallback(customGoal)
                    coachSummary = fallbackMsg
                    proposals = fallbackProps
                    errorMessage = err.message
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    RoundedCornerShape(14.dp)
                )
                .border(
                    1.dp,
                    if (overScreenLimit) LairPalette.DangerRed else LairPalette.BorderSubtle,
                    RoundedCornerShape(14.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (overScreenLimit) Icons.Outlined.WarningAmber else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (overScreenLimit) LairPalette.DangerRed else secondary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!screenTimeSnapshot.hasUsagePermission) {
                        "Screen Guard: Grant Usage Access in Settings to track screen overuse"
                    } else if (overScreenLimit) {
                        "Overuse Warning: ${screenTimeSnapshot.todayMinutes}m / ${aiState.dailyScreenLimitMinutes}m limit (${screenTimeSnapshot.topAppName})"
                    } else {
                        "Screen Time Today: ${screenTimeSnapshot.todayMinutes}m / ${aiState.dailyScreenLimitMinutes}m limit"
                    },
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${missedOrPendingToday.size} scheduled items still open today",
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Direct Routine & Habit Command", color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("What do you want to fix, build, or get called out on?") },
                placeholder = { Text("e.g., Fix my missed evening study, cut phone overuse, set a calisthenics reminder") },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle,
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { runAiCoach("Audit my bad habits, missed items, and screen time overuse right now.") },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Audit Now", color = LairPalette.TextPrimary)
                }
                Button(
                    onClick = { runAiCoach(promptText) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoading) "Running..." else "Run AI Coach")
                }
            }
        }

        errorMessage?.let { err ->
            Text(err, color = LairPalette.AccentAmber, style = MaterialTheme.typography.bodySmall)
        }

        coachSummary?.let { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                    .border(1.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("AI Assessment", color = primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(note, color = LairPalette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (proposals.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Proposed Habits, Tasks & Reminders", color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                proposals.forEach { draft ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
                            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(draft.title, color = LairPalette.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${draft.type.name} • ${format24hToAmPm(draft.scheduledTime)}", color = secondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(draft.reason, color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onApproveProposedItem(draft)
                                    proposals = proposals.filterNot { it.id == draft.id }
                                    Toast.makeText(context, "Added \"${draft.title}\"", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Approve", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { proposals = proposals.filterNot { it.id == draft.id } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Dismiss", color = LairPalette.TextSecondary)
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
        connectTimeout = 20_000
        readTimeout = 30_000
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
            put("max_tokens", 1024)
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
            put("temperature", 0.3)
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
        throw IllegalStateException("HTTP $code: ${bodyText.take(180)}")
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
