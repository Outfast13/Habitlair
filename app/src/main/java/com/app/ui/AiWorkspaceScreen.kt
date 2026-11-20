package com.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.ai.ProposedTrackerItem
import com.app.habits.TrackerItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private data class AiRunResult(
    val ok: Boolean,
    val statusMessage: String,
    val proposals: List<ProposedTrackerItem> = emptyList()
)

private suspend fun runAiExtraction(
    settings: AiSettingsState,
    promptText: String
): AiRunResult = withContext(Dispatchers.IO) {
    val endpoint = settings.endpointUrl.trim()
    val apiKey = settings.apiKey.trim()
    val primaryModel = settings.modelId.trim().ifEmpty { "gpt-4o-mini" }

    if (!endpoint.startsWith("https://", ignoreCase = true) && !endpoint.startsWith("http://", ignoreCase = true)) {
        return@withContext AiRunResult(false, "Invalid endpoint URL. Configure a full http(s) URL in Settings.")
    }
    if (apiKey.isBlank()) {
        return@withContext AiRunResult(false, "Missing API key. Enter your API key in Settings.")
    }

    val modelsToTry = buildList {
        add(primaryModel)
        if (settings.enableFailover && settings.fallbackModelId.isNotBlank() && settings.fallbackModelId.trim() != primaryModel) {
            add(settings.fallbackModelId.trim())
        }
    }

    var lastError = "Request failed."
    for (model in modelsToTry) {
        val attempt = sendChatCompletionRequest(endpoint, apiKey, model, promptText)
        if (attempt.ok) {
            return@withContext attempt.copy(statusMessage = "Connected via '$model'. ${attempt.statusMessage}")
        }
        lastError = "Model '$model': ${attempt.statusMessage}"
    }
    AiRunResult(ok = false, statusMessage = lastError)
}

private fun sendChatCompletionRequest(
    endpoint: String,
    apiKey: String,
    model: String,
    promptText: String
): AiRunResult {
    var conn: HttpURLConnection? = null
    return try {
        val url = URL(endpoint)
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "Extract habits, tasks, or reminders. Return JSON array with keys: title, type (HABIT, TASK, or REMINDER), category, reason.")
                        }
                    )
                    put(JSONObject().apply { put("role", "user"); put("content", promptText) })
                }
            )
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val rawBody = stream?.let { ins -> BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { it.readText() } }.orEmpty()

        if (code !in 200..299) {
            return AiRunResult(ok = false, statusMessage = "HTTP $code — ${rawBody.take(180).replace("\n", " ")}")
        }

        val drafts = parseProposals(rawBody)
        AiRunResult(ok = true, statusMessage = "Extracted ${drafts.size} draft(s).", proposals = drafts)
    } catch (ex: Exception) {
        AiRunResult(ok = false, statusMessage = ex.message ?: "Network error")
    } finally {
        conn?.disconnect()
    }
}

private fun parseProposals(rawJson: String): List<ProposedTrackerItem> {
    val contentText = runCatching {
        val root = JSONObject(rawJson)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
        } else ""
    }.getOrDefault("")

    val cleaned = contentText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = cleaned.indexOf('[')
    val end = cleaned.lastIndexOf(']')
    if (start >= 0 && end > start) {
        val arr = runCatching { JSONArray(cleaned.substring(start, end + 1)) }.getOrNull()
        if (arr != null) {
            val out = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim()
                    if (title.isEmpty()) continue
                    val tRaw = obj.optString("type", "TASK").uppercase()
                    val t = when {
                        tRaw.contains("HABIT") -> TrackerItemType.HABIT
                        tRaw.contains("REMINDER") -> TrackerItemType.REMINDER
                        else -> TrackerItemType.TASK
                    }
                    add(
                        ProposedTrackerItem(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            type = t,
                            category = obj.optString("category", "AI Draft").trim().ifEmpty { "AI Draft" },
                            reason = obj.optString("reason", "Extracted by AI").trim()
                        )
                    )
                }
            }
            if (out.isNotEmpty()) return out
        }
    }

    return listOf(
        ProposedTrackerItem(
            id = UUID.randomUUID().toString(),
            title = contentText.take(60).ifBlank { "Review AI output" },
            type = TrackerItemType.TASK,
            category = "AI Note",
            reason = "Review response before saving."
        )
    )
}

@Composable
fun AiWorkspaceScreen(
    aiState: AiSettingsState,
    onApproveProposedItem: (ProposedTrackerItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary
    val scope = rememberCoroutineScope()

    var notesInput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready. Configure key in Settings and run test.") }
    var isError by remember { mutableStateOf(false) }
    var proposals by remember { mutableStateOf<List<ProposedTrackerItem>>(emptyList()) }

    fun runAction(prompt: String) {
        if (isRunning) return
        val p = prompt.trim().ifEmpty { "Return a JSON array with 1 HABIT and 1 TASK." }
        isRunning = true
        isError = false
        statusText = "Calling AI endpoint..."
        scope.launch {
            val res = runAiExtraction(aiState, p)
            isRunning = false
            isError = !res.ok
            statusText = res.statusMessage
            if (res.ok) proposals = res.proposals + proposals
        }
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
            sectionTitle = "AI STUDIO",
            sectionSubtitle = "${aiState.providerName} • ${aiState.modelId}",
            badgeText = if (aiState.apiKey.isBlank()) "No Key" else "Ready",
            onOpenDrawer = onOpenDrawer
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .pressScale { onOpenSettings() }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("AI Settings", color = primary, fontWeight = FontWeight.SemiBold)
            }
        }

        Surface(
            color = LairPalette.SurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (isError) LairPalette.DangerRed else LairPalette.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = statusText,
                color = if (isError) LairPalette.DangerRed else LairPalette.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
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
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Paste routines, tasks, or reminders...", color = LairPalette.TextSecondary) },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LairPalette.TextPrimary,
                    unfocusedTextColor = LairPalette.TextPrimary,
                    focusedBorderColor = primary,
                    unfocusedBorderColor = LairPalette.BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(enabled = !isRunning) {
                            runAction("Return a JSON array with 1 HABIT and 1 TASK to verify connection.")
                        }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isRunning) "Testing..." else "Test AI", color = secondary, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(enabled = !isRunning) { runAction(notesInput) }
                        .background(primary, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isRunning) "Working..." else "Extract", color = onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        proposals.forEach { draft ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("${draft.type.name} • ${draft.category}", color = primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(draft.title, color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                if (draft.reason.isNotBlank()) {
                    Text(draft.reason, color = LairPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale {
                                onApproveProposedItem(draft)
                                proposals = proposals.filterNot { it.id == draft.id }
                            }
                            .background(primary, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Approve", color = onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale { proposals = proposals.filterNot { it.id == draft.id } }
                            .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Dismiss", color = LairPalette.DangerRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
