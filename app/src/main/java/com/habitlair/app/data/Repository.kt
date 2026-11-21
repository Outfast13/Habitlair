package com.habitlair.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.habitlair.app.ai.ImportBundle
import com.habitlair.app.widget.HabitWidget
import com.habitlair.app.work.ReminderScheduler
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

const val BACKUP_VERSION = 1

@Serializable
data class BackupFile(
    val app: String = "HabitLair",
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val categories: List<Category> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val completions: List<HabitCompletion> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val sessions: List<PomodoroSession> = emptyList(),
    val presets: List<PomodoroPreset> = emptyList()
)

data class ImportResult(val habits: Int, val tasks: Int, val subtasks: Int, val presets: Int)

class MediaFiles(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "media").apply { mkdirs() }

    fun newFile(ext: String) = File(dir, "${UUID.randomUUID()}.$ext")
    fun uriFor(f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    fun attachmentFor(f: File, kind: String, name: String? = null) =
        Attachment(ownerType = "", ownerId = 0, kind = kind, path = f.absolutePath, name = name ?: f.name)

    fun copyIn(uri: Uri, kind: String): Attachment {
        val mime = ctx.contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: when (kind) { MediaKind.PHOTO -> "jpg"; MediaKind.VIDEO -> "mp4"; else -> "m4a" }
        val f = newFile(ext)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Can't open the selected file")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return attachmentFor(f, kind, displayName(uri))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}

class Repository(
    private val ctx: Context,
    val db: AppDatabase,
    val settings: SettingsStore,
    val scheduler: ReminderScheduler,
    val media: MediaFiles
) {
    val habits = db.habits().observe()
    val completions = db.habits().observeCompletions()
    val categories = db.categories().observe()
    val tasks = db.tasks().observe()
    val journal = db.journal().observe()
    val sessions = db.pomodoro().observeSessions()
    val presets = db.pomodoro().observePresets()

    private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true; coerceInputValues = true }

    private suspend fun refreshWidget() { runCatching { HabitWidget().updateAll(ctx) } }

    // ---------- attachments ----------
    suspend fun attachmentsFor(type: String, id: Long): List<Attachment> = db.attachments().forOwner(type, id)

    private suspend fun commit(type: String, id: Long, c: AttachmentChanges) {
        c.removed.forEach { db.attachments().delete(it.id); File(it.path).delete() }
        c.added.forEach { db.attachments().insert(it.copy(id = 0, ownerType = type, ownerId = id)) }
    }

    private suspend fun purge(type: String, id: Long) {
        db.attachments().forOwner(type, id).forEach { File(it.path).delete() }
        db.attachments().deleteForOwner(type, id)
    }

    // ---------- habits ----------
    suspend fun saveHabit(h: Habit, att: AttachmentChanges? = null): Long {
        val id = if (h.id == 0L) db.habits().upsert(h) else { db.habits().upsert(h); h.id }
        att?.let { commit(Owner.HABIT, id, it) }
        scheduler.syncHabit(id)
        refreshWidget()
        return id
    }

    suspend fun deleteHabit(id: Long) {
        scheduler.cancel(Owner.HABIT, id)
        purge(Owner.HABIT, id)
        db.tasks().unlinkHabit(id)
        db.pomodoro().unlinkHabit(id)
        db.habits().delete(id)
        refreshWidget()
    }

    suspend fun setHabitDone(id: Long, day: Long, done: Boolean) {
        if (done) db.habits().addCompletion(HabitCompletion(habitId = id, epochDay = day)) else db.habits().removeCompletion(id, day)
        refreshWidget()
    }

    suspend fun toggleHabit(id: Long, day: Long): Boolean {
        val now = db.habits().isDone(id, day) == 0
        setHabitDone(id, day, now)
        return now
    }

    // ---------- tasks ----------
    suspend fun saveTask(t: TaskItem, subs: List<TaskItem>? = null, att: AttachmentChanges? = null): Long {
        val id = if (t.id == 0L) db.tasks().upsert(t) else { db.tasks().upsert(t); t.id }
        if (subs != null) {
            val keep = subs.map { it.id }.filter { it != 0L }.toSet()
            db.tasks().children(id).filter { it.id !in keep }.forEach { db.tasks().delete(it.id) }
            subs.forEach { db.tasks().upsert(it.copy(parentId = id)) }
        }
        att?.let { commit(Owner.TASK, id, it) }
        scheduler.syncTask(id)
        return id
    }

    suspend fun deleteTask(id: Long) {
        db.tasks().children(id).forEach { scheduler.cancel(Owner.TASK, it.id) }
        db.tasks().deleteChildren(id)
        scheduler.cancel(Owner.TASK, id)
        purge(Owner.TASK, id)
        db.pomodoro().unlinkTask(id)
        db.tasks().delete(id)
    }

    suspend fun setTaskDone(id: Long, done: Boolean) {
        val t = db.tasks().get(id) ?: return
        db.tasks().upsert(t.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
        scheduler.syncTask(id)
    }

    // ---------- journal ----------
    suspend fun saveJournal(e: JournalEntry, att: AttachmentChanges? = null): Long {
        val entry = e.copy(updatedAt = System.currentTimeMillis())
        val id = if (e.id == 0L) db.journal().upsert(entry) else { db.journal().upsert(entry); e.id }
        att?.let { commit(Owner.JOURNAL, id, it) }
        return id
    }

    suspend fun deleteJournal(id: Long) { purge(Owner.JOURNAL, id); db.journal().delete(id) }

    // ---------- categories / pomodoro ----------
    suspend fun saveCategory(c: Category) { db.categories().upsert(c) }
    suspend fun deleteCategory(id: Long) { db.habits().clearCategory(id); db.categories().delete(id) }
    suspend fun logSession(s: PomodoroSession) { db.pomodoro().insertSession(s) }
    suspend fun clearSessions() = db.pomodoro().clearSessions()
    suspend fun deletePreset(id: Long) = db.pomodoro().deletePreset(id)

    // ---------- AI import ----------
    private val importColors = listOf(0xFF7CB342, 0xFF9C6FE4, 0xFFD63DAA, 0xFF2FA37F, 0xFF3F8CF2, 0xFFF2A33A, 0xFFE0415F, 0xFF26A6C9)

    suspend fun importBundle(b: ImportBundle): ImportResult {
        var subCount = 0
        db.withTransaction {
            b.habits.forEachIndexed { i, p ->
                db.habits().upsert(Habit(name = p.name, icon = p.icon, colorHex = importColors[i % importColors.size], timeMinutes = p.timeMinutes, daysMask = p.daysMask, notes = p.notes))
            }
            val today = Dates.today()
            b.tasks.forEach { p ->
                val pid = db.tasks().upsert(
                    TaskItem(title = p.title, priority = p.priority, estimateMinutes = p.estimateMinutes, parentId = b.parentTaskId,
                        dueEpochDay = if (b.parentTaskId == null) p.dueInDays?.let { today + it } else null)
                )
                p.subtasks.forEach { s ->
                    db.tasks().upsert(TaskItem(title = s.title, estimateMinutes = s.estimateMinutes, parentId = pid)); subCount++
                }
                if (b.parentTaskId != null) subCount++
            }
            b.presets.forEach { db.pomodoro().upsertPreset(PomodoroPreset(label = it.label, focusMin = it.focusMinutes, breakMin = it.breakMinutes, cycles = it.cycles)) }
        }
        scheduler.rescheduleAll()
        refreshWidget()
        return ImportResult(b.habits.size, if (b.parentTaskId == null) b.tasks.size else 0, subCount, b.presets.size)
    }

    // ---------- backup ----------
    suspend fun exportJson(): String = backupJson.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            settings = settings.current(),
            categories = db.categories().all(), habits = db.habits().all(), completions = db.habits().allCompletions(),
            tasks = db.tasks().all(), journal = db.journal().all(), sessions = db.pomodoro().allSessions(), presets = db.pomodoro().allPresets()
        )
    )

    /** Parses and validates; throws IllegalArgumentException with a readable message. */
    fun parseBackup(text: String): BackupFile {
        val b = try {
            backupJson.decodeFromString(BackupFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("This isn't a valid HabitLair backup (${e.message?.lineSequence()?.firstOrNull()?.take(140)}).")
        }
        require(b.app == "HabitLair") { "This file wasn't created by HabitLair." }
        require(b.version in 1..BACKUP_VERSION) { "Backup version ${b.version} isn't supported by this app version." }
        require(b.habits.map { it.id }.toSet().size == b.habits.size) { "Backup contains duplicate habit IDs." }
        require(b.tasks.map { it.id }.toSet().size == b.tasks.size) { "Backup contains duplicate task IDs." }
        val habitIds = b.habits.map { it.id }.toSet()
        return b.copy(completions = b.completions.filter { it.habitId in habitIds })
    }

    suspend fun restore(b: BackupFile) {
        db.habits().all().forEach { scheduler.cancel(Owner.HABIT, it.id) }
        db.tasks().all().forEach { scheduler.cancel(Owner.TASK, it.id) }
        db.attachments().all().forEach { File(it.path).delete() }
        db.withTransaction {
            db.attachments().clear()
            db.habits().clearCompletions(); db.habits().clear(); db.tasks().clear(); db.journal().clear()
            db.pomodoro().clearSessions(); db.pomodoro().clearPresets(); db.categories().clear()
            db.categories().insertAll(b.categories); db.habits().insertAll(b.habits); db.habits().insertCompletions(b.completions)
            db.tasks().insertAll(b.tasks); db.journal().insertAll(b.journal)
            db.pomodoro().insertSessions(b.sessions); db.pomodoro().insertPresets(b.presets)
        }
        settings.replace(b.settings)
        scheduler.rescheduleAll()
        refreshWidget()
    }
}
