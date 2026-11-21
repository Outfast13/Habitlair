@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.habitlair.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitlair.app.data.*

@Composable
fun TasksScreen(nav: NavController) {
    val vm = appViewModel { TasksViewModel(it) }
    val hvm = appViewModel { HabitViewModel(it) }
    val c = LocalContainer.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val habits by hvm.habits.collectAsStateWithLifecycle()
    var status by rememberSaveable { mutableIntStateOf(0) }
    var prio by rememberSaveable { mutableIntStateOf(-1) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Open<TaskUi>?>(null) }

    val allTags = tasks.flatMap { it.task.tags.tagList() }.distinct().sorted()
    val shown = tasks
        .filter { when (status) { 0 -> !it.task.done; 1 -> it.task.done; else -> true } }
        .filter { prio < 0 || it.task.priority == prio }
        .filter { tag == null || tag in it.task.tags.tagList() }
        .filter { query.isBlank() || it.task.title.contains(query, true) || it.subs.any { s -> s.title.contains(query, true) } }
        .sortedWith(compareBy<TaskUi>({ it.task.done }, { it.task.dueEpochDay ?: Long.MAX_VALUE }, { -it.task.priority }))

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text("Tasks", style = MaterialTheme.typography.headlineMedium) }) },
        floatingActionButton = { FloatingActionButton({ sheet = Open() }) { Icon(Icons.Filled.Add, "New task") } }
    ) { pv ->
        Column(Modifier.padding(pv)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search tasks") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Clear search") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Open", "Done", "All").forEachIndexed { i, l -> FilterChip(status == i, { status = i }, { Text(l) }) }
                listOf("Low", "Medium", "High").forEachIndexed { i, l -> FilterChip(prio == i, { prio = if (prio == i) -1 else i }, { Text(l) }) }
                allTags.forEach { t -> FilterChip(tag == t, { tag = if (tag == t) null else t }, { Text("#$t") }) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (shown.isEmpty()) item { EmptyState(Icons.Filled.TaskAlt, if (tasks.isEmpty()) "No tasks yet. Tap + to add one." else "No tasks match your filters.") }
                items(shown, key = { it.task.id }) { t ->
                    Column {
                        TaskRow(t, 0, { vm.setDone(t.task.id, !t.task.done) }, { sheet = Open(t) })
                        Column(Modifier.padding(start = 74.dp, end = 16.dp)) {
                            val meta = buildList {
                                t.task.dueEpochDay?.let { add(Dates.fmtDate(it)) }
                                t.task.estimateMinutes?.let { add("$it min") }
                                if (t.focusMin > 0) add("⏱ ${t.focusMin}m focused")
                                t.task.tags.tagList().forEach { add("#$it") }
                            }.joinToString("  ·  ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            t.subs.forEach { s ->
                                Row(Modifier.clickable { vm.setDone(s.id, !s.done) }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(s.done, { vm.setDone(s.id, it) })
                                    Text(s.title + (s.estimateMinutes?.let { " · ${it}m" } ?: ""), style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (s.done) TextDecoration.LineThrough else null)
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
    sheet?.let { s ->
        TaskEditorSheet(
            s.value?.task, s.value?.subs.orEmpty(), habits.map { it.habit }, s.value?.focusMin ?: 0,
            { t, subs, a -> vm.save(t, subs, a); sheet = null },
            s.value?.let { u -> { vm.delete(u.task.id); sheet = null } },
            { t -> c.pomodoro.setLink(null, t.id); sheet = null; nav.goTab("timer") },
            { sheet = null }
        )
    }
}
