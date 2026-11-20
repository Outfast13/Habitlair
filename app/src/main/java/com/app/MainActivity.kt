package com.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsStorage
import com.app.backup.BackupManager
import com.app.habits.EvaluationMode
import com.app.habits.HabitStorage
import com.app.habits.TrackerItem
import com.app.habits.TrackerItemType
import com.app.journal.JournalStorage
import com.app.notifications.ReminderScheduler
import com.app.notifications.ScreenTimeMonitor
import com.app.notifications.ScreenTimeSnapshot
import com.app.pomodoro.PomodoroStorage
import com.app.ui.AiWorkspaceScreen
import com.app.ui.AppSettingsScreen
import com.app.ui.HabitLairTheme
import com.app.ui.HistoryDrawerContent
import com.app.ui.JournalScreen
import com.app.ui.LairPalette
import com.app.ui.PomodoroScreen
import com.app.ui.TrackerScreen
import com.app.widget.HabitLairWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private enum class AppDestination(val label: String, val icon: ImageVector) {
    HABITS("Habits", Icons.Outlined.Repeat),
    TASKS("Tasks", Icons.Outlined.CheckCircle),
    JOURNAL("Journal", Icons.Outlined.Book),
    TIMER("Timer", Icons.Outlined.Timer),
    SETTINGS("Settings", Icons.Outlined.Timer),
    AI_STUDIO("AI Studio", Icons.Outlined.Timer)
}

