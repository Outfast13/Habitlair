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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.app.ai.AiSettingsStorage
import com.app.backup.BackupManager
import com.app.habits.HabitStorage
import com.app.habits.TrackerItem
import com.app.habits.TrackerItemType
import com.app.journal.JournalStorage
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private enum class AppTab(val label: String, val icon: String) {
    HABITS("Habits", "↻"),
    TASKS("Tasks", "✓"),
    REMINDERS("Remind", "⏰"),
    JOURNAL("Journal", "✎"),
    POMODORO("Timer", "⏱"),
    AI("AI", "✦"),
    SETTINGS("Config", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val aiStorage = AiSettingsStorage(applicationContext)
        val habitStorage = HabitStorage(applicationContext)
        val journalStorage = JournalStorage(applicationContext)
        val pomodoroStorage = PomodoroStorage(applicationContext)
        val backupManager = BackupManager(applicationContext)
        backupManager.runAutoBackupIfEnabled()

        setContent {
            val scope = rememberCoroutineScope()
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            var currentTab by remember { mutableStateOf(AppTab.HABITS) }
            var selectedDate by remember { mutableStateOf(LocalDate.now()) }
            var aiState by remember { mutableStateOf(aiStorage.load()) }
            var habitsState by remember { mutableStateOf(habitStorage.load()) }
            var journalState by remember { mutableStateOf(journalStorage.load()) }
            var pomodoroState by remember { mutableStateOf(pomodoroStorage.load()) }

            fun reloadAllFromStorage() {
                aiState = aiStorage.load()
                habitsState = habitStorage.load()
                journalState = journalStorage.load()
                pomodoroState = pomodoroStorage.load()
                HabitLairWidgetProvider.refreshAll(applicationContext)
            }

            HabitLairTheme(settings = aiState) {
                val primary = MaterialTheme.colorScheme.primary
                val onPrimary = MaterialTheme.colorScheme.onPrimary

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        HistoryDrawerContent(
                            habitState = habitsState,
                            onClose = { scope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    Scaffold(
                        containerColor = LairPalette.Canvas,
                        bottomBar = {
                            NavigationBar(containerColor = LairPalette.SurfaceCard) {
                                AppTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        },
                                        icon = { Text(tab.icon) },
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
                                targetState = currentTab,
                                animationSpec = tween(180),
                                label = "mainTabCrossfade"
                            ) { target ->
                                when (target) {
                                    AppTab.HABITS -> TrackerScreen(
                                        fixedType = TrackerItemType.HABIT,
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = habitsState,
                                        storage = habitStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onStateChanged = { updated ->
                                            habitsState = updated
                                            habitStorage.save(updated)
                                            HabitLairWidgetProvider.refreshAll(applicationContext)
                                        }
                                    )

                                    AppTab.TASKS -> TrackerScreen(
                                        fixedType = TrackerItemType.TASK,
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = habitsState,
                                        storage = habitStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onStateChanged = { updated ->
                                            habitsState = updated
                                            habitStorage.save(updated)
                                            HabitLairWidgetProvider.refreshAll(applicationContext)
                                        }
                                    )

                                    AppTab.REMINDERS -> TrackerScreen(
                                        fixedType = TrackerItemType.REMINDER,
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = habitsState,
                                        storage = habitStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onStateChanged = { updated ->
                                            habitsState = updated
                                            habitStorage.save(updated)
                                            HabitLairWidgetProvider.refreshAll(applicationContext)
                                        }
                                    )

                                    AppTab.JOURNAL -> JournalScreen(
                                        selectedDate = selectedDate,
                                        onSelectDate = { selectedDate = it },
                                        settings = aiState,
                                        initialState = journalState,
                                        storage = journalStorage,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onStateChanged = { updated ->
                                            journalState = updated
                                            journalStorage.save(updated)
                                        }
                                    )

                                    AppTab.POMODORO -> PomodoroScreen(
                                        initialState = pomodoroState,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onStateChanged = { updated ->
                                            pomodoroState = updated
                                            pomodoroStorage.save(updated)
                                        }
                                    )

                                    AppTab.AI -> AiWorkspaceScreen(
                                        aiState = aiState,
                                        onApproveProposedItem = { draft ->
                                            val added = TrackerItem(
                                                id = UUID.randomUUID().toString(),
                                                title = draft.title,
                                                type = draft.type,
                                                category = draft.category,
                                                notes = draft.reason
                                            )
                                            val next = habitsState.copy(items = listOf(added) + habitsState.items)
                                            habitsState = next
                                            habitStorage.save(next)
                                            HabitLairWidgetProvider.refreshAll(applicationContext)
                                        },
                                        onOpenSettings = { currentTab = AppTab.SETTINGS },
                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                    )

                                    AppTab.SETTINGS -> AppSettingsScreen(
                                        aiState = aiState,
                                        backupManager = backupManager,
                                        onSaveAiState = { updated ->
                                            aiState = updated
                                            aiStorage.save(updated)
                                        },
                                        onBackupRestored = { reloadAllFromStorage() },
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
