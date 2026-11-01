package dev.mauve.fit.ui

import androidx.compose.runtime.getValue

import androidx.compose.runtime.collectAsState
import dev.mauve.fit.system.Recovery
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Labels
import dev.mauve.fit.data.Nutrition
import dev.mauve.fit.data.Progress
import dev.mauve.fit.data.Routine
import dev.mauve.fit.data.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L

private val dateFmt get() = DateTimeFormatter.ofPattern(L("EEEE, d. MMMM", "EEEE, MMMM d"), I18n.locale)

@Composable
fun HomeScreen(data: AppData, nav: NavController) {
    val history = data.history
    val goal = data.settings.weeklyGoal
    val thisWeek = Stats.workoutsThisWeek(history)
    val volume = Stats.weeklyVolume(history)
    val next = Stats.nextRoutine(data.routines, history, App.repo)
    val recovery by Recovery.today.collectAsState()
    val tip = remember(data.history, data.settings, data.routines) { Coach.homeTip(data, App.repo) }

    fun start(r: Routine?) {
        App.store.startWorkout(r)
        nav.navigate("workout")
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenTitle(LocalDate.now().format(dateFmt), L("Heute", "Today"), Modifier.weight(1f))
                RoundButton(Icons.Rounded.Settings, L("Einstellungen", "Settings"), size = 44.dp) { nav.navigate("settings") }
            }
        }

        item { WeekStrip(Stats.weekDays(history)) }

        // A running workout always comes first.
        data.active?.let { w ->
            item {
                Card(onClick = { nav.navigate("workout") }, color = Ink.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(L("Training läuft", "Workout in progress"), color = Ink.bg)
                            Text(w.name, style = MaterialTheme.typography.headlineSmall, color = Ink.bg)
                            Text(L("${Stats.sets(w.doneSets.size)} erledigt", "${Stats.sets(w.doneSets.size)} done"), color = Ink.bg.copy(alpha = 0.7f))
                        }
                        Box(Modifier.size(52.dp).clip(Pill).background(Ink.bg), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, L("Fortsetzen", "Resume"), tint = Ink.accent)
                        }
                    }
                }
            }
        }

        if (data.active == null && tip != null) item {
            CoachCard(tip) { action ->
                when (action) {
                    is Coach.Action.StartDeload -> App.store.startDeload()
                    is Coach.Action.EndDeload -> App.store.endDeload()
                    is Coach.Action.StartBlock -> App.store.startBlock(data.settings.blockWeeks)
                    is Coach.Action.AddExercise -> App.store.addToRoutine(action.routineId, action.exerciseId)
                }
            }
        }

        recovery?.let { r -> item { ReadinessCard(r, onShort = next?.let { n -> { App.store.startWorkout(n, short = true); nav.navigate("workout") } }.takeIf { data.active == null && r.score < 50 }) } }
        Coach.blockWeek(data)?.let { b -> item { BlockCard(b) } }

        if (data.active == null && next != null) item {
            NextWorkoutCard(next, onShort = {
                App.store.startWorkout(next, short = true)
                nav.navigate("workout")
            }) { start(next) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("$thisWeek/$goal", L("diese Woche", "this week"), Modifier.weight(1f), if (thisWeek >= goal) Ink.accent else Ink.text)
                Stat("${Stats.weekStreak(history, goal)}", L("Serie", "streak"), Modifier.weight(1f), Ink.coral)
                Stat(Stats.fmtVolume(volume.last().second), L("Volumen", "volume"), Modifier.weight(1f))
            }
        }

        if (data.active == null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutTile(L("Schnelltraining", "Quick workout"), L("Coach baut es", "Coach builds it"), Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { nav.navigate("quick") }
                ShortcutTile(L("Freies Training", "Free workout"), L("Ohne Plan", "No plan"), Icons.Rounded.PlayArrow, Modifier.weight(1f)) { start(null) }
            }
        }

        if (history.isNotEmpty()) item { RecoveryCard(Progress.recovery(history, App.repo)) }

    }
    }
}

/** Monday to Sunday; trained days filled, today ringed. */
@Composable
private fun WeekStrip(trained: Set<LocalDate>) {
    val today = LocalDate.now()
    val monday = Stats.weekStart(today)
    val names = if (I18n.german) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        names.forEachIndexed { i, n ->
            val d = monday.plusDays(i.toLong())
            val done = d in trained
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(n, style = MaterialTheme.typography.labelMedium, color = if (d == today) Ink.text else Ink.muted)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(40.dp).clip(Pill)
                        .background(if (done) Ink.accent else Ink.surface)
                        .then(if (d == today && !done) Modifier.border(2.dp, Ink.accent, Pill) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(20.dp))
                    else Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (d.isAfter(today)) Ink.faint else Ink.muted)
                }
            }
        }
    }
}

