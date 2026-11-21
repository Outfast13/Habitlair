package com.habitlair.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.habitlair.app.HabitLairApp
import com.habitlair.app.MainActivity
import com.habitlair.app.data.Dates
import com.habitlair.app.data.Habit
import com.habitlair.app.data.isScheduled

val HabitIdKey = ActionParameters.Key<Long>("habitId")

class HabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as HabitLairApp).container
        val day = Dates.today()
        val habits = c.db.habits().all().filter { !it.archived && it.isScheduled(day) }.sortedBy { it.timeMinutes ?: Int.MAX_VALUE }
        val done = c.db.habits().doneIds(day).toSet()
        provideContent { GlanceTheme { WidgetContent(habits, done) } }
    }
}

@Composable
private fun WidgetContent(habits: List<Habit>, done: Set<Long>) {
    val total = habits.size
    val n = habits.count { it.id in done }
    Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "HabitLair · Today", modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurface)
            )
            Text("$n/$total", style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (total == 0) 0f else n.toFloat() / total,
            modifier = GlanceModifier.fillMaxWidth(),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer
        )
        Spacer(GlanceModifier.height(6.dp))
        if (habits.isEmpty()) {
            Text("Nothing scheduled today", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            LazyColumn {
                items(habits, itemId = { it.id }) { h ->
                    CheckBox(
                        checked = h.id in done,
                        onCheckedChange = actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id)),
                        text = (h.timeMinutes?.let { Dates.fmtTime(it) + "  " } ?: "") + h.name,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
                    )
                }
            }
        }
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        (context.applicationContext as HabitLairApp).container.repo.toggleHabit(id, Dates.today())
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
