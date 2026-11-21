package com.habitlair.app.ai

import com.habitlair.app.data.IconKeys
import kotlinx.serialization.json.*

data class HabitPlan(val name: String, val timeMinutes: Int?, val daysMask: Int, val icon: String, val notes: String)
data class SubtaskPlan(val title: String, val estimateMinutes: Int?)
data class TaskPlan(
    val title: String, val priority: Int, val dueInDays: Int?, val estimateMinutes: Int?,
    val subtasks: List<SubtaskPlan> = emptyList()
)
data class PomodoroPlan(val label: String, val focusMinutes: Int, val breakMinutes: Int, val cycles: Int)
data class RoutinePlan(val habits: List<HabitPlan>, val tasks: List<TaskPlan>, val pomodoro: List<PomodoroPlan>)
data class Suggestion(val title: String, val detail: String)
data class CoachReport(
    val summary: String, val insights: List<String>, val suggestions: List<Suggestion>, val newHabits: List<HabitPlan>
)

/** What the preview-before-import sheet shows, and what gets written into Room. */
data class ImportBundle(
    val habits: List<HabitPlan> = emptyList(),
    val tasks: List<TaskPlan> = emptyList(),
    val presets: List<PomodoroPlan> = emptyList(),
    /** When set, `tasks` are inserted as sub-tasks of this task. */
    val parentTaskId: Long? = null
) {
    val isEmpty get() = habits.isEmpty() && tasks.isEmpty() && presets.isEmpty()
}

class AiParseException(message: String) : Exception(message)

object AiParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun extract(raw: String): JsonElement {
        var t = raw.trim()
        Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(t)?.let { t = it.groupValues[1].trim() }
        val s = t.indexOfFirst { it == '{' || it == '[' }
        if (s < 0) throw AiParseException("No JSON found in the AI response.")
        val close = if (t[s] == '{') '}' else ']'
        val e = t.lastIndexOf(close)
        if (e <= s) throw AiParseException("The JSON in the AI response is cut off.")
        val body = t.substring(s, e + 1).replace(Regex(",\\s*([}\\]])"), "$1")
        return try {
            json.parseToJsonElement(body)
        } catch (ex: Exception) {
            throw AiParseException("Malformed JSON: ${ex.message?.take(120)}")
        }
    }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let {
        it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun JsonObject.arr(vararg keys: String): List<JsonElement> =
        keys.firstNotNullOfOrNull { (this[it] as? JsonArray)?.toList() } ?: emptyList()

    private fun asObject(root: JsonElement, arrayKey: String): JsonObject = when (root) {
        is JsonObject -> root
        is JsonArray -> buildJsonObject { put(arrayKey, root) }
        else -> throw AiParseException("Unexpected JSON shape.")
    }

    fun parseTime(s: String?): Int? {
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*([aApP][mM])?$").find(s?.trim() ?: return null) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3].lowercase()
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    private val dayKeys = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun parseDays(el: JsonElement?): Int {
        fun word(w: String): Int = when (val k = w.trim().lowercase()) {
            "daily", "everyday", "every day", "all" -> 127
            "weekdays", "weekday" -> 31
            "weekends", "weekend" -> 96
            else -> dayKeys.indexOf(k.take(3)).takeIf { it >= 0 }?.let { 1 shl it }
                ?: k.toIntOrNull()?.takeIf { it in 1..7 }?.let { 1 shl (it - 1) } ?: 0
        }
        val mask = when (el) {
            is JsonArray -> el.fold(0) { acc, e -> acc or word((e as? JsonPrimitive)?.contentOrNull ?: "") }
            is JsonPrimitive -> el.contentOrNull?.split(',', ' ', '/')?.filter { it.isNotBlank() }?.fold(0) { a, w -> a or word(w) } ?: 0
            else -> 0
        }
        return if (mask == 0) 127 else mask
    }

    private fun priority(o: JsonObject): Int = when (o.str("priority")?.lowercase()) {
        "high", "urgent", "2", "3" -> 2
        "low", "0" -> 0
        else -> 1
    }

    private fun habit(e: JsonElement): HabitPlan? {
        val o = e as? JsonObject ?: return null
        val name = (o.str("name") ?: o.str("title"))?.take(60) ?: return null
        val icon = o.str("icon")?.lowercase()?.takeIf { it in IconKeys.all } ?: "check"
        return HabitPlan(name, parseTime(o.str("time")), parseDays(o["days"]), icon, o.str("notes")?.take(300) ?: "")
    }

    private fun subtask(e: JsonElement): SubtaskPlan? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { SubtaskPlan(it.take(120), null) }
        is JsonObject -> (e.str("title") ?: e.str("name") ?: e.str("step"))?.take(120)
            ?.let { SubtaskPlan(it, (e.int("estimateMinutes") ?: e.int("minutes"))?.coerceIn(1, 600)) }
        else -> null
    }

    private fun task(e: JsonElement): TaskPlan? {
        val o = e as? JsonObject ?: return null
        val title = (o.str("title") ?: o.str("name"))?.take(120) ?: return null
        return TaskPlan(
            title, priority(o), o.int("dueInDays")?.coerceIn(0, 365), o.int("estimateMinutes")?.coerceIn(1, 600),
            o.arr("subtasks", "steps").mapNotNull { subtask(it) }.take(12)
        )
    }

    fun routine(raw: String): RoutinePlan {
        val o = asObject(extract(raw), "habits")
        val habits = o.arr("habits", "routines").mapNotNull { habit(it) }.take(20)
        val tasks = o.arr("tasks").mapNotNull { task(it) }.take(20)
        val pomo = o.arr("pomodoro", "pomodoroBlocks").mapNotNull { e ->
            val p = e as? JsonObject ?: return@mapNotNull null
            PomodoroPlan(
                p.str("label")?.take(40) ?: "Focus block",
                (p.int("focusMinutes") ?: 25).coerceIn(5, 120), (p.int("breakMinutes") ?: 5).coerceIn(1, 60),
                (p.int("cycles") ?: 4).coerceIn(1, 12)
            )
        }.take(6)
        if (habits.isEmpty() && tasks.isEmpty() && pomo.isEmpty())
            throw AiParseException("The response contained no valid habits, tasks or Pomodoro blocks.")
        return RoutinePlan(habits, tasks, pomo)
    }

    fun breakdown(raw: String): List<SubtaskPlan> {
        val o = asObject(extract(raw), "subtasks")
        val list = o.arr("subtasks", "steps", "tasks").mapNotNull { subtask(it) }.take(15)
        if (list.isEmpty()) throw AiParseException("The response contained no usable sub-tasks.")
        return list
    }

    fun coach(raw: String): CoachReport {
        val o = asObject(extract(raw), "insights")
        val summary = o.str("summary") ?: ""
        val insights = o.arr("insights").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }.take(8)
        val suggestions = o.arr("suggestions").mapNotNull { e ->
            when (e) {
                is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { Suggestion(it.take(80), "") }
                is JsonObject -> (e.str("title") ?: e.str("name"))?.let { Suggestion(it.take(80), e.str("detail") ?: e.str("description") ?: "") }
                else -> null
            }
        }.take(8)
        val habits = o.arr("newHabits", "habits").mapNotNull { habit(it) }.take(6)
        if (summary.isBlank() && insights.isEmpty() && suggestions.isEmpty() && habits.isEmpty())
            throw AiParseException("The coach response was empty.")
        return CoachReport(summary, insights, suggestions, habits)
    }
}
