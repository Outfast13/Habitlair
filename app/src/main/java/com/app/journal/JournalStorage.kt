package com.app.journal

import android.content.Context
import com.app.habits.MediaAttachment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JournalStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_journal_v4", Context.MODE_PRIVATE)

    fun getMediaDirectory(): File {
        val dir = File(context.filesDir, "journal_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun load(): JournalState {
        val raw = prefs.getString("entries_json", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            val entries = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val attArr = obj.optJSONArray("attachments") ?: JSONArray()
                    val atts = buildList {
                        for (j in 0 until attArr.length()) {
                            val aObj = attArr.getJSONObject(j)
                            add(
                                MediaAttachment(
                                    id = aObj.optString("id"),
                                    displayName = aObj.optString("displayName"),
                                    mimeType = aObj.optString("mimeType", "*/*"),
                                    localPath = aObj.optString("localPath"),
                                    sizeBytes = aObj.optLong("sizeBytes", 0L)
                                )
                            )
                        }
                    }
                    add(
                        JournalEntry(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            body = obj.optString("body"),
                            mood = obj.optString("mood", "Neutral"),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            attachments = atts
                        )
                    )
                }
            }
            JournalState(entries = entries)
        }.getOrDefault(JournalState())
    }

    fun save(state: JournalState) {
        val arr = JSONArray()
        state.entries.forEach { entry ->
            val attArr = JSONArray()
            entry.attachments.forEach { att ->
                attArr.put(
                    JSONObject().apply {
                        put("id", att.id)
                        put("displayName", att.displayName)
                        put("mimeType", att.mimeType)
                        put("localPath", att.localPath)
                        put("sizeBytes", att.sizeBytes)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("body", entry.body)
                    put("mood", entry.mood)
                    put("timestampMs", entry.timestampMs)
                    put("attachments", attArr)
                }
            )
        }
        prefs.edit().putString("entries_json", arr.toString()).apply()
    }
}
