@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.habitlair.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitlair.app.ai.AiOutcome
import com.habitlair.app.data.TaskItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun AiScreen(nav: NavController, initialTab: Int) {
    val vm = appViewModel { AiViewModel(it) }
    val status by vm.status.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
                title = { Text("AI assistant") },
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton({ nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "AI settings") } }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv)) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            TabRow(selectedTabIndex = tab) {
                listOf("Routine", "Break down", "Coach").forEachIndexed { i, l -> Tab(tab == i, { tab = i }, text = { Text(l) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> RoutineTab(vm)
                    1 -> BreakdownTab(vm)
                    else -> CoachTab(vm)
                }
            }
        }
    }
    preview?.let { p -> ImportPreviewSheet(p, { vm.dismissPreview() }, { vm.import(it) }) }
}

@Composable
private fun Outcome(o: AiOutcome<*>?) {
    if (o == null) return
    Text("Source: ${o.source}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    o.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Busy(loading: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth()) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Filled.AutoAwesome, null)
        Spacer(Modifier.width(8.dp)); Text(if (loading) "Working…" else label)
    }
}

@Composable
private fun RoutineTab(vm: AiViewModel) {
    val st by vm.routine.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf("") }
    Text("Describe a goal or your schedule and get a structured routine you can review before importing.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(goal, { goal = it }, label = { Text("e.g. Study Arabic 3 evenings a week and exercise on weekends") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Busy(st.loading, "Generate routine", goal.isNotBlank()) { vm.generateRoutine(goal.trim()) }
    Outcome(st.result)
    st.result?.value?.let { r -> Text("Last result: ${r.habits.size} habits, ${r.tasks.size} tasks, ${r.pomodoro.size} Pomodoro blocks", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun BreakdownTab(vm: AiViewModel) {
    val st by vm.breakdown.collectAsStateWithLifecycle()
    val open by vm.openTasks.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<TaskItem?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    Text("Split a task into ordered sub-tasks with time estimates. Pick an existing task or type a new one.", style = MaterialTheme.typography.bodyMedium)
    Box {
        OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(selected?.title ?: "Choose an existing task (optional)") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("New task (type below)") }, { selected = null; menu = false })
            open.forEach { t -> DropdownMenuItem({ Text(t.title) }, { selected = t; menu = false }) }
        }
    }
    if (selected == null) OutlinedTextField(title, { title = it }, label = { Text("Task to break down") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    Busy(st.loading, "Break down task", selected != null || title.isNotBlank()) {
        val t = selected
        vm.breakDown(t?.title ?: title.trim(), t?.notes ?: "", t)
    }
    Outcome(st.result)
}

@Composable
private fun CoachTab(vm: AiViewModel) {
    val st by vm.coach.collectAsStateWithLifecycle()
    Text("Reviews your recent journal moods and habit streaks, then suggests adjustments. With context sharing off, analysis stays on your device.", style = MaterialTheme.typography.bodyMedium)
    Busy(st.loading, "Review my progress", true) { vm.runCoach() }
    val r = st.result
    Outcome(r)
    if (r != null) {
        val rep = r.value
        if (rep.summary.isNotBlank()) Text(rep.summary, style = MaterialTheme.typography.bodyLarge)
        if (rep.insights.isNotEmpty()) { SectionLabel("Insights"); rep.insights.forEach { Text("• $it") } }
        if (rep.suggestions.isNotEmpty()) {
            SectionLabel("Suggestions")
            rep.suggestions.forEach { s ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(s.title, style = MaterialTheme.typography.titleSmall); if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodyMedium) } }
            }
        }
        if (rep.newHabits.isNotEmpty()) OutlinedButton({ vm.previewCoachHabits(r) }, Modifier.fillMaxWidth()) { Text("Review ${rep.newHabits.size} suggested habit(s)") }
    }
}
