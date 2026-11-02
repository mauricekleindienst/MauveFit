package dev.mauve.fit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Motivation
import dev.mauve.fit.data.Stats
import dev.mauve.fit.watch.WatchSync
import java.time.LocalDate

/**
 * Home screen widget: the next workout (or the running one) and this week's progress. Three
 * layouts by size: a single line (2x1), the card with a start button (3x2) and the full week with
 * challenge and training block (4x3 and up).
 */
class NextWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data by App.store.data.collectAsState()
            Content(data)
        }
    }

    @Composable
    private fun Content(d: AppData) {
        val context = LocalContext.current
        val size = LocalSize.current
        val active = d.active
        val next = if (active == null) Stats.nextRoutine(d.routines, d.history, App.repo) else null
        val week = Stats.workoutsThisWeek(d.history)
        val goal = d.settings.weeklyGoal.coerceAtLeast(1)
        val open = openApp(context, if (active != null) "workout" else null)
        when {
            size.height < MEDIUM.height || size.width < MEDIUM.width -> Small(d, next?.name, week, goal, open)
            size.height < LARGE.height || size.width < LARGE.width -> Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(16.dp).clickable(open),
            ) {
                Head(d, next?.name, next?.exercises?.size, context)
                Spacer(GlanceModifier.defaultWeight())
                WeekBar(week, goal)
            }
            else -> Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(16.dp).clickable(open),
            ) {
                Head(d, next?.name, next?.exercises?.size, context)
                Spacer(GlanceModifier.defaultWeight())
                Extras(d)
                Spacer(GlanceModifier.height(8.dp))
                WeekBar(week, goal, strip = Stats.weekDays(d.history))
            }
        }
    }

    /** 2x1: name of the next (or running) workout and the week count. */
    @Composable
    private fun Small(d: AppData, nextName: String?, week: Int, goal: Int, open: Action) {
        Row(
            GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 6.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Eyebrow(if (d.active != null) L("Läuft", "Running") else L("Als Nächstes", "Up next"))
                Text(
                    d.active?.name ?: nextName ?: L("Kein Plan", "No plan"),
                    style = style(TextColor, 15.sp, FontWeight.Bold), maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text("$week/$goal", style = style(Copper, 16.sp, FontWeight.Bold), maxLines = 1)
        }
    }

    /** Title block with the button: "Start" for the next plan, "Open" while a workout runs. */
    @Composable
    private fun Head(d: AppData, nextName: String?, nextCount: Int?, context: Context) {
        val active = d.active
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(GlanceModifier.defaultWeight()) {
                Eyebrow(if (active != null) L("Training läuft", "Workout running") else L("Als Nächstes", "Up next"))
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    active?.name ?: nextName ?: L("Kein Plan", "No plan"),
                    style = style(TextColor, 18.sp, FontWeight.Bold), maxLines = 2,
                )
                val line = when {
                    active != null -> {
                        val s = WatchSync.state(d)
                        val set = if (s.warmup) L("Aufwärmen", "Warm-up") else L("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}")
                        "${s.exercise} · $set"
                    }
                    nextName == null -> L("Lege einen Plan in der App an", "Create a plan in the app")
                    nextCount == 1 -> L("1 Übung", "1 exercise")
                    else -> L("$nextCount Übungen", "$nextCount exercises")
                }
                Text(line, style = style(Muted, 13.sp), maxLines = 2)
            }
            if (active != null) {
                Spacer(GlanceModifier.width(8.dp))
                Pill(L("Öffnen", "Open"), openApp(context, "workout"))
            } else if (nextName != null) {
                Spacer(GlanceModifier.width(8.dp))
                Pill("Start", openApp(LocalContext.current, "start-next"))
            }
        }
    }

    /** Weekly challenge and the training block week, if one runs. */
    @Composable
    private fun Extras(d: AppData) {
        val challenge = Motivation.challenge(d, App.repo)
        val block = Coach.blockWeek(d)
        Column(GlanceModifier.fillMaxWidth().background(Surface).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(challenge.title, GlanceModifier.defaultWeight(), style = style(TextColor, 13.sp, FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.width(8.dp))
                Text(challenge.label, style = style(if (challenge.done) Copper else Mauve, 13.sp, FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                challenge.fraction, GlanceModifier.fillMaxWidth(),
                color = ColorProvider(Mauve), backgroundColor = ColorProvider(Raised),
            )
            if (block != null) {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    L("Block: Woche ${block.week} von ${block.weeks} · ${block.phase.label}", "Block: week ${block.week} of ${block.weeks} · ${block.phase.label}"),
                    style = style(Muted, 12.sp), maxLines = 1,
                )
            }
        }
    }

    /** This week's count against the goal, with the Mo..Su strip in the large layout. */
    @Composable
    private fun WeekBar(week: Int, goal: Int, strip: Set<LocalDate>? = null) {
        Column(GlanceModifier.fillMaxWidth().background(Surface).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(L("Diese Woche", "This week"), GlanceModifier.defaultWeight(), style = style(Muted, 13.sp))
                Text("$week/$goal", style = style(Copper, 15.sp, FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (strip == null) {
                LinearProgressIndicator(
                    (week / goal.toFloat()).coerceIn(0f, 1f), GlanceModifier.fillMaxWidth(),
                    color = ColorProvider(Copper), backgroundColor = ColorProvider(Raised),
                )
            } else {
                val today = LocalDate.now()
                val monday = Stats.weekStart(today)
                val names = if (I18n.german) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
                else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                Row(GlanceModifier.fillMaxWidth()) {
                    names.forEachIndexed { i, name ->
                        val day = monday.plusDays(i.toLong())
                        val isToday = day == today
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                name, GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = ColorProvider(if (isToday) TextColor else Muted), fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Box(GlanceModifier.size(18.dp).background(if (day in strip) Copper else Raised).cornerRadius(9.dp)) {}
                        }
                    }
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(100.dp, 40.dp)
        val MEDIUM = DpSize(170.dp, 100.dp)
        // Head, challenge and the week strip need about 250 dp; a 3x2 widget on a phone is around
        // 210 dp high and cut the strip off, so it gets the medium layout.
        val LARGE = DpSize(240.dp, 250.dp)

        /** Kept for App.onCreate: follows the store for every widget of the app. */
        fun follow(context: Context) = Widgets.follow(context)
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}
