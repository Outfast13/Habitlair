package com.habitlair.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitlair.app.AppContainer
import com.habitlair.app.ai.*
import com.habitlair.app.data.*
import com.habitlair.app.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val lazily = SharingStarted.WhileSubscribed(5_000)

data class HabitUi(
    val habit: Habit, val current: Int, val best: Int, val total: Int, val rate30: Int,
    val days: Set<Long>, val category: Category?
)

class HabitViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val categories = repo.categories.stateIn(viewModelScope, lazily, emptyList())

    val habits: StateFlow<List<HabitUi>> = combine(repo.habits, repo.completions, repo.categories) { hs, cs, cats ->
        val today = Dates.today()
        val byHabit = cs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.epochDay }.toSet() }
        hs.map { h ->
            val d = byHabit[h.id] ?: emptySet()
            val s = Streaks.compute(h, d, today)
            HabitUi(h, s.current, s.best, s.total, s.rate30, d, cats.firstOrNull { it.id == h.categoryId })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, lazily, emptyList())

    fun toggle(id: Long, day: Long) { viewModelScope.launch { repo.toggleHabit(id, day) } }
    fun save(h: Habit, changes: AttachmentChanges) { viewModelScope.launch { repo.saveHabit(h, changes) } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteHabit(id) } }
    fun saveCategory(cat: Category) { viewModelScope.launch { repo.saveCategory(cat) } }
    fun deleteCategory(id: Long) { viewModelScope.launch { repo.deleteCategory(id) } }
    suspend fun attachments(id: Long) = repo.attachmentsFor(Owner.HABIT, id)
}

data class TaskUi(val task: TaskItem, val subs: List<TaskItem>, val focusMin: Int)

class TasksViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val tasks: StateFlow<List<TaskUi>> = combine(repo.tasks, repo.sessions) { ts, ss ->
        val subs = ts.filter { it.parentId != null }.groupBy { it.parentId }
        val focus = ss.filter { it.kind == "FOCUS" && it.taskId != null }.groupBy { it.taskId }
            .mapValues { e -> e.value.sumOf { it.minutes } }
        ts.filter { it.parentId == null }.map { TaskUi(it, subs[it.id].orEmpty().sortedBy { s -> s.id }, focus[it.id] ?: 0) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, lazily, emptyList())

    fun setDone(id: Long, done: Boolean) { viewModelScope.launch { repo.setTaskDone(id, done) } }
    fun save(t: TaskItem, subs: List<TaskItem>, changes: AttachmentChanges) { viewModelScope.launch { repo.saveTask(t, subs, changes) } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteTask(id) } }
    suspend fun attachments(id: Long) = repo.attachmentsFor(Owner.TASK, id)
}

class JournalViewModel(private val c: AppContainer) : ViewModel() {
    val entries = c.repo.journal.stateIn(viewModelScope, lazily, emptyList())
    fun save(e: JournalEntry, changes: AttachmentChanges) { viewModelScope.launch { c.repo.saveJournal(e, changes) } }
    fun delete(id: Long) { viewModelScope.launch { c.repo.deleteJournal(id) } }
    suspend fun attachments(id: Long) = c.repo.attachmentsFor(Owner.JOURNAL, id)
}

