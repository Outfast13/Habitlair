package com.app.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_habits_v4", Context.MODE_PRIVATE)

    fun getMediaDirectory(): File {
        val dir = File(context.filesDir, "tracker_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun load(): HabitState {
        val raw = prefs.getString("state_v4_json", null)
        if (raw == null) return HabitState()
        return runCatching {
            val root = JSONObject(raw)
            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.getJSONObject(i)
                    val typeName = obj.optString("type", "HABIT")
                    val type = runCatching { TrackerItemType.valueOf(typeName) }.getOrDefault(TrackerItemType.HABIT)
                    val schedObj = obj.optJSONObject("schedule") ?: JSONObject()
                    val cadence = runCatching {
                        ScheduleCadence.valueOf(schedObj.optString("cadence", ScheduleCadence.DAILY.name))
                    }.getOrDefault(ScheduleCadence.DAILY)
                    val unit = runCatching {
                        CustomIntervalUnit.valueOf(schedObj.optString("customUnit", CustomIntervalUnit.DAYS.name))
                    }.getOrDefault(CustomIntervalUnit.DAYS)
                    val daysArr = schedObj.optJSONArray("specificDays") ?: JSONArray()
                    val days = buildList {
                        for (d in 0 until daysArr.length()) {
                            runCatching { DayOfWeek.valueOf(daysArr.getString(d)) }.getOrNull()?.let { add(it) }
                        }
                    }.ifEmpty { listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }

                    val schedule = ScheduleRule(
                        cadence = cadence,
                        specificDays = days,
                        customEvery = schedObj.optInt("customEvery", 1).coerceAtLeast(1),
                        customUnit = unit,
                        anchorEpochDay = schedObj.optLong("anchorEpochDay", LocalDate.now().toEpochDay()),
                        scheduledTime = schedObj.optString("scheduledTime", "08:00"),
                        alarmEnabled = schedObj.optBoolean("alarmEnabled", false)
                    )

                    val attArr = obj.optJSONArray("attachments") ?: JSONArray()
                    val attachments = parseMediaList(attArr)

                    val evalMode = runCatching {
                        EvaluationMode.valueOf(obj.optString("evaluationMode", EvaluationMode.YES_NO.name))
                    }.getOrDefault(EvaluationMode.YES_NO)

                    val chkArr = obj.optJSONArray("checklistItems") ?: JSONArray()
                    val checklist = buildList {
                        for (c in 0 until chkArr.length()) {
                            val cObj = chkArr.optJSONObject(c) ?: continue
                            add(
                                ChecklistSubItem(
                                    id = cObj.optString("id"),
                                    label = cObj.optString("label"),
                                    checked = cObj.optBoolean("checked", false)
                                )
                            )
                        }
                    }

                    add(
                        TrackerItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            type = type,
                            category = obj.optString("category", "General"),
                            notes = obj.optString("notes", ""),
                            schedule = schedule,
                            attachments = attachments,
                            streak = obj.optInt("streak", 0),
                            evaluationMode = evalMode,
                            targetValue = obj.optDouble("targetValue", 1.0),
                            targetUnit = obj.optString("targetUnit", "count"),
                            targetSeconds = obj.optInt("targetSeconds", 900).coerceAtLeast(10),
                            checklistItems = checklist
                        )
                    )
                }
            }

            val logsArr = root.optJSONArray("logs") ?: JSONArray()
            val logs = buildList {
                for (i in 0 until logsArr.length()) {
                    val lObj = logsArr.getJSONObject(i)
                    val tName = lObj.optString("itemType", "HABIT")
                    val tType = runCatching { TrackerItemType.valueOf(tName) }.getOrDefault(TrackerItemType.HABIT)
                    add(
                        CompletionLog(
                            id = lObj.optString("id"),
                            itemId = lObj.optString("itemId"),
                            itemTitle = lObj.optString("itemTitle"),
                            itemType = tType,
                            dateEpochDay = lObj.optLong("dateEpochDay", LocalDate.now().toEpochDay()),
                            completedAtTime = lObj.optString("completedAtTime", "12:00"),
                            completedAtMillis = lObj.optLong("completedAtMillis", 0L),
                            proofPhotoPath = lObj.optString("proofPhotoPath", "").ifBlank { null },
                            note = lObj.optString("note", ""),
                            numericDelta = lObj.optDouble("numericDelta", 0.0),
                            elapsedSeconds = lObj.optInt("elapsedSeconds", 0)
                        )
                    )
                }
            }

            HabitState(items = items, logs = logs)
        }.getOrDefault(HabitState())
    }

    fun save(state: HabitState) {
        val root = JSONObject()
        val itemsArr = JSONArray()
        state.items.forEach { item ->
            val daysArr = JSONArray().apply {
                item.schedule.specificDays.forEach { put(it.name) }
            }
            val schedObj = JSONObject().apply {
                put("cadence", item.schedule.cadence.name)
                put("specificDays", daysArr)
                put("customEvery", item.schedule.customEvery)
                put("customUnit", item.schedule.customUnit.name)
                put("anchorEpochDay", item.schedule.anchorEpochDay)
                put("scheduledTime", item.schedule.scheduledTime)
                put("alarmEnabled", item.schedule.alarmEnabled)
            }
            val chkArr = JSONArray().apply {
                item.checklistItems.forEach { sub ->
                    put(
                        JSONObject().apply {
                            put("id", sub.id)
                            put("label", sub.label)
                            put("checked", sub.checked)
                        }
                    )
                }
            }
            itemsArr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type.name)
                    put("category", item.category)
                    put("notes", item.notes)
                    put("schedule", schedObj)
                    put("attachments", encodeMediaList(item.attachments))
                    put("streak", item.streak)
                    put("evaluationMode", item.evaluationMode.name)
                    put("targetValue", item.targetValue)
                    put("targetUnit", item.targetUnit)
                    put("targetSeconds", item.targetSeconds)
                    put("checklistItems", chkArr)
                }
            )
        }

        val logsArr = JSONArray()
        state.logs.forEach { log ->
            logsArr.put(
                JSONObject().apply {
                    put("id", log.id)
                    put("itemId", log.itemId)
                    put("itemTitle", log.itemTitle)
                    put("itemType", log.itemType.name)
                    put("dateEpochDay", log.dateEpochDay)
                    put("completedAtTime", log.completedAtTime)
                    put("completedAtMillis", log.completedAtMillis)
                    put("proofPhotoPath", log.proofPhotoPath ?: "")
                    put("note", log.note)
                    put("numericDelta", log.numericDelta)
                    put("elapsedSeconds", log.elapsedSeconds)
                }
            )
        }

        root.put("items", itemsArr)
        root.put("logs", logsArr)
        prefs.edit().putString("state_v4_json", root.toString()).apply()
    }

    private fun parseMediaList(arr: JSONArray): List<MediaAttachment> = buildList {
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            add(
                MediaAttachment(
                    id = m.optString("id"),
                    displayName = m.optString("displayName"),
                    mimeType = m.optString("mimeType", "*/*"),
                    localPath = m.optString("localPath"),
                    sizeBytes = m.optLong("sizeBytes", 0L)
                )
            )
        }
    }

    private fun encodeMediaList(list: List<MediaAttachment>): JSONArray = JSONArray().apply {
        list.forEach { m ->
            put(
                JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("mimeType", m.mimeType)
                    put("localPath", m.localPath)
                    put("sizeBytes", m.sizeBytes)
                }
            )
        }
    }
}
