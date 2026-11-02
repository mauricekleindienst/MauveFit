package dev.mauve.fit.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Intervals
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import dev.mauve.fit.data.Strength
import dev.mauve.fit.system.Haptics
import kotlinx.coroutines.delay

/** Gym helpers that need no workout: interval timer, 1RM, plates and warm-up sets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolsScreen(data: AppData, nav: NavController) {
    val s = data.settings
    LazyColumn(
        Modifier.statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 16.dp)) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
            }
        }
        item { ScreenTitle(L("Timer und Rechner fürs Training", "Timer and calculators for training"), L("Werkzeuge", "Tools")) }
        item { IntervalCard(s.sound) }
        item { OneRmCard(s.weightStep) }
        item { PlateCard(s.barWeight) }
        item { WarmupCard(s.weightStep, s.barWeight) }
    }
}

/** Wide tile on the Training tab that opens the tools. */
@Composable
fun ToolsTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Timer, null, tint = Ink.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Werkzeuge", "Tools"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(L("Intervall-Timer, 1RM, Scheiben, Aufwärmen", "Interval timer, 1RM, plates, warm-up"),
                    style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 2)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink.faint)
        }
    }
}

@Composable
private fun ToolHeader(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
    }
    Spacer(Modifier.height(16.dp))
}

// --- Interval timer ------------------------------------------------------------------------------

private enum class IntervalMode(private val de: String, private val en: String) {
    Emom("EMOM", "EMOM"), Tabata("Tabata", "Tabata"), Custom("Eigene", "Custom");

    val label get() = L(de, en)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalCard(sound: Boolean) {
    var mode by rememberSaveable { mutableStateOf(IntervalMode.Emom) }
    var emomRounds by rememberSaveable { mutableIntStateOf(10) }
    var work by rememberSaveable { mutableIntStateOf(40) }
    var rest by rememberSaveable { mutableIntStateOf(20) }
    var rounds by rememberSaveable { mutableIntStateOf(8) }
    // Clock anchors: elapsed = banked + (now - startedAt) while running, so the countdown is
    // computed from real time and never drifts, however late a frame comes. 0 = not running.
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var banked by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val plan = when (mode) {
        IntervalMode.Emom -> Intervals.emom(emomRounds)
        IntervalMode.Tabata -> Intervals.tabata
        IntervalMode.Custom -> Intervals.Plan(work, rest, rounds)
    }
    val running = startedAt != 0L
    val elapsed = banked + if (running) (now - startedAt).coerceAtLeast(0) else 0
    val idle = !running && banked == 0L
    val tick = Intervals.at(plan, elapsed)
    val secLeft = Intervals.secondsLeft(tick)

    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    // The last phase ran out: stop on the finished state.
    LaunchedEffect(tick.phase, running) {
        if (running && tick.phase == Intervals.Phase.Done) { banked = plan.totalMs; startedAt = 0L }
    }

    // The screen stays on while the timer runs; the phone is usually lying on the floor.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    fun beep(kind: Int, ms: Int) { if (sound) runCatching { tone?.startTone(kind, ms) } }

    // Cues on every phase change: three pulses when work starts, one firm hit for rest.
    val cue = tick.phase to tick.round
    var lastCue by remember { mutableStateOf(cue) }
    LaunchedEffect(cue) {
        if (cue != lastCue && (running || tick.phase == Intervals.Phase.Done)) when (tick.phase) {
            Intervals.Phase.Work -> { Haptics.restOver(); beep(ToneGenerator.TONE_PROP_BEEP2, 300) }
            Intervals.Phase.Rest -> { Haptics.heavy(); beep(ToneGenerator.TONE_PROP_BEEP, 250) }
            Intervals.Phase.Done -> { Haptics.success(); beep(ToneGenerator.TONE_PROP_ACK, 500) }
        }
        lastCue = cue
    }
    // A light tick on the last three seconds of a phase.
    LaunchedEffect(secLeft) {
        if (running && tick.phase != Intervals.Phase.Done && secLeft in 1..3 && tick.phaseMs > 5_000) Haptics.tick()
    }

    Card(Modifier.fillMaxWidth()) {
        ToolHeader(Icons.Rounded.Timer, L("Intervall-Timer", "Interval timer"), L("EMOM, Tabata oder eigene Intervalle", "EMOM, Tabata or your own intervals"))

        if (idle) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntervalMode.entries.forEach { m -> Chip(m.label, m == mode) { mode = m } }
            }
            Spacer(Modifier.height(12.dp))
            when (mode) {
                IntervalMode.Emom -> Stepper(L("Runden zu je 1 Minute", "Rounds of 1 minute"), "$emomRounds", Modifier.fillMaxWidth(),
                    { emomRounds = (emomRounds - 1).coerceAtLeast(1) }) { emomRounds = (emomRounds + 1).coerceAtMost(60) }
                IntervalMode.Tabata -> Text(L("20 s Arbeit, 10 s Pause, 8 Runden", "20 s work, 10 s rest, 8 rounds"), color = Ink.muted)
                IntervalMode.Custom -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                    Stepper(L("Arbeit", "Work"), Intervals.fmt(work), Modifier.weight(1f),
                        { work = (work - 5).coerceAtLeast(5) }) { work = (work + 5).coerceAtMost(600) }
                    Stepper(L("Pause", "Rest"), Intervals.fmt(rest), Modifier.weight(1f),
                        { rest = (rest - 5).coerceAtLeast(0) }) { rest = (rest + 5).coerceAtMost(600) }
                    Stepper(L("Runden", "Rounds"), "$rounds", Modifier.weight(1f),
                        { rounds = (rounds - 1).coerceAtLeast(1) }) { rounds = (rounds + 1).coerceAtMost(99) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(L("Gesamt ${Intervals.fmt((plan.totalMs / 1000).toInt())}", "Total ${Intervals.fmt((plan.totalMs / 1000).toInt())}"),
                style = MaterialTheme.typography.bodySmall, color = Ink.faint)
        }

        val color = when {
            idle -> Ink.muted
            tick.phase == Intervals.Phase.Rest -> Ink.mauve
            else -> Ink.accent
        }
        val label = when {
            idle -> L("Bereit", "Ready")
            tick.phase == Intervals.Phase.Work -> L("Arbeit", "Work")
            tick.phase == Intervals.Phase.Rest -> L("Pause", "Rest")
            else -> L("Fertig", "Done")
        }
        val progress = if (idle || tick.phaseMs == 0L) 1f else tick.remainingMs.toFloat() / tick.phaseMs
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Ring(progress, Modifier.size(220.dp), stroke = 10.dp, color = color)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = color)
                Text(Intervals.fmt(secLeft), style = MaterialTheme.typography.displayMedium.copy(fontFamily = Mono), maxLines = 1)
                Text(L("Runde ${tick.round.coerceAtLeast(1)} von ${plan.rounds}", "Round ${tick.round.coerceAtLeast(1)} of ${plan.rounds}"),
                    style = MaterialTheme.typography.bodyMedium, color = Ink.muted)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (running) PrimaryButton(L("Pause", "Pause"), Modifier.weight(1f), Icons.Rounded.Pause) {
                banked = elapsed; startedAt = 0L
            } else {
                val done = tick.phase == Intervals.Phase.Done
                PrimaryButton(if (idle || done) L("Starten", "Start") else L("Weiter", "Resume"), Modifier.weight(1f), Icons.Rounded.PlayArrow) {
                    if (done) banked = 0L
                    now = SystemClock.elapsedRealtime()
                    startedAt = now
                }
            }
            RoundButton(Icons.Rounded.Replay, L("Zurücksetzen", "Reset"), size = 56.dp, enabled = !idle) {
                banked = 0L; startedAt = 0L
            }
        }
    }
}