class TimerViewModel(private val c: AppContainer) : ViewModel() {
    val timer = c.pomodoro.state
    val sessions = c.repo.sessions.stateIn(viewModelScope, lazily, emptyList())
    val presets = c.repo.presets.stateIn(viewModelScope, lazily, emptyList())
    val habits = c.repo.habits.stateIn(viewModelScope, lazily, emptyList())
    val tasks = c.repo.tasks.map { l -> l.filter { !it.done && it.parentId == null } }.stateIn(viewModelScope, lazily, emptyList())
    val settings = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun start() = c.pomodoro.start()
    fun pause() = c.pomodoro.pause()
    fun reset() = c.pomodoro.reset()
    fun skip() = c.pomodoro.skip()
    fun link(habitId: Long?, taskId: Long?) = c.pomodoro.setLink(habitId, taskId)
    fun updateSettings(f: (AppSettings) -> AppSettings) { viewModelScope.launch { c.settings.update(f) } }
    fun applyPreset(p: PomodoroPreset) = updateSettings { it.copy(focusMin = p.focusMin, breakMin = p.breakMin, cyclesBeforeLong = p.cycles) }
    fun deletePreset(id: Long) { viewModelScope.launch { c.repo.deletePreset(id) } }
    fun clearLog() { viewModelScope.launch { c.repo.clearSessions() } }
}

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Success(val ms: Long, val model: String) : TestState
    data class Failure(val message: String) : TestState
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.flow.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val test = MutableStateFlow<TestState>(TestState.Idle)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val pendingBackup = MutableStateFlow<BackupFile?>(null)
    val backupError = MutableStateFlow<String?>(null)

    fun update(reschedule: Boolean = false, f: (AppSettings) -> AppSettings) {
        viewModelScope.launch { c.settings.update(f); if (reschedule) c.scheduler.rescheduleAll() }
    }

    fun testConnection(cfg: AiConfig) {
        test.value = TestState.Running
        viewModelScope.launch {
            test.value = try {
                val r = c.ai.test(cfg)
                TestState.Success(r.ms, r.model)
            } catch (e: AiException) {
                TestState.Failure(e.describe())
            } catch (e: Exception) {
                TestState.Failure("Unexpected error: ${e.message}")
            }
        }
    }

    fun saveAi(provider: Provider, models: Map<Provider, String>, base: String, keys: Map<Provider, String>, prompt: String, temp: Float, share: Boolean) {
        viewModelScope.launch {
            keys.forEach { (p, k) -> c.secure.set(p, k) }
            c.settings.update {
                it.copy(provider = provider, models = Provider.entries.associateWith { p -> models[p]?.trim().orEmpty().ifBlank { p.defaultModel } },
                    customBaseUrl = base.trim(), systemPrompt = prompt.ifBlank { DEFAULT_PROMPT }, temperature = temp, shareContext = share)
            }
            messages.emit("AI settings saved")
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val ok = Notifier.showTest(c.appContext, c.settings.current())
            messages.emit(if (ok) "Test notification sent" else "Notification permission is not granted")
        }
    }

    fun scheduleTestAlarm() {
        c.scheduler.setAt("test", 0, System.currentTimeMillis() + 10_000)
        viewModelScope.launch { messages.emit(if (c.scheduler.canExact()) "Test alarm will fire in 10 seconds" else "Alarm set (inexact: allow exact alarms for precise timing)") }
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val json = c.repo.exportJson()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("Couldn't open the destination file")
                }
                messages.emit("Backup exported")
            } catch (e: Exception) { messages.emit("Export failed: ${e.message}") }
        }
    }

    fun readBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Couldn't read the file") }
                pendingBackup.value = c.repo.parseBackup(text)
            } catch (e: Exception) { backupError.value = e.message ?: "Couldn't read that backup." }
        }
    }

    fun confirmRestore() {
        val b = pendingBackup.value ?: return
        pendingBackup.value = null
        viewModelScope.launch {
            try { c.repo.restore(b); messages.emit("Backup restored") } catch (e: Exception) { messages.emit("Restore failed: ${e.message}") }
        }
    }
}

data class GenState<T>(val loading: Boolean = false, val result: AiOutcome<T>? = null)
data class Preview(val bundle: ImportBundle, val source: String, val note: String?)

class AiViewModel(private val c: AppContainer) : ViewModel() {
    val status = MutableStateFlow("")
    val routine = MutableStateFlow(GenState<RoutinePlan>())
    val breakdown = MutableStateFlow(GenState<List<SubtaskPlan>>())
    val coach = MutableStateFlow(GenState<CoachReport>())
    val preview = MutableStateFlow<Preview?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val openTasks = c.repo.tasks.map { l -> l.filter { !it.done && it.parentId == null } }.stateIn(viewModelScope, lazily, emptyList())

    init { refreshStatus() }
    fun refreshStatus() { viewModelScope.launch { status.value = c.ai.status() } }

    fun generateRoutine(goal: String) {
        routine.value = GenState(loading = true)
        viewModelScope.launch {
            val r = c.ai.generateRoutine(goal)
            routine.value = GenState(false, r)
            preview.value = Preview(ImportBundle(r.value.habits, r.value.tasks, r.value.pomodoro), r.source, r.note)
        }
    }

    fun breakDown(title: String, notes: String, parent: TaskItem?) {
        breakdown.value = GenState(loading = true)
        viewModelScope.launch {
            val r = c.ai.breakDown(title, notes)
            breakdown.value = GenState(false, r)
            val subs = r.value.map { TaskPlan(it.title, 1, null, it.estimateMinutes) }
            val bundle = if (parent != null) ImportBundle(tasks = subs, parentTaskId = parent.id)
            else ImportBundle(tasks = listOf(TaskPlan(title, 1, null, null, r.value)))
            preview.value = Preview(bundle, r.source, r.note)
        }
    }

    fun runCoach() {
        coach.value = GenState(loading = true)
        viewModelScope.launch { coach.value = GenState(false, c.ai.coach()) }
    }

    fun previewCoachHabits(r: AiOutcome<CoachReport>) {
        preview.value = Preview(ImportBundle(habits = r.value.newHabits), r.source, r.note)
    }

    fun dismissPreview() { preview.value = null }

    fun import(b: ImportBundle) {
        preview.value = null
        viewModelScope.launch {
            val r = c.repo.importBundle(b)
            val parts = buildList {
                if (r.habits > 0) add("${r.habits} habit(s)")
                if (r.tasks > 0) add("${r.tasks} task(s)")
                if (r.subtasks > 0) add("${r.subtasks} sub-task(s)")
                if (r.presets > 0) add("${r.presets} Pomodoro preset(s)")
            }
            messages.emit(if (parts.isEmpty()) "Nothing imported" else "Imported " + parts.joinToString(", "))
        }
    }
}
