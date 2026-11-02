package dev.mauve.fit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.Insights
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.Haptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

private val shortFmt get() = DateTimeFormatter.ofPattern(L("d. MMM", "MMM d"), I18n.locale)

private val lookup = Insights.Lookup { App.repo[it] }

/** Headline numbers for the Insights tile: streak, goal rate, four-week volume change. */
private data class Headline(val streak: Int, val rate: Insights.Rate, val volume: Double?)

/** Card at the top of Progress > Overview that opens the Insights screen. */
@Composable
fun InsightsTile(data: AppData, onClick: () -> Unit) {
    val day by rememberToday()
    val h = remember(data.history, data.settings.weeklyGoal, data.frozenWeeks, day) {
        val today = day
        Headline(
            Insights.streaks(data.history, data.settings.weeklyGoal, today, data.frozenWeeks).current,
            Insights.goalRate(data.history, data.settings.weeklyGoal, today),
            Insights.volumeChange(data.history, today),
        )
    }
    Card(Modifier.fillMaxWidth(), onClick = onClick, color = Ink.raised, shape = Hero, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Insights, null, tint = Ink.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Deine Analysen", "Your insights"), style = MaterialTheme.typography.titleLarge)
                Text(L("Muskeln, Kraft, Gewohnheiten", "Muscles, strength, habits"), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink.faint)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeadCell(L("${h.streak} Wo.", "${h.streak} wk"), L("Serie", "Streak"), Ink.coral, Modifier.weight(1f))
            HeadCell(if (h.rate.of > 0) "${h.rate.pct} %" else "–", L("Ziel erreicht", "Goal met"), Ink.text, Modifier.weight(1f))
            HeadCell(
                h.volume?.let { (if (it >= 0) "+" else "−") + "${kotlin.math.abs(it).roundToInt()} %" } ?: "–",
                L("Volumen 4 Wo.", "Volume 4 wk"), if ((h.volume ?: 0.0) >= 0) Ink.accent else Ink.muted, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeadCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RowShape).background(Ink.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Everything the history says about training, one card per topic with a chart and a takeaway. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InsightsScreen(data: AppData, nav: NavController) {
    val today by rememberToday()
    // One pass over the whole history; only redone when the data behind it changes.
    val r = remember(data.history, data.food, data.water, data.bodyWeights, data.settings.weeklyGoal, data.frozenWeeks, data.profile.goal) {
        Insights.report(data, lookup, today)
    }
    val twoColumns = LocalWidth.current.expanded
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 16.dp)) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
            }
        }
        item { ScreenTitle(L("Was deine Daten über dein Training sagen", "What your data says about your training"), L("Analysen", "Insights")) }
        // Two columns of cards on expanded windows.
        cards(twoColumns, 12.dp, key = "cards") {
            item(key = "consistency") { ConsistencyCard(r) }
            item(key = "volume") { VolumeCard(r) }
            item(key = "strength") { StrengthCard(r, nav) }
            item(key = "sessions") { SessionsCard(r) }
            item(key = "records") { RecordsCard(r, today, nav) }
            r.food?.let { f -> item(key = "food") { FoodCard(f) } }
            if (r.weight != null || r.readiness != null) item(key = "body") { BodyCard(r) }
            item(key = "effort") { EffortCard(r.effort) }
        }
    }
}

/** A section: title, the content, then the takeaway in plain words. */
@Composable
private fun Section(title: String, takeaway: String?, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(title)
        Spacer(Modifier.height(14.dp))
        content()
        if (!takeaway.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(takeaway, style = MaterialTheme.typography.bodyMedium, color = Ink.text)
        }
    }
}