private val PRIMARY_NAV_TABS = listOf(
    AppDestination.HABITS,
    AppDestination.TASKS,
    AppDestination.JOURNAL,
    AppDestination.TIMER
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val aiStorage = AiSettingsStorage(applicationContext)
        val habitStorage = HabitStorage(applicationContext)
        val journalStorage = JournalStorage(applicationContext)
        val pomodoroStorage = PomodoroStorage(applicationContext)
        val backupManager = BackupManager(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            val haptics = LocalHapticFeedback.current
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            var destination by remember { mutableStateOf(AppDestination.HABITS) }
            var selectedDate by remember { mutableStateOf(LocalDate.now()) }
            var aiState by remember { mutableStateOf(aiStorage.load()) }
            var habitsState by remember { mutableStateOf(habitStorage.load()) }
            var journalState by remember { mutableStateOf(journalStorage.load()) }
            var pomodoroState by remember { mutableStateOf(pomodoroStorage.load()) }
            var screenSnapshot by remember {
                mutableStateOf(ScreenTimeSnapshot(hasUsagePermission = false, todayMinutes = 0, topAppName = "Checking"))
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    backupManager.runAutoBackupIfEnabled()
                    ReminderScheduler.rescheduleAll(applicationContext, habitsState.items, LocalDate.now())
                    val snap = ScreenTimeMonitor.queryTodayScreenTime(applicationContext)
                    withContext(Dispatchers.Main) {
                        screenSnapshot = snap
                    }
                    if (aiState.screenTimeGuardEnabled && snap.hasUsagePermission && snap.todayMinutes >= aiState.dailyScreenLimitMinutes) {
                        ReminderScheduler.sendScreenTimeWarningNotification(
                            applicationContext,
                            snap.todayMinutes,
                            aiState.dailyScreenLimitMinutes
                        )
                    }
                }
            }

            fun persistHabits(updated: com.app.habits.HabitState) {
                habitsState = updated
                scope.launch(Dispatchers.IO) {
                    habitStorage.save(updated)
                    HabitLairWidgetProvider.refreshAll(applicationContext)
                }
            }

            fun persistJournal(updated: com.app.journal.JournalState) {
                journalState = updated
                scope.launch(Dispatchers.IO) {
                    journalStorage.save(updated)
                }
            }

            fun persistPomodoro(updated: com.app.pomodoro.PomodoroState) {
                pomodoroState = updated
                scope.launch(Dispatchers.IO) {
                    pomodoroStorage.save(updated)
                }
            }

            fun reloadAllFromStorage() {
                scope.launch(Dispatchers.IO) {
                    val loadedAi = aiStorage.load()
                    val loadedHabits = habitStorage.load()
                    val loadedJournal = journalStorage.load()
                    val loadedPomodoro = pomodoroStorage.load()
                    val snap = ScreenTimeMonitor.queryTodayScreenTime(applicationContext)
                    HabitLairWidgetProvider.refreshAll(applicationContext)
                    withContext(Dispatchers.Main) {
                        aiState = loadedAi
                        habitsState = loadedHabits
                        journalState = loadedJournal
                        pomodoroState = loadedPomodoro
                        screenSnapshot = snap
                    }
                }
            }

            HabitLairTheme(settings = aiState) {
                val primary = MaterialTheme.colorScheme.primary
                val onPrimary = MaterialTheme.colorScheme.onPrimary

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        HistoryDrawerContent(
                            habitState = habitsState,
                            onClose = { scope.launch { drawerState.close() } },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                destination = AppDestination.SETTINGS
                            },
                            onOpenAi = {
                                scope.launch { drawerState.close() }
                                destination = AppDestination.AI_STUDIO
                            }
                        )
                    }
                ) {
                    Scaffold(
                        containerColor = LairPalette.Canvas,
                        bottomBar = {
                            NavigationBar(
                                containerColor = LairPalette.SurfaceCard,
                                tonalElevation = 0.dp
                            ) {
                                PRIMARY_NAV_TABS.forEach { tab ->
                                    val selected = destination == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            destination = tab
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = onPrimary,
                                            selectedTextColor = primary,
                                            indicatorColor = primary,
                                            unselectedIconColor = LairPalette.TextSecondary,
                                            unselectedTextColor = LairPalette.TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(LairPalette.Canvas)
                        ) {
                            Crossfade(
                                targetState = destination,
                                animationSpec = tween(durationMillis = 120),
                                label = "mainDestinationCrossfade"
                            ) { target ->
                                when (target) {
                                    AppDestination.HABITS -> TrackerScreen(
                                        fixedType = TrackerItemType.HABIT,
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = habitsState,
                                        storage = habitStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenSettings = { destination = AppDestination.SETTINGS },
                                        onStateChanged = { persistHabits(it) }
                                    )

                                    AppDestination.TASKS -> TrackerScreen(
                                        fixedType = TrackerItemType.TASK,
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = habitsState,
                                        storage = habitStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenSettings = { destination = AppDestination.SETTINGS },
                                        onStateChanged = { persistHabits(it) }
                                    )

                                    AppDestination.JOURNAL -> JournalScreen(
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = journalState,
                                        storage = journalStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenSettings = { destination = AppDestination.SETTINGS },
                                        onStateChanged = { persistJournal(it) }
                                    )

                                    AppDestination.TIMER -> PomodoroScreen(
                                        initialState = pomodoroState,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenSettings = { destination = AppDestination.SETTINGS },
                                        onStateChanged = { persistPomodoro(it) }
                                    )

                                    AppDestination.SETTINGS -> AppSettingsScreen(
                                        aiState = aiState,
                                        backupManager = backupManager,
                                        onSaveAiState = { updated ->
                                            aiState = updated
                                            scope.launch(Dispatchers.IO) {
                                                aiStorage.save(updated)
                                                if (updated.notificationsEnabled) {
                                                    ReminderScheduler.rescheduleAll(applicationContext, habitsState.items, LocalDate.now())
                                                }
                                            }
                                        },
                                        onBackupRestored = { reloadAllFromStorage() },
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenAiStudio = { destination = AppDestination.AI_STUDIO }
                                    )

                                    AppDestination.AI_STUDIO -> AiWorkspaceScreen(
                                        aiState = aiState,
                                        habitState = habitsState,
                                        screenTimeSnapshot = screenSnapshot,
                                        onApproveProposedItem = { draft ->
                                            val mode = runCatching {
                                                EvaluationMode.valueOf(draft.evaluationMode.uppercase())
                                            }.getOrDefault(EvaluationMode.YES_NO)
                                            val added = TrackerItem(
                                                id = UUID.randomUUID().toString(),
                                                title = draft.title,
                                                type = draft.type,
                                                category = draft.category,
                                                notes = draft.reason,
                                                evaluationMode = mode
                                            )
                                            persistHabits(habitsState.copy(items = listOf(added) + habitsState.items))
                                            ReminderScheduler.scheduleForItem(applicationContext, added, LocalDate.now())
                                        },
                                        onOpenSettings = { destination = AppDestination.SETTINGS },
                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