// --- Calculators ---------------------------------------------------------------------------------

/** Accepts "62,5" as well as "62.5"; null for anything that is not a sensible load. */
private fun parseKg(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }

@Composable
private fun KgField(value: String, label: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) }, modifier,
        label = { Text(label) }, suffix = { Text("kg") }, singleLine = true, shape = RowShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun OneRmCard(step: Double) {
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableIntStateOf(5) }
    val kg = parseKg(weight)
    Card(Modifier.fillMaxWidth()) {
        ToolHeader(Icons.AutoMirrored.Rounded.TrendingUp, L("1RM-Rechner", "1RM calculator"), L("Maximalkraft aus einem schweren Satz schätzen", "Estimate your max from one hard set"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KgField(weight, L("Gewicht", "Weight"), Modifier.weight(1f)) { weight = it }
            Stepper(L("Wiederholungen", "Reps"), "$reps", Modifier.width(132.dp),
                { reps = (reps - 1).coerceAtLeast(1) }) { reps = (reps + 1).coerceAtMost(20) }
        }
        if (kg == null) {
            Spacer(Modifier.height(10.dp))
            Text(L("Trag Gewicht und Wiederholungen eines Satzes ein, den du sauber geschafft hast.", "Enter weight and reps of a set you completed with good form."),
                style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            return@Card
        }
        val oneRm = Strength.oneRm(kg, reps)
        Spacer(Modifier.height(16.dp))
        Eyebrow(L("Geschätztes 1RM", "Estimated 1RM"))
        Text("${Stats.fmtKg(Stats.roundTo(oneRm, step))} kg", style = MaterialTheme.typography.headlineMedium, color = Ink.accent)
        if (reps > 12) Text(L("Über 12 Wiederholungen wird die Schätzung ungenau.", "Beyond 12 reps the estimate gets rough."),
            style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        Spacer(Modifier.height(12.dp))
        TableRow(L("Anteil", "Share"), L("Gewicht", "Weight"), L("Wdh.", "Reps"), header = true, tinted = false)
        Strength.table(oneRm, step).forEachIndexed { i, r ->
            TableRow("${r.percent} %", "${Stats.fmtKg(r.weight)} kg", L("ca. ${r.reps}", "about ${r.reps}"), header = false, tinted = i % 2 == 0)
        }
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, header: Boolean, tinted: Boolean) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono)
    val color = if (header) Ink.muted else Ink.text
    Row(
        Modifier.fillMaxWidth().clip(RowShape).background(if (tinted) Ink.raised else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = if (header) 4.dp else 8.dp),
    ) {
        Text(a, Modifier.weight(1f), style = style, color = color, maxLines = 1)
        Text(b, Modifier.weight(1.3f), style = style, color = color, maxLines = 1)
        Text(c, Modifier.weight(1f), style = style, color = Ink.muted, maxLines = 1)
    }
}

@Composable
private fun PlateCard(defaultBar: Double) {
    var target by rememberSaveable { mutableStateOf("") }
    var bar by rememberSaveable { mutableStateOf(defaultBar) }
    val total = parseKg(target)
    Card(Modifier.fillMaxWidth()) {
        ToolHeader(Icons.Rounded.Calculate, L("Scheibenrechner", "Plate calculator"), L("Was pro Seite auf die Stange kommt", "What goes on each side of the bar"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KgField(target, L("Zielgewicht", "Target weight"), Modifier.weight(1f)) { target = it }
            Stepper(L("Stange", "Bar"), "${Stats.fmtKg(bar)} kg", Modifier.width(132.dp),
                { bar = (bar - 2.5).coerceAtLeast(0.0) }) { bar = (bar + 2.5).coerceAtMost(30.0) }
        }
        if (total == null) return@Card
        Spacer(Modifier.height(16.dp))
        val plates = Coach.plates(total, bar)
        when {
            plates == null -> Text(L("Weniger als die Stange (${Stats.fmtKg(bar)} kg)", "Less than the bar (${Stats.fmtKg(bar)} kg)"), color = Ink.muted)
            plates.isEmpty() -> Text(L("Nur die Stange", "Just the bar"), color = Ink.muted)
            else -> {
                Eyebrow(L("Pro Seite", "Per side"))
                Spacer(Modifier.height(10.dp))
                PlateRow(plates)
                Spacer(Modifier.height(10.dp))
                Text(plates.joinToString(" + ") { Stats.fmtKg(it) } + " kg", style = MaterialTheme.typography.titleMedium)
                // Plates go down to 1.25 kg; a load in between can only be met approximately.
                val loaded = bar + 2 * plates.sum()
                if (loaded < total - 1e-6) Text(
                    L("Nächstmöglich: ${Stats.fmtKg(loaded)} kg", "Closest possible: ${Stats.fmtKg(loaded)} kg"),
                    style = MaterialTheme.typography.bodySmall, color = Ink.muted,
                )
            }
        }
    }
}

/** One side of the bar: sleeve, plates from the inside out, collar. */
@Composable
private fun PlateRow(plates: List<Double>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.width(28.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Ink.faint))
        plates.forEach { p ->
            Box(Modifier.width(if (p >= 10) 16.dp else 10.dp).height((28 + p * 2.6).dp).clip(RoundedCornerShape(4.dp)).background(plateColor(p)))
        }
        Box(Modifier.width(6.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Ink.lineStrong))
        Box(Modifier.width(24.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Ink.faint))
    }
}

