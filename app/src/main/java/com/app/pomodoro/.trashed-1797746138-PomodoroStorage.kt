package com.app.pomodoro

import android.content.Context
import org.json.JSONObject

class PomodoroStorage(context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_pomodoro_v4", Context.MODE_PRIVATE)

    fun load(): PomodoroState {
        val raw = prefs.getString("state_json", null) ?: return PomodoroState()
        return runCatching {
            val obj = JSONObject(raw)
            val focusMin = obj.optInt("focusMinutes", 25).coerceIn(1, 180)
            val breakMin = obj.optInt("breakMinutes", 5).coerceIn(1, 90)
            val phaseName = obj.optString("phase", "FOCUS")
            val phase = if (phaseName == "BREAK") PomodoroPhase.BREAK else PomodoroPhase.FOCUS
            val defaultRem = if (phase == PomodoroPhase.FOCUS) focusMin * 60 else breakMin * 60
            PomodoroState(
                focusMinutes = focusMin,
                breakMinutes = breakMin,
                remainingSeconds = obj.optInt("remainingSeconds", defaultRem).coerceAtLeast(0),
                phase = phase,
                isRunning = false,
                completedFocusCount = obj.optInt("completedFocusCount", 0)
            )
        }.getOrDefault(PomodoroState())
    }

    fun save(state: PomodoroState) {
        val obj = JSONObject().apply {
            put("focusMinutes", state.focusMinutes)
            put("breakMinutes", state.breakMinutes)
            put("remainingSeconds", state.remainingSeconds)
            put("phase", state.phase.name)
            put("completedFocusCount", state.completedFocusCount)
        }
        prefs.edit().putString("state_json", obj.toString()).apply()
    }
}
