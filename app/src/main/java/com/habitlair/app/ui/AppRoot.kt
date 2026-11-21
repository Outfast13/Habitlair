package com.habitlair.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Filled.Today),
    Tab("habits", "Habits", Icons.Filled.EmojiEvents),
    Tab("tasks", "Tasks", Icons.Filled.TaskAlt),
    Tab("categories", "Categories", Icons.Filled.Category),
    Tab("timer", "Timer", Icons.Filled.Timer)
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val anim = LocalAnim.current
    val enter = if (anim) fadeIn() else EnterTransition.None
    val exit = if (anim) fadeOut() else ExitTransition.None

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pv ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pv),
            enterTransition = { enter }, exitTransition = { exit }, popEnterTransition = { enter }, popExitTransition = { exit }) {
            composable("today") { TodayScreen(nav) }
            composable("habits") { HabitsScreen(nav) }
            composable("tasks") { TasksScreen(nav) }
            composable("categories") { CategoriesScreen() }
            composable("timer") { TimerScreen() }
            composable("habit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { HabitDetailScreen(it.arguments?.getLong("id") ?: 0L, nav) }
            composable("journal") { JournalScreen(nav) }
            composable("ai?tab={tab}", listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { AiScreen(nav, it.arguments?.getInt("tab") ?: 0) }
            composable("settings") { SettingsScreen(nav) }
        }
    }
}
