package dev.mauve.fit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Motivation
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.Recovery
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** 2x2: weeks in a row that met the goal (freezes honoured), freezes in stock and today's readiness. */
class StreakWidget : GlanceAppWidget() {
    // Below about 180 dp the bars do not fit next to the readiness panel; the compact one drops them.
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 100.dp), DpSize(110.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Readiness is only read when the app comes to the front; try once if the process is fresh.
        if (Recovery.today.value == null) {
            runCatching { withTimeoutOrNull(4000) { Recovery.refresh(context.applicationContext) } }
        }
        provideContent {
            val data by App.store.data.collectAsState()
            val readiness by Recovery.today.collectAsState()
            Content(data, readiness?.score)
        }
    }

    @Composable
    private fun Content(d: AppData, readiness: Int?) {
        val context = LocalContext.current
        val streak = Stats.weekStreak(d.history, d.settings.weeklyGoal, d.frozenWeeks)
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(14.dp).clickable(openApp(context))) {
            Eyebrow(L("Serie", "Streak"))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$streak", style = style(TextColor, 30.sp, FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (streak == 1) L("Woche", "week") else L("Wochen", "weeks"),
                    GlanceModifier.padding(bottom = 5.dp), style = style(Muted, 13.sp), maxLines = 1,
                )
            }
            if (d.freezes > 0) Text(Motivation.freezeLabel(d.freezes), style = style(Mauve, 12.sp, FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (LocalSize.current.height >= 180.dp) {
                Weeks(d)
                Spacer(GlanceModifier.defaultWeight())
            }
            if (readiness != null) {
                Column(GlanceModifier.fillMaxWidth().background(Surface).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(L("Erholung", "Readiness"), GlanceModifier.defaultWeight(), style = style(Muted, 12.sp), maxLines = 1)
                        Text("$readiness", style = style(Copper, 14.sp, FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    LinearProgressIndicator(
                        readiness / 100f, GlanceModifier.fillMaxWidth(),
                        color = ColorProvider(Copper), backgroundColor = ColorProvider(Raised),
                    )
                }
            } else {
                val week = Stats.workoutsThisWeek(d.history)
                Text(
                    L("Diese Woche $week/${d.settings.weeklyGoal.coerceAtLeast(1)}", "This week $week/${d.settings.weeklyGoal.coerceAtLeast(1)}"),
                    style = style(Muted, 12.sp), maxLines = 1,
                )
            }
        }
    }
}

/**
 * The last eight weeks as bars, this week on the right: full height when the goal was met, a
 * part for each workout short of it, frozen weeks in mauve.
 */
@Composable
private fun Weeks(d: AppData) {
    val goal = d.settings.weeklyGoal.coerceAtLeast(1)
    val monday = Stats.weekStart(LocalDate.now())
    val counts = d.history.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount()
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 7 downTo 0) {
                val week = monday.minusWeeks(i.toLong())
                val n = counts[week] ?: 0
                val frozen = Stats.weekKey(week) in d.frozenWeeks
                val h = when {
                    frozen || n >= goal -> 34
                    n == 0 -> 4
                    else -> 4 + 30 * n / goal
                }
                val color = when {
                    frozen -> Mauve
                    n >= goal -> Copper
                    i == 0 -> TextColor
                    else -> Muted
                }
                Box(GlanceModifier.defaultWeight().height(34.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(GlanceModifier.width(10.dp).height(h.dp).background(if (n == 0 && !frozen) Raised else color).cornerRadius(5.dp)) {}
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(L("Letzte 8 Wochen", "Last 8 weeks"), style = style(Muted, 11.sp), maxLines = 1)
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