@Composable
private fun NextWorkoutCard(r: Routine, onShort: () -> Unit, onStart: () -> Unit) {
    val hero = App.repo[r.exercises.firstOrNull()?.exerciseId.orEmpty()]
    Card(padding = PaddingValues(0.dp), shape = Hero) {
        Box {
            ExerciseMedia(hero, Modifier.fillMaxWidth().height(mediaStageHeight()), corner = 0.dp)
            Box(
                Modifier.padding(16.dp).clip(Pill).background(Ink.accent).padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text(L("Als Nächstes", "Up next"), style = MaterialTheme.typography.labelLarge, color = Ink.bg) }
        }
        Column(Modifier.padding(22.dp)) {
            Text(r.name, style = MaterialTheme.typography.displaySmall)
            if (r.note.isNotBlank()) Text(r.note, color = Ink.muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(18.dp))
            // The session at a glance, scoreboard-style.
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Figure("${r.exercises.size}", L("Übungen", "exercises"))
                Figure("${r.exercises.sumOf { it.sets }}", L("Sätze", "sets"))
                Figure("${estMinutes(r)}", L("Minuten", "minutes"))
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(L("Training starten", "Start workout"), Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow, onStart)
            Spacer(Modifier.height(8.dp))
            // For days with little time: the coach trims the plan.
            val short = Coach.shortVersion(r)
            GhostButton(L("Kurzversion, ${estMinutes(short)} min", "Quick version, ${estMinutes(short)} min"),
                Modifier.fillMaxWidth(), onClick = onShort)
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = Ink.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
    }
}

/** Six muscle groups as rings: full means recovered, the coach uses the same numbers. */
@Composable
private fun RecoveryCard(recovery: Map<String, Float>) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(L("Erholung", "Recovery"), Modifier.weight(1f))
            val ready = recovery.count { it.value >= 0.9f }
            Text(L("$ready von ${recovery.size} bereit", "$ready of ${recovery.size} ready"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            recovery.forEach { (group, r) ->
                val color = when {
                    r >= 0.9f -> Ink.accent
                    r >= 0.5f -> Ink.mauve
                    else -> Ink.coral
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Ring(r, Modifier.fillMaxSize(), stroke = 5.dp, color = color)
                        Text("${(r * 100).toInt()}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(group, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                }
            }
        }
    }
}

/** Rough duration: ~40 s per set plus the planned rest. */
fun estMinutes(r: Routine): Int = (r.exercises.sumOf { it.sets * (40 + it.restSec) } / 60).coerceAtLeast(1)

/** Where the running block stands: one segment per week, the current phase spelled out. */
@Composable
fun BlockCard(b: Coach.BlockWeek, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow(L("Trainingsblock", "Training block"))
                Text(b.phase.label, style = MaterialTheme.typography.headlineSmall)
            }
            Text(L("Woche ${b.week} von ${b.weeks}", "Week ${b.week} of ${b.weeks}"), color = Ink.muted, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Coach.phases(b.weeks).forEachIndexed { i, ph ->
                val color = when {
                    i + 1 < b.week -> Ink.accent.copy(alpha = 0.45f)
                    i + 1 == b.week -> Ink.accent
                    ph == Coach.Phase.Deload -> Ink.mauve.copy(alpha = 0.25f)
                    else -> Ink.raised
                }
                Box(Modifier.weight(1f).height(8.dp).clip(Pill).background(color))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(b.phase.blurb, color = Ink.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Today's readiness from the watch: score ring, the three inputs, one line of advice. */
@Composable
private fun ReadinessCard(r: Recovery.Today, onShort: (() -> Unit)?) {
    val color = when { r.score >= 75 -> Ink.accent; r.score >= 50 -> Ink.mauve; else -> Ink.coral }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Ring(r.score / 100f, Modifier.fillMaxSize(), stroke = 7.dp, color = color)
                Text("${r.score}", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(L("Erholung heute", "Readiness today"))
                Text(r.advice, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(r.sleepMinutes?.let { "${it / 60}:%02d".format(it % 60) } ?: "–", L("Schlaf", "Sleep"), null, Modifier.weight(1f))
            Metric(r.restingHr?.let { "$it" } ?: "–", L("Ruhepuls", "Resting HR"), r.restingBase?.let { b -> r.restingHr?.let { it - b } }, Modifier.weight(1f), lowerIsBetter = true)
            Metric(r.hrv?.let { "$it ms" } ?: "–", "HRV", r.hrvBase?.let { b -> r.hrv?.let { it - b } }, Modifier.weight(1f))
        }
        if (onShort != null) {
            Spacer(Modifier.height(14.dp))
            GhostButton(L("Kurzversion starten", "Start the short version"), Modifier.fillMaxWidth(), onClick = onShort)
        }
    }
}

@Composable
private fun Metric(value: String, label: String, delta: Int?, modifier: Modifier, lowerIsBetter: Boolean = false) {
    Column(modifier.clip(RowShape).background(Ink.raised).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, color = Ink.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        if (delta != null && delta != 0) {
            val good = if (lowerIsBetter) delta < 0 else delta > 0
            Text((if (delta > 0) "+" else "") + delta + L(" zur Basis", " vs base"), color = if (good) Ink.accent else Ink.coral, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
