package dev.mauve.fit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mauve.fit.App
import dev.mauve.fit.MainActivity
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

// The widget always wears the dark copper look, whatever theme the app uses.
private val Bg = Color(0xFF111315)
private val Surface = Color(0xFF1A1D21)
private val Raised = Color(0xFF23272C)
private val TextColor = Color(0xFFEDEFF2)
private val Muted = Color(0xFFA6ACB4)
private val Copper = Color(0xFFDB9062)

/** Home screen widget: the next workout (or the running one) and this week's progress. */
class NextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data by App.store.data.collectAsState()
            Content(data)
        }
    }

    @Composable
    private fun Content(d: AppData) {
        val active = d.active
        val next = if (active == null) Stats.nextRoutine(d.routines, d.history, App.repo) else null
        val week = Stats.workoutsThisWeek(d.history)
        val goal = d.settings.weeklyGoal.coerceAtLeast(1)
        val count = active?.entries?.size ?: next?.exercises?.size ?: 0
        Column(
            GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                if (active != null) L("Training läuft", "Workout running") else L("Als Nächstes", "Up next"),
                style = TextStyle(color = ColorProvider(Copper), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                active?.name ?: next?.name ?: L("Kein Plan", "No plan"),
                style = TextStyle(color = ColorProvider(TextColor), fontSize = 19.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Text(
                when {
                    active == null && next == null -> L("Lege einen Plan in der App an", "Create a plan in the app")
                    count == 1 -> L("1 Übung", "1 exercise")
                    else -> L("$count Übungen", "$count exercises")
                },
                style = TextStyle(color = ColorProvider(Muted), fontSize = 13.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxWidth().background(Surface).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Diese Woche", "This week"), GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(Muted), fontSize = 13.sp))
                    Text("$week/$goal", style = TextStyle(color = ColorProvider(Copper), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    (week / goal.toFloat()).coerceIn(0f, 1f), GlanceModifier.fillMaxWidth(),
                    color = ColorProvider(Copper), backgroundColor = ColorProvider(Raised),
                )
            }
        }
    }

    companion object {
        /** Refreshes placed widgets when something they show changes; cheap when none are placed. */
        @OptIn(FlowPreview::class)
        fun follow(context: Context) {
            App.store.data
                .map { d -> listOf(d.active?.name, d.routines, d.history.size, d.history.firstOrNull()?.id, d.settings.weeklyGoal) }
                .distinctUntilChanged()
                .debounce(1000)
                .onEach { runCatching { NextWidget().updateAll(context) } }
                .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}
