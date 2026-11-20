package com.app.pomodoro

enum class PomodoroPhase { FOCUS, BREAK }

data class PomodoroState(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingSeconds: Int = 25 * 60,
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val isRunning: Boolean = false,
    val completedFocusCount: Int = 0
)
