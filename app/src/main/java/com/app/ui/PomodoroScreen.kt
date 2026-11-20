package com.app.ui

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
import com.app.pomodoro.PomodoroPhase
import com.app.pomodoro.PomodoroState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PomodoroScreen(
    initialState: PomodoroState,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onStateChanged: (PomodoroState) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary

    var state by remember(initialState) { mutableStateOf(initialState) }
    var focusInput by remember(state.focusMinutes) { mutableStateOf(state.focusMinutes.toString()) }
    var breakInput by remember(state.breakMinutes) { mutableStateOf(state.breakMinutes.toString()) }

    fun commit(next: PomodoroState) {
        state = next
        onStateChanged(next)
    }

    LaunchedEffect(state.isRunning, state.phase, state.remainingSeconds) {
        if (!state.isRunning) return@LaunchedEffect
        delay(1000L)
        if (state.remainingSeconds > 1) {
            commit(state.copy(remainingSeconds = state.remainingSeconds - 1))
        } else {
            val nextPhase = if (state.phase == PomodoroPhase.FOCUS) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
            val nextCompleted = if (state.phase == PomodoroPhase.FOCUS) state.completedFocusCount + 1 else state.completedFocusCount
            val nextSec = if (nextPhase == PomodoroPhase.FOCUS) state.focusMinutes * 60 else state.breakMinutes * 60
            commit(
                state.copy(
                    phase = nextPhase,
                    remainingSeconds = nextSec,
                    isRunning = false,
                    completedFocusCount = nextCompleted
                )
            )
        }
    }

    val mins = (state.remainingSeconds / 60).coerceAtLeast(0)
    val secs = (state.remainingSeconds % 60).coerceAtLeast(0)
    val clockText = String.format(Locale.ROOT, "%02d:%02d", mins, secs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LairPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HabitLairTopBar(
            sectionTitle = "POMODORO",
            sectionSubtitle = "Focus intervals, break blocks & session counter",
            badgeText = "${state.completedFocusCount} blocks",
            onOpenDrawer = onOpenDrawer,
            onOpenSettings = onOpenSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (state.phase == PomodoroPhase.FOCUS) "FOCUS SESSION" else "BREAK SESSION",
                color = if (state.phase == PomodoroPhase.FOCUS) primary else secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = clockText,
                color = LairPalette.TextPrimary,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale { commit(state.copy(isRunning = !state.isRunning)) }
                        .background(primary, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (state.isRunning) "Pause" else "Start", color = onPrimary, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale {
                            val sec = if (state.phase == PomodoroPhase.FOCUS) state.focusMinutes * 60 else state.breakMinutes * 60
                            commit(state.copy(isRunning = false, remainingSeconds = sec))
                        }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Reset", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale {
                            val nextP = if (state.phase == PomodoroPhase.FOCUS) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
                            val nextS = if (nextP == PomodoroPhase.FOCUS) state.focusMinutes * 60 else state.breakMinutes * 60
                            commit(state.copy(phase = nextP, remainingSeconds = nextS, isRunning = false))
                        }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Switch", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Durations (Minutes)", color = LairPalette.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = focusInput,
                    onValueChange = { focusInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Focus", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = breakInput,
                    onValueChange = { breakInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Break", color = LairPalette.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LairPalette.TextPrimary,
                        unfocusedTextColor = LairPalette.TextPrimary,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = LairPalette.BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale {
                        val f = (focusInput.toIntOrNull() ?: state.focusMinutes).coerceIn(1, 180)
                        val b = (breakInput.toIntOrNull() ?: state.breakMinutes).coerceIn(1, 90)
                        val rem = if (state.phase == PomodoroPhase.FOCUS) f * 60 else b * 60
                        commit(state.copy(focusMinutes = f, breakMinutes = b, remainingSeconds = rem, isRunning = false))
                    }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(12.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply Durations", color = primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
