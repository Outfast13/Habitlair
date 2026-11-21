package com.habitlair.app.ai

import com.habitlair.app.data.*

data class AiOutcome<T>(val value: T, val source: String, val note: String?, val offline: Boolean)

class AiService(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val secure: SecureKeyStore,
    private val client: AiClient
) {
    fun configFrom(s: AppSettings): AiConfig =
        AiConfig(s.provider, s.baseUrl, s.modelFor(s.provider), secure.get(s.provider), s.temperature)

    /** Null when AI can't be used (no key). */
    private suspend fun activeConfig(): Pair<AppSettings, AiConfig?> {
        val s = settings.current()
        val c = configFrom(s)
        return s to if (c.apiKey.isBlank() && c.provider != Provider.CUSTOM) null else c
    }

    suspend fun status(): String {
        val (s, c) = activeConfig()
        return if (c == null) "No API key for ${s.provider.label}. The offline generator will be used."
        else "AI ready: ${s.provider.label} · ${c.model}"
    }

    suspend fun test(cfg: AiConfig): AiResult =
        client.chat(cfg, "You are a connectivity probe.", "Reply with the single word: OK")

    private fun system(s: AppSettings, schema: String) =
        s.systemPrompt.ifBlank { DEFAULT_PROMPT } +
            "\n\nYou are embedded in the HabitLair app. Respond with ONLY one valid JSON object, no prose and no markdown fences. Schema:\n" + schema

    private suspend fun <T> run(
        offline: () -> T, schema: String, user: String, parse: (String) -> T
    ): AiOutcome<T> {
        val (s, cfg) = activeConfig()
        if (cfg == null) return AiOutcome(offline(), "Offline generator", "No API key set, so the on-device generator was used.", true)
        return try {
            val r = client.chat(cfg, system(s, schema), user, json = true)
            AiOutcome(parse(r.text), "${s.provider.label} · ${r.model}", null, false)
        } catch (e: AiException) {
            AiOutcome(offline(), "Offline generator", "AI request failed (${e.describe()}). Used the on-device generator instead.", true)
        } catch (e: AiParseException) {
            AiOutcome(offline(), "Offline generator", "The AI reply could not be parsed (${e.message}). Used the on-device generator instead.", true)
        }
    }

    private suspend fun contextBlock(): String {
        val today = Dates.today()
        val habits = db.habits().all().filter { !it.archived }
        val done = db.habits().allCompletions().groupBy { it.habitId }.mapValues { m -> m.value.map { it.epochDay }.toSet() }
        val tasks = db.tasks().all().filter { !it.done && it.parentId == null }.take(15)
        val journal = db.journal().all().take(5)
        return buildString {
            appendLine("USER CONTEXT (today is epoch day $today):")
            appendLine("Habits:")
            habits.forEach {
                val d = done[it.id] ?: emptySet()
                val st = Streaks.compute(it, d, today)
                appendLine("- ${it.name} (${Dates.daysLabel(it.daysMask)}${it.timeMinutes?.let { t -> " at " + Dates.fmtTime(t) } ?: ""}) streak=${st.current} best=${st.best} rate30=${st.rate30}% doneToday=${today in d}")
            }
            appendLine("Open tasks:")
            tasks.forEach { appendLine("- ${it.title} [priority ${it.priority}]${it.dueEpochDay?.let { d -> " due " + Dates.fmtDate(d) } ?: ""}") }
            appendLine("Recent journal:")
            journal.forEach { appendLine("- ${Dates.fmtDate(it.epochDay)} mood=${it.mood}/5: ${(it.title + " " + it.body).trim().take(160).replace('\n', ' ')}") }
        }
    }

    suspend fun generateRoutine(goal: String): AiOutcome<RoutinePlan> {
        val s = settings.current()
        val ctx = if (s.shareContext) "\n\n" + contextBlock() + "\nAvoid duplicating existing habits." else ""
        return run(
            offline = { OfflineGenerator.routine(goal) },
            schema = ROUTINE_SCHEMA,
            user = "Build a realistic daily/weekly routine for this goal or schedule:\n$goal$ctx",
            parse = AiParser::routine
        )
    }

    suspend fun breakDown(title: String, notes: String): AiOutcome<List<SubtaskPlan>> {
        val s = settings.current()
        val ctx = if (s.shareContext) "\n\n" + contextBlock() else ""
        return run(
            offline = { OfflineGenerator.breakDown(title, notes) },
            schema = BREAKDOWN_SCHEMA,
            user = "Break this task into 3-10 ordered sub-tasks with time estimates in minutes.\nTask: $title\nNotes: $notes$ctx",
            parse = AiParser::breakdown
        )
    }

    private suspend fun snapshot(): OfflineGenerator.Snapshot {
        val today = Dates.today()
        val done = db.habits().allCompletions().groupBy { it.habitId }.mapValues { m -> m.value.map { it.epochDay }.toSet() }
        val stats = db.habits().all().filter { !it.archived }.map { h ->
            val d = done[h.id] ?: emptySet()
            val st = Streaks.compute(h, d, today)
            var sched = 0; var hit = 0
            for (x in today - 13..today) if (h.isScheduled(x)) { sched++; if (x in d) hit++ }
            OfflineGenerator.HabitStat(h.name, st.current, st.best, if (sched == 0) 100 else hit * 100 / sched)
        }
        val moods = db.journal().all().take(10).map { it.mood }
        val tasks = db.tasks().all().filter { !it.done && it.parentId == null }
        return OfflineGenerator.Snapshot(
            stats, moods.takeIf { it.isNotEmpty() }?.average(), moods.size, tasks.size,
            tasks.count { (it.dueEpochDay ?: Long.MAX_VALUE) < today }
        )
    }

    suspend fun coach(): AiOutcome<CoachReport> {
        val s = settings.current()
        if (!s.shareContext) {
            val r = OfflineGenerator.coach(snapshot())
            return AiOutcome(r, "On-device coach", "Context sharing is off, so your data was analysed locally and not sent anywhere.", true)
        }
        val snap = snapshot()
        return run(
            offline = { OfflineGenerator.coach(snap) },
            schema = COACH_SCHEMA,
            user = "Review my recent journal entries and habit streaks and suggest adjustments.\n\n" + contextBlock(),
            parse = AiParser::coach
        )
    }

    private companion object {
        const val HABIT_SHAPE = """{"name":"string","time":"HH:mm (24h)","days":["MON","TUE","WED","THU","FRI","SAT","SUN"],"icon":"one of check,health,study,rest,chat,fitness,book,water,sleep,code,run,music,heart,food,work,star","notes":"string"}"""
        const val ROUTINE_SCHEMA = """{"habits":[$HABIT_SHAPE],"tasks":[{"title":"string","priority":"LOW|MEDIUM|HIGH","dueInDays":0,"estimateMinutes":30,"subtasks":[{"title":"string","estimateMinutes":15}]}],"pomodoro":[{"label":"string","focusMinutes":25,"breakMinutes":5,"cycles":4}]}
Limits: at most 8 habits, 6 tasks, 3 pomodoro blocks."""
        const val BREAKDOWN_SCHEMA = """{"subtasks":[{"title":"string","estimateMinutes":15}]}"""
        const val COACH_SCHEMA = """{"summary":"string","insights":["string"],"suggestions":[{"title":"string","detail":"string"}],"newHabits":[$HABIT_SHAPE]}"""
    }
}
