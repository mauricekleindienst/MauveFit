package dev.mauve.fit.ui

import dev.mauve.fit.data.WeeklyReport
import dev.mauve.fit.system.Haptics

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import dev.mauve.fit.data.Motivation
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
    val cycle by App.store.cycle.collectAsState()
    // Keyed by the week too: kept in memory over Sunday night they would show last week's.
    val weekKey = Stats.weekKey(java.time.LocalDate.now())
    val tip = remember(data.history, data.settings, data.routines, data.cardio, weekKey) { Coach.homeTip(data, App.repo) }
    val challenge = remember(data.history, data.settings, data.profile, weekKey) { Motivation.challenge(data, App.repo) }
    val streak = Stats.weekStreak(history, goal, data.frozenWeeks)
    val twoColumns = LocalWidth.current.expanded

    // A new week may have started while the app stayed open: book the one that ended.
    LaunchedEffect(Stats.weekKey(LocalDate.now())) { App.store.settleStreak() }
    // Completed outside the summary (e.g. a goal change): count it and celebrate once here.
    LaunchedEffect(challenge.week, challenge.done) {
        if (challenge.done && App.store.completeChallenge(challenge.week)) Haptics.success()
    }

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

        // On expanded windows the cards below pack into two columns; on phones each is its own row.
        cards(twoColumns, 14.dp, key = "cards") {

            // A running workout always comes first.
            data.active?.let { w ->
                // Open for hours: most likely forgotten, so offer to save or drop it right here.
                if (System.currentTimeMillis() - w.startedAt > 4 * 60 * 60_000L) item {
                    Card(Modifier.fillMaxWidth()) {
                        Eyebrow(L("Training noch offen", "Workout still open"), color = Ink.coral)
                        Text(
                            L("„${w.name}“ läuft seit ${Stats.fmtDuration(System.currentTimeMillis() - w.startedAt)}. Speichern, was erledigt ist, oder verwerfen?",
                                "\"${w.name}\" has been running for ${Stats.fmtDuration(System.currentTimeMillis() - w.startedAt)}. Save what is done, or discard it?"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(L("Speichern", "Save"), Modifier.weight(1f)) {
                                Haptics.success()
                                // The real end is unknown; about three minutes per set done keeps the stats honest.
                                val minutes = (w.doneSets.size * 3).coerceIn(10, 120)
                                App.store.finishWorkout(w.startedAt + minutes * 60_000L)?.let { nav.navigate("summary/$it") }
                            }
                            GhostButton(L("Verwerfen", "Discard"), Modifier.weight(1f)) { Haptics.warn(); App.store.discardWorkout() }
                        }
                    }
                }
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
                        is Coach.Action.AddExercise -> App.store.acceptAddTip(action.routineId, action.exerciseId)
                    }
                }
            }

            recovery?.let { r -> item { ReadinessCard(r, onShort = next?.let { n -> { App.store.startWorkout(n, short = true); nav.navigate("workout") } }.takeIf { data.active == null && r.score < 50 }) } }
            if (cycle.on) item { CycleCard(cycle) { nav.navigate("settings") } }
            Coach.blockWeek(data)?.let { b -> item { BlockCard(b) } }

            if (data.active == null && next != null) item {
                NextWorkoutCard(next, onShort = {
                    App.store.startWorkout(next, short = true)
                    nav.navigate("workout")
                }) { start(next) }
            }

            item {
                // Same height for all three, even when the streak carries its freeze line.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("$thisWeek/$goal", L("diese Woche", "this week"), Modifier.weight(1f).fillMaxHeight(), if (thisWeek >= goal) Ink.accent else Ink.text)
                    StreakStat(streak, data.freezes, Modifier.weight(1f).fillMaxHeight())
                    // A week of push-ups and pull-ups is reps, not "0 kg".
                    val weekReps = history.filter { Stats.weekStart(Stats.day(it.startedAt)) == Stats.weekStart(java.time.LocalDate.now()) }
                        .sumOf { w -> w.doneSets.sumOf { it.second.reps } }
                    if (volume.last().second <= 0 && weekReps > 0) Stat("$weekReps", L("Wiederholungen", "reps"), Modifier.weight(1f).fillMaxHeight())
                    else Stat(Stats.fmtVolume(volume.last().second), L("Volumen", "volume"), Modifier.weight(1f).fillMaxHeight())
                }
            }

            // Monday and Tuesday: how last week went, before the new one takes over.
            if (LocalDate.now().dayOfWeek.value <= 2 && data.history.isNotEmpty()) item {
                ReportCard(remember(data.history, data.cardio, data.settings.weeklyGoal) { WeeklyReport.of(data, WeeklyReport.lastWeekStart(), App.repo::get) })
            }
            item { ChallengeCard(challenge, data.challengesDone.size) }

            if (data.active == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutTile(L("Schnelltraining", "Quick workout"), L("Coach baut es", "Coach builds it"), Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { nav.navigate("quick") }
                    ShortcutTile(L("Freies Training", "Free workout"), L("Ohne Plan", "No plan"), Icons.Rounded.PlayArrow, Modifier.weight(1f)) { start(null) }
                }
            }

            if (history.isNotEmpty()) item { RecoveryCard(Coach.withCardio(Progress.recovery(history, App.repo), data.cardio)) }
        }

    }
    }
}