/** Friendly placeholder: what is missing and what fills it. */
@Composable
private fun NotEnough(need: String) {
    Text(L("Noch zu wenig Daten", "Not enough data yet"), style = MaterialTheme.typography.titleMedium)
    Text(need, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink.text) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- 1. Consistency -------------------------------------------------------------------------------

@Composable
private fun ConsistencyCard(r: Insights.Report) {
    Section(L("Regelmäßigkeit · 16 Wochen", "Consistency · 16 weeks"), if (r.calendar.any { it.level > 0 }) Insights.consistencyLine(r) else null) {
        if (r.calendar.none { it.level > 0 }) {
            NotEnough(L("Nach deinem ersten Training füllt sich hier der Kalender.", "Your calendar fills up here after your first workout."))
            return@Section
        }
        Row {
            // Weekday initials next to the rows: Monday, Wednesday, Friday, Sunday.
            Column(Modifier.padding(end = 6.dp).height(CAL_HEIGHT), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(L("Mo", "Mon"), "", L("Mi", "Wed"), "", L("Fr", "Fri"), "", L("So", "Sun")).forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Ink.faint, maxLines = 1)
                }
            }
            CalendarHeatmap(r.calendar, Modifier.weight(1f).height(CAL_HEIGHT))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Weniger", "Less"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
            (0..4).forEach { l -> Box(Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RowShape).background(heatColor(l))) }
            Text(L("Mehr Volumen", "More volume"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Figure(L("${r.streaks.current} Wo.", "${r.streaks.current} wk"), L("Aktuelle Serie", "Current streak"), Modifier.weight(1f), Ink.coral)
            Figure(L("${r.streaks.best} Wo.", "${r.streaks.best} wk"), L("Beste Serie", "Best streak"), Modifier.weight(1f))
            Figure("${r.goalRate.pct} %", L("Ziel ${r.goal}× erreicht", "Goal ${r.goal}× met"), Modifier.weight(1f), Ink.accent)
        }
    }
}

private val CAL_HEIGHT = 126.dp

private fun heatColor(level: Int): Color = when (level) {
    0 -> Ink.raised
    1 -> Ink.accent.copy(alpha = 0.3f)
    2 -> Ink.accent.copy(alpha = 0.52f)
    3 -> Ink.accent.copy(alpha = 0.76f)
    else -> Ink.accent
}

/** GitHub-style grid: one column per week, Monday at the top; days still to come stay blank. */
@Composable
private fun CalendarHeatmap(days: List<Insights.Day>, modifier: Modifier = Modifier) {
    val colors = (0..4).map(::heatColor)
    val today = Ink.text
    Canvas(modifier) {
        val weeks = days.size / 7
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
        val radius = CornerRadius(cell * 0.28f)
        days.forEachIndexed { i, d ->
            if (d.level < 0) return@forEachIndexed
            val tl = Offset((i / 7) * (cell + gap), (i % 7) * (cell + gap))
            drawRoundRect(colors[d.level], tl, Size(cell, cell), radius)
            // The last filled day is today: ringed so the grid reads as "up to now".
            if (days.getOrNull(i + 1)?.level?.let { it < 0 } != false) drawRoundRect(today, tl, Size(cell, cell), radius, style = Stroke(1.5.dp.toPx()))
        }
    }
}

// --- 2. Sets per muscle group ---------------------------------------------------------------------