/** Competition colours: they encode the weight, so they stay fixed whatever the accent. */
private fun plateColor(kg: Double): Color = when (kg) {
    25.0 -> Ink.red
    20.0 -> Color(0xFF5B8CFF)
    15.0 -> Color(0xFFFFD43B)
    10.0 -> Color(0xFF4CAF6A)
    else -> Ink.faint
}

@Composable
private fun WarmupCard(step: Double, bar: Double) {
    var weight by rememberSaveable { mutableStateOf("") }
    val work = parseKg(weight)
    Card(Modifier.fillMaxWidth()) {
        ToolHeader(Icons.Rounded.FitnessCenter, L("Aufwärmsätze", "Warm-up sets"), L("Rampe zu deinem Arbeitsgewicht", "Ramp up to your working weight"))
        KgField(weight, L("Arbeitsgewicht", "Working weight"), Modifier.fillMaxWidth()) { weight = it }
        if (work == null) return@Card
        Spacer(Modifier.height(12.dp))
        val sets = Coach.warmups(work, step, bar)
        if (sets.isEmpty()) {
            Text(L("Bei diesem Gewicht reicht die leere Stange zum Aufwärmen.", "At this weight the empty bar is warm-up enough."), color = Ink.muted)
            return@Card
        }
        TableRow(L("Satz", "Set"), L("Gewicht", "Weight"), L("Pro Seite", "Per side"), header = true, tinted = false)
        sets.forEachIndexed { i, s ->
            TableRow("${i + 1}", "${Stats.fmtKg(s.weight)} kg × ${s.reps}", perSide(s.weight, bar), header = false, tinted = i % 2 == 0)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RowShape).background(Ink.accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(L("Arbeit", "Work"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink.accent)
            Text("${Stats.fmtKg(work)} kg", Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono), color = Ink.accent)
            Text(perSide(work, bar), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono), color = Ink.accent, maxLines = 1)
        }
    }
}

private fun perSide(total: Double, bar: Double): String {
    val plates = Coach.plates(total, bar) ?: return "–"
    return if (plates.isEmpty()) L("Stange", "Bar") else plates.joinToString(" + ") { Stats.fmtKg(it) }
}
