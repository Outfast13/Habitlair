package com.app.backup

import android.content.Context
import android.net.Uri
import com.app.ai.AiSettingsStorage
import com.app.habits.HabitStorage
import com.app.journal.JournalStorage
import com.app.pomodoro.PomodoroStorage
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_backups_v4", Context.MODE_PRIVATE)

    fun getBackupDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSnapshotFiles(): List<File> {
        return getBackupDir()
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun buildBackupJson(): String {
        val habitsRaw = context.getSharedPreferences("habitlair_habits_v4", Context.MODE_PRIVATE)
            .getString("state_v4_json", "{}") ?: "{}"
        val journalRaw = context.getSharedPreferences("habitlair_journal_v4", Context.MODE_PRIVATE)
            .getString("entries_json", "[]") ?: "[]"
        val pomodoroRaw = context.getSharedPreferences("habitlair_pomodoro_v4", Context.MODE_PRIVATE)
            .getString("state_json", "{}") ?: "{}"
        val settingsRaw = context.getSharedPreferences("habitlair_settings_v4", Context.MODE_PRIVATE)
            .getString("settings_json", "{}") ?: "{}"

        return JSONObject().apply {
            put("app", "HabitLair")
            put("version", 4)
            put("exportedAtMs", System.currentTimeMillis())
            put("habitsStateRaw", habitsRaw)
            put("journalEntriesRaw", journalRaw)
            put("pomodoroStateRaw", pomodoroRaw)
            put("settingsRaw", settingsRaw)
        }.toString(2)
    }

    fun createLocalSnapshot(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(getBackupDir(), "habitlair_backup_$stamp.json")
        file.writeText(buildBackupJson(), Charsets.UTF_8)
        prefs.edit().putLong("last_backup_ms", System.currentTimeMillis()).apply()
        return file
    }

    fun exportToUri(uri: Uri): Result<String> = runCatching {
        val json = buildBackupJson()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("Unable to open destination file.")
        prefs.edit().putLong("last_backup_ms", System.currentTimeMillis()).apply()
        "Exported backup (${json.length} chars)."
    }

    fun restoreFromJsonString(jsonText: String): Result<String> = runCatching {
        val root = JSONObject(jsonText)
        if (root.optString("app") != "HabitLair" && !root.has("habitsStateRaw")) {
            throw IllegalArgumentException("Unrecognized HabitLair backup format.")
        }

        val habitsRaw = root.optString("habitsStateRaw", "")
        if (habitsRaw.isNotBlank()) {
            context.getSharedPreferences("habitlair_habits_v4", Context.MODE_PRIVATE)
                .edit().putString("state_v4_json", habitsRaw).apply()
        }

        val journalRaw = root.optString("journalEntriesRaw", "")
        if (journalRaw.isNotBlank()) {
            context.getSharedPreferences("habitlair_journal_v4", Context.MODE_PRIVATE)
                .edit().putString("entries_json", journalRaw).apply()
        }

        val pomodoroRaw = root.optString("pomodoroStateRaw", "")
        if (pomodoroRaw.isNotBlank()) {
            context.getSharedPreferences("habitlair_pomodoro_v4", Context.MODE_PRIVATE)
                .edit().putString("state_json", pomodoroRaw).apply()
        }

        val settingsRaw = root.optString("settingsRaw", "")
        if (settingsRaw.isNotBlank()) {
            context.getSharedPreferences("habitlair_settings_v4", Context.MODE_PRIVATE)
                .edit().putString("settings_json", settingsRaw).apply()
        }

        "Backup restored. Reloading workspace..."
    }

    fun restoreFromUri(uri: Uri): Result<String> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("Cannot read backup file.")
        restoreFromJsonString(text).getOrThrow()
    }

    fun lastBackupMillis(): Long = prefs.getLong("last_backup_ms", 0L)

    fun runAutoBackupIfEnabled() {
        val settings = AiSettingsStorage(context).load()
        if (!settings.autoBackupEnabled) return
        val last = lastBackupMillis()
        val sixHoursMs = 6L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - last > sixHoursMs) {
            createLocalSnapshot()
        }
    }

    fun ensureStoragesInitialized() {
        HabitStorage(context).load()
        JournalStorage(context).load()
        PomodoroStorage(context).load()
    }
}