@Composable
private fun VolumeCard(r: Insights.Report) {
    val any = r.weekly.values.any { w -> w.any { it > 0 } }
    Section(L("Sätze pro Muskelgruppe · 7 Tage", "Sets per muscle group · 7 days"), if (any) Insights.volumeLine(r.lastWeek) else null) {
        if (!any) {
            NotEnough(L("Trainiere ein paar Wochen, dann siehst du hier, welche Muskeln genug Sätze bekommen.", "Train for a few weeks to see which muscles get enough sets."))
            return@Section
        }
        Text(
            L("Harte Sätze ohne Aufwärmen, Nebenmuskeln zählen halb. Schattiert: 10 bis 20 Sätze, der Bereich für Muskelaufbau.",
                "Hard sets without warm-ups, secondary muscles count half. Shaded: 10 to 20 sets, the range for muscle growth."),
            style = MaterialTheme.typography.bodySmall, color = Ink.muted,
        )
        Spacer(Modifier.height(12.dp))
        val scale = maxOf(25.0, r.lastWeek.values.maxOrNull() ?: 0.0)
        Insights.MuscleGroup.entries.forEach { g ->
            val n = r.lastWeek[g] ?: 0.0
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.label, Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RangeBar(n, scale, Modifier.weight(1f).height(14.dp))
                Text(Stats.fmtKg(n), Modifier.width(36.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = bandColor(n))
                Spacer(Modifier.width(8.dp))
                Sparkline(r.weekly[g].orEmpty(), Modifier.width(48.dp).height(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(L("Rechts: Verlauf über 12 Wochen", "Right: trend over 12 weeks"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
    }
}

private fun bandColor(sets: Double) = when (Insights.band(sets)) {
    Insights.Band.Under -> Ink.muted
    Insights.Band.Good -> Ink.accent
    Insights.Band.High -> Ink.coral
}

/** Horizontal bar on a 0..[scale] axis with the 10 to 20 set band shaded behind it. */
@Composable
private fun RangeBar(value: Double, scale: Double, modifier: Modifier = Modifier) {
    val track = Ink.raised
    val band = Ink.accent.copy(alpha = 0.18f)
    val fill = bandColor(value)
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        fun x(v: Double) = (v / scale).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(track, cornerRadius = r)
        drawRect(band, Offset(x(Insights.SETS_LOW), 0f), Size(x(Insights.SETS_HIGH) - x(Insights.SETS_LOW), size.height))
        val w = x(value)
        if (w > 0f) drawRoundRect(fill, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/** Tiny trend line, oldest first, scaled from zero to its own maximum. */
@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val color = Ink.muted
    val dot = Ink.accent
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val pts = values.mapIndexed { i, v ->
            Offset(i * size.width / (values.size - 1), size.height - (v / max).toFloat() * size.height)
        }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(dot, 2.5.dp.toPx(), pts.last())
    }
}

// --- 3. Strength ----------------------------------------------------------------------------------

@Composable
private fun StrengthCard(r: Insights.Report, nav: NavController) {
    Section(L("Krafttrends · geschätztes 1RM", "Strength trends · estimated 1RM"), r.lifts.takeIf { it.isNotEmpty() }?.let { Insights.strengthLine(it, lookup) }) {
        if (r.lifts.isEmpty()) {
            NotEnough(L("Mach eine Übung mit Gewicht in mindestens zwei Trainings, dann erscheint hier ihr Verlauf.", "Do a weighted exercise in at least two workouts to see its trend here."))
            return@Section
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.lifts.forEach { lift -> LiftRow(lift) { nav.navigate("exercise/${lift.exerciseId}") } }
        }
    }
}

@Composable
private fun LiftRow(lift: Insights.Lift, onClick: () -> Unit) {
    val ex = App.repo[lift.exerciseId]
    Card(Modifier.fillMaxWidth(), onClick = onClick, color = Ink.raised, shape = RowShape, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex?.title ?: lift.exerciseId, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    L("Bestwert ~${Stats.fmtKg(Stats.roundTo(lift.best, 0.5))} kg · ${lift.points.size} Trainings", "Best ~${Stats.fmtKg(Stats.roundTo(lift.best, 0.5))} kg · ${lift.points.size} sessions"),
                    style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val c = lift.change
                Text(
                    c?.let { (if (it >= 0) "+" else "−") + "${kotlin.math.abs(it).roundToInt()} %" } ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                    color = when { c == null -> Ink.faint; c >= 0.5 -> Ink.accent; c <= -0.5 -> Ink.coral; else -> Ink.text },
                )
                Text(L("8 Wochen", "8 weeks"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
            }
        }
        Spacer(Modifier.height(10.dp))
        LineChart(lift.points.map { it.date.toEpochDay().toFloat() to it.e1rm.toFloat() }, Modifier.fillMaxWidth().height(64.dp))
        if (lift.plateau) {
            Spacer(Modifier.height(8.dp))
            Tag(L("Plateau seit ${lift.weeksSinceBest} Wochen", "Plateau for ${lift.weeksSinceBest} weeks"), Ink.coral)
        }
    }
}

/**
 * Line over (x, y) points, both axes scaled to the data; an optional [overlay] (same scale) is
 * drawn on top in the accent while the main line steps back to a quiet tone.
 */
@Composable
private fun LineChart(points: List<Pair<Float, Float>>, modifier: Modifier = Modifier, overlay: List<Pair<Float, Float>>? = null) {
    val accent = Ink.accent
    val quiet = Ink.lineStrong
    val grid = Ink.line
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val all = points + overlay.orEmpty()
        val minX = all.minOf { it.first }
        val spanX = (all.maxOf { it.first } - minX).takeIf { it > 0f } ?: 1f
        val minY = all.minOf { it.second }
        val spanY = (all.maxOf { it.second } - minY).takeIf { it > 0f } ?: 1f
        val pad = 5.dp.toPx()
        fun at(p: Pair<Float, Float>) = Offset(
            pad + (p.first - minX) / spanX * (size.width - 2 * pad),
            pad + (1 - (p.second - minY) / spanY) * (size.height - 2 * pad),
        )
        drawLine(grid, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        fun line(pts: List<Offset>) = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val main = points.map(::at)
        if (overlay == null) {
            val area = Path().apply {
                addPath(line(main)); lineTo(main.last().x, size.height); lineTo(main.first().x, size.height); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0f))))
            drawPath(line(main), accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            main.forEach { drawCircle(accent, 2.dp.toPx(), it) }
            drawCircle(accent, 4.dp.toPx(), main.last())
        } else {
            main.forEach { drawCircle(quiet, 2.5.dp.toPx(), it) }
            val avg = overlay.map(::at)
            if (avg.size >= 2) drawPath(line(avg), accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent, 4.dp.toPx(), avg.last())
        }
    }
}

// --- 4. Sessions ----------------------------------------------------------------------------------

@Composable
private fun SessionsCard(r: Insights.Report) {
    val s = r.sessions
    Section(L("Einheiten · 12 Wochen", "Sessions · 12 weeks"), if (s.count > 0) Insights.sessionLine(s, r.volumeChange) else null) {
        if (s.count == 0) {
            NotEnough(L("In den letzten 12 Wochen gab es kein Training. Das nächste zählt hier sofort.", "No workouts in the last 12 weeks. Your next one shows up here right away."))
            return@Section
        }
        Row {
            Figure(s.avgMinutes?.let { "$it min" } ?: "–", L("Dauer im Schnitt", "Average length"), Modifier.weight(1f))
            Figure(Stats.fmtKg(Math.round(s.avgSets * 10) / 10.0), L("Sätze pro Einheit", "Sets per session"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(L("Trainings pro Woche", "Workouts per week"), style = MaterialTheme.typography.labelMedium, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        Bars(s.perWeek.map { it.second.toFloat() }, s.perWeek.map { "${it.first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" }, Modifier.height(110.dp))
    }
}

// --- 5. Records -----------------------------------------------------------------------------------

@Composable
private fun RecordsCard(r: Insights.Report, today: LocalDate, nav: NavController) {
    Section(L("Rekorde · zuletzt", "Records · latest"), r.prs.takeIf { it.isNotEmpty() }?.let { Insights.recordsLine(it, today) }) {
        if (r.prs.isEmpty()) {
            NotEnough(L("Ein Rekord zählt, sobald du eine Übung besser machst als beim ersten Mal.", "A record counts once you beat the first time you did an exercise."))
            return@Section
        }
        val dot = Ink.accent
        val rail = Ink.line
        r.prs.forEachIndexed { i, pr ->
            val ex = App.repo[pr.exerciseId]
            Row(
                Modifier.fillMaxWidth().clip(RowShape).pressable { Haptics.click(); nav.navigate("exercise/${pr.exerciseId}") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Timeline rail: a dot per record, joined by a thin line.
                Canvas(Modifier.width(18.dp).height(48.dp)) {
                    val cx = size.width / 2
                    if (i > 0) drawLine(rail, Offset(cx, 0f), Offset(cx, size.height / 2), 2.dp.toPx())
                    if (i < r.prs.lastIndex) drawLine(rail, Offset(cx, size.height / 2), Offset(cx, size.height), 2.dp.toPx())
                    drawCircle(dot, 5.dp.toPx(), Offset(cx, size.height / 2))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex?.title ?: pr.exerciseId, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pr.date.format(shortFmt), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Stats.setLabel(pr.set, pr.set.weight <= 0), style = MaterialTheme.typography.titleSmall)
                    pr.gain?.let { Text("+${Stats.fmtKg(Math.round(it * 10) / 10.0)} %", style = MaterialTheme.typography.labelSmall, color = Ink.accent) }
                }
            }
        }
    }
}

// --- 6. Nutrition ---------------------------------------------------------------------------------

@Composable
private fun FoodCard(f: Insights.Food) {
    Section(L("Ernährung · 14 Tage", "Nutrition · 14 days"), Insights.foodLine(f)) {
        Row {
            val t = f.targets
            Figure(if (t != null) "${f.proteinHit}/${f.logged}" else "${f.avgProtein} g", if (t != null) L("Tage Proteinziel", "Days protein hit") else L("Protein im Schnitt", "Average protein"), Modifier.weight(1f), Ink.accent)
            Figure("${f.avgKcal}", if (t != null) L("kcal von ${t.kcal}", "kcal of ${t.kcal}") else L("kcal im Schnitt", "kcal average"), Modifier.weight(1f))
            Figure(f.avgWater?.let { Stats.fmtKg(Math.round(it / 100.0) / 10.0) + " l" } ?: "–", L("Wasser pro Tag", "Water per day"), Modifier.weight(1f), Ink.mauve)
        }
        Spacer(Modifier.height(14.dp))
        Text(L("Protein pro Tag", "Protein per day") + (f.targets?.let { L(" · Ziel ${it.protein} g", " · target ${it.protein} g") }.orEmpty()),
            style = MaterialTheme.typography.labelMedium, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        ProteinChart(f.protein, f.targets?.protein, Modifier.fillMaxWidth().height(72.dp))
        f.targets?.let { t ->
            Spacer(Modifier.height(12.dp))
            Text(L("Kalorien im Schnitt", "Average calories"), style = MaterialTheme.typography.labelMedium, color = Ink.muted)
            Spacer(Modifier.height(6.dp))
            Meter(f.avgKcal / t.kcal.toFloat().coerceAtLeast(1f), Modifier.fillMaxWidth(), if (f.avgKcal > t.kcal * 1.1) Ink.coral else Ink.accent)
        }
    }
}

/** Daily protein as bars; with a target, days that hit it are in the accent and the target is a dashed line. */
@Composable
private fun ProteinChart(values: List<Int>, target: Int?, modifier: Modifier = Modifier) {
    val hit = Ink.accent
    val miss = Ink.lineStrong
    val empty = Ink.raised
    val line = Ink.text.copy(alpha = 0.55f)
    Canvas(modifier) {
        val top = maxOf(values.maxOrNull() ?: 0, target ?: 0).coerceAtLeast(1) * 1.1f
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 3.dp.toPx() else v / top * size.height
            val color = when { v == 0 -> empty; target == null || v >= target -> hit; else -> miss }
            drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
        }
        target?.let {
            val y = size.height - it / top * size.height
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
    }
}

// --- 7. Body --------------------------------------------------------------------------------------

@Composable
private fun BodyCard(r: Insights.Report) {
    Section(L("Körper", "Body"), r.weight?.let(Insights::weightLine)) {
        r.weight?.let { w ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${Stats.fmtKg(Math.round(w.average.last().second * 10) / 10.0)} kg", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(8.dp))
                Text(L("7-Tage-Schnitt", "7-day average"), color = Ink.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            LineChart(
                w.points.map { (it.first / Stats.DAY_MS).toFloat() to it.second.toFloat() },
                Modifier.fillMaxWidth().height(96.dp),
                overlay = w.average.map { (it.first / Stats.DAY_MS).toFloat() to it.second.toFloat() },
            )
            Spacer(Modifier.height(6.dp))
            Text(L("Punkte: Messungen · Linie: 7-Tage-Schnitt", "Dots: weigh-ins · line: 7-day average"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
        }
        r.readiness?.let { link ->
            if (r.weight != null) Spacer(Modifier.height(14.dp))
            Text(L("Erholung und Volumen", "Recovery and volume"), style = MaterialTheme.typography.labelMedium, color = Ink.muted)
            Spacer(Modifier.height(6.dp))
            // Correlation from -1 to 1 as a marker on a centred scale.
            val marker = Ink.mauve
            val track = Ink.raised
            val mid = Ink.lineStrong
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                drawLine(mid, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                drawCircle(marker, size.height / 2, Offset(((link.toFloat() + 1) / 2) * size.width, size.height / 2))
            }
            Spacer(Modifier.height(6.dp))
            Text(Insights.readinessLine(link), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- 8. Effort ------------------------------------------------------------------------------------

@Composable
private fun EffortCard(e: Insights.Effort) {
    Section(L("Anstrengung · 12 Wochen", "Effort · 12 weeks"), if (e.sets > 0) Insights.effortLine(e) else null) {
        if (e.sets == 0) {
            NotEnough(L("Hier erscheint, wie hart deine Sätze sind, sobald du in den letzten 12 Wochen trainiert hast.", "How hard your sets are shows up here once you have trained in the last 12 weeks."))
            return@Section
        }
        Row {
            Figure("${(e.share(e.rated) * 100).roundToInt()} %", L("Sätze mit RPE", "Sets with RPE"), Modifier.weight(1f))
            Figure(e.avgRpe?.let { Stats.fmtKg(Math.round(it * 10) / 10.0) } ?: "–", L("RPE im Schnitt", "Average RPE"), Modifier.weight(1f), Ink.accent)
        }
        Spacer(Modifier.height(14.dp))
        // One bar for all working sets, split into normal, drop and to-failure sets.
        val normal = Ink.raised
        val drop = Ink.mauve
        val fail = Ink.coral
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(Pill)) {
            val f = e.share(e.failure) * size.width
            val d = e.share(e.drop) * size.width
            drawRect(normal)
            drawRect(drop, Offset(size.width - f - d, 0f), Size(d, size.height))
            drawRect(fail, Offset(size.width - f, 0f), Size(f, size.height))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(normal, L("Normal", "Normal"))
            Legend(drop, L("Drop ${(e.share(e.drop) * 100).roundToInt()} %", "Drop ${(e.share(e.drop) * 100).roundToInt()} %"))
            Legend(fail, L("Versagen ${(e.share(e.failure) * 100).roundToInt()} %", "Failure ${(e.share(e.failure) * 100).roundToInt()} %"))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(Pill).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1)
    }
}