/** The week streak like [Stat], with the freezes in stock below it. */
@Composable
private fun StreakStat(streak: Int, freezes: Int, modifier: Modifier) {
    Card(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Text("$streak", style = MaterialTheme.typography.headlineMedium, color = Ink.coral, maxLines = 1, softWrap = false)
        Text(L("Serie", "streak"), style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1)
        if (freezes > 0) Text(Motivation.freezeLabel(freezes), style = MaterialTheme.typography.labelSmall, color = Ink.accent, maxLines = 1)
    }
}

/** This week's challenge: what to do, how far along, and a word of praise once it is done. */
@Composable
fun ChallengeCard(c: Motivation.Challenge, completed: Int, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(L("Wochen-Challenge", "Weekly challenge"), Modifier.weight(1f), color = if (c.done) Ink.accent else Ink.muted)
            if (c.done) Tag(L("Geschafft", "Done"), Ink.accent) else Text(c.label, style = MaterialTheme.typography.labelLarge, color = Ink.muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Meter(c.fraction, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            if (c.done) L("Stark. Das ist deine ${completed.coerceAtLeast(1)}. geschaffte Challenge.", "Nice work. That makes ${completed.coerceAtLeast(1)} challenges done.")
            else c.detail,
            style = MaterialTheme.typography.bodySmall, color = if (c.done) Ink.text else Ink.muted,
        )
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
            PrimaryButton(L("Training starten", "Start workout"), Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow, onClick = onStart)
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

/** Last week at a glance: the summary line and four numbers against the week before. */
@Composable
private fun ReportCard(r: WeeklyReport.Report) {
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(L("Letzte Woche", "Last week"))
        Text(r.headline, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("${r.workouts}/${r.goal}", style = MaterialTheme.typography.headlineSmall, color = if (r.goalHit) Ink.accent else Ink.text)
                Text(L("Trainings", "workouts"), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            }
            Column {
                Text(Stats.fmtVolume(r.volume), style = MaterialTheme.typography.headlineSmall)
                Text(r.volumeChange?.let { (if (it >= 0) "+" else "") + "$it %" } ?: L("Volumen", "volume"),
                    style = MaterialTheme.typography.labelSmall, color = when { (r.volumeChange ?: 0) > 0 -> Ink.accent; (r.volumeChange ?: 0) < 0 -> Ink.coral; else -> Ink.muted })
            }
            Column {
                Text("${r.records}", style = MaterialTheme.typography.headlineSmall)
                Text(L("Rekorde", "records"), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            }
            if (r.cardioMinutes > 0) Column {
                Text("${r.cardioMinutes}", style = MaterialTheme.typography.headlineSmall)
                Text(L("min Cardio", "min cardio"), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            }
        }
        r.topMuscle?.let {
            Spacer(Modifier.height(10.dp))
            Text(L("Am meisten trainiert: ${Labels.muscle(it)}", "Trained most: ${Labels.muscle(it)}"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
