package dev.mauve.fit.ui

import androidx.compose.material.icons.rounded.Edit
import dev.mauve.fit.data.WeightGoal
import androidx.compose.ui.text.style.TextAlign
import dev.mauve.fit.data.Labels
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.mauve.fit.data.Motivation
import dev.mauve.fit.system.ShareImage
import kotlinx.coroutines.launch
import java.time.temporal.IsoFields
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.CardioSession
import dev.mauve.fit.data.Progress
import dev.mauve.fit.data.Stats
import dev.mauve.fit.data.Workout
import java.time.format.DateTimeFormatter
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L

// Getters, not vals: the language can change while the process lives.
private val dayFmt get() = DateTimeFormatter.ofPattern(L("EEEE, d. MMMM", "EEEE, MMMM d"), I18n.locale)
private val shortFmt get() = DateTimeFormatter.ofPattern(L("d. MMM", "MMM d"), I18n.locale)
private val monthFmt get() = DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale)
private val monthNameFmt get() = DateTimeFormatter.ofPattern("LLLL", I18n.locale)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(data: AppData, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Workout?>(null) }
    var confirmCardio by remember { mutableStateOf<CardioSession?>(null) }
    // "weight" or a measurement key while the add dialog is open; the open body card lists its entries.
    var addFor by remember { mutableStateOf<String?>(null) }
    var openBody by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle(L("${data.history.size} Trainings", "${data.history.size} workouts"), L("Fortschritt", "Progress"))
        }
        stickyHeader {
            Box(Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().clip(Pill).background(Ink.surface).padding(4.dp)) {
                listOf(L("Überblick", "Overview"), L("Rekorde", "Records"), L("Erfolge", "Badges"), L("Körper", "Body")).forEachIndexed { i, t ->
                    Box(
                        Modifier.weight(1f).clip(Pill).background(if (tab == i) Ink.accent else Ink.surface)
                            .clickable { Haptics.tick(); tab = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t, color = if (tab == i) Ink.bg else Ink.muted, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                }
            }
            }
        }
        when (tab) {
            0 -> {
                overview(data, nav)
                workouts(data, expanded, { expanded = it }, { confirm = it }) { confirmCardio = it }
            }
            1 -> records(data, nav)
            2 -> achievements(data)
            else -> body(data, openBody, { openBody = it }) { addFor = it }
        }
    }

    confirm?.let { w ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(L("Training löschen?", "Delete workout?")) },
            text = { Text(L("${w.name} vom ${Stats.day(w.startedAt).format(dayFmt)} wird entfernt.", "${w.name} from ${Stats.day(w.startedAt).format(dayFmt)} will be removed.")) },
            confirmButton = { TextButton(onClick = { Haptics.warn(); App.store.deleteWorkout(w.id); confirm = null }) { Text(L("Löschen", "Delete"), color = Ink.red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }
    confirmCardio?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmCardio = null },
            title = { Text(L("Cardio löschen?", "Delete cardio?")) },
            text = { Text(L("${s.kind.label} vom ${Stats.day(s.at).format(dayFmt)} wird entfernt.", "${s.kind.label} from ${Stats.day(s.at).format(dayFmt)} will be removed.")) },
            confirmButton = { TextButton(onClick = { Haptics.warn(); App.store.deleteCardio(s.id); confirmCardio = null }) { Text(L("Löschen", "Delete"), color = Ink.red) } },
            dismissButton = { TextButton(onClick = { confirmCardio = null }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }
    addFor?.let { kind ->
        val weight = kind == "weight"
        val unit = if (weight) "kg" else "cm"
        var value by remember(kind) {
            val last = if (weight) data.bodyWeights.firstOrNull()?.kg else data.measurements.firstOrNull { it.kind == kind }?.cm
            mutableStateOf(last?.let(Stats::fmtKg).orEmpty())
        }
        AlertDialog(
            onDismissRequest = { addFor = null },
            title = { Text(if (weight) L("Körpergewicht", "Body weight") else Progress.measures[kind].orEmpty()) },
            text = {
                OutlinedTextField(value, { value = it }, label = { Text(unit) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.replace(',', '.').toDoubleOrNull()
                    if (weight) v?.takeIf { it in 25.0..350.0 }?.let(App.store::addBodyWeight)
                    else v?.takeIf { it in 10.0..250.0 }?.let { App.store.addMeasurement(kind, it) }
                    if (v != null) Haptics.tick()
                    addFor = null
                }) { Text(L("Speichern", "Save")) }
            },
            dismissButton = { TextButton(onClick = { addFor = null }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }
}

private fun LazyListScope.workouts(data: AppData, expanded: String?, onExpand: (String?) -> Unit, onDelete: (Workout) -> Unit, onDeleteCardio: (CardioSession) -> Unit) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Stats.fmtVolume(data.history.sumOf { it.volume }), L("bewegt", "lifted"), Modifier.weight(1f), Ink.accent)
            Stat(data.history.sumOf { it.doneSets.size }.toString(), L("Sätze", "sets"), Modifier.weight(1f))
            Stat(Stats.fmtDuration(data.history.sumOf { it.durationMs }), L("Zeit", "time"), Modifier.weight(1f))
        }
    }
    if (data.history.isEmpty() && data.cardio.isEmpty()) item {
        Card(Modifier.fillMaxWidth()) {
            Text(L("Noch keine Trainings", "No workouts yet"), style = MaterialTheme.typography.titleLarge)
            Text(L("Jeder abgehakte Satz landet hier, mit Volumen, Dauer und Rekorden.", "Every set you tick off lands here, with volume, duration and records."), color = Ink.muted)
        }
    }
    // Strength workouts and cardio in one timeline, newest first.
    val timeline = (data.history.map { it.startedAt to it } + data.cardio.map { it.at to it }).sortedByDescending { it.first }.map { it.second }
    timeline.groupBy { Stats.day(if (it is CardioSession) it.at else (it as Workout).startedAt).withDayOfMonth(1) }.forEach { (month, list) ->
        item(key = "m$month") { Eyebrow(month.format(monthFmt), Modifier.padding(top = 14.dp, bottom = 2.dp)) }
        items(list, key = { if (it is CardioSession) "c" + it.id else (it as Workout).id }) { item ->
            if (item is CardioSession) { CardioRow(item) { onDeleteCardio(item) }; return@items }
            val w = item as Workout
            val open = expanded == w.id
            Card(Modifier.fillMaxWidth().animateContentSize(), onClick = { onExpand(if (open) null else w.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Stats.day(w.startedAt).format(dayFmt) + when (w.feel) { 1 -> L(" · Leicht", " · Easy"); 2 -> L(" · Gut", " · Good"); 3 -> L(" · Hart", " · Hard"); else -> "" },
                            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Stats.headline(w), style = MaterialTheme.typography.titleLarge, color = Ink.accent)
                        Text("${Stats.sets(w.doneSets.size)} · ${Stats.fmtDuration(w.durationMs)}", color = Ink.faint, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (open) {
                    Spacer(Modifier.height(12.dp))
                    if (w.note.isNotBlank()) Text(w.note, color = Ink.text, modifier = Modifier.padding(bottom = 8.dp))
                    w.entries.forEach { e ->
                        val ex = App.repo[e.exerciseId]
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Thumb(ex, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex?.title ?: e.exerciseId, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    e.sets.filter { !it.warmup }.joinToString("   ") { Stats.setLabel(it, ex?.bodyweight == true) },
                                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        RoundButton(Icons.Rounded.DeleteOutline, L("Training löschen", "Delete workout")) { onDelete(w) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.records(data: AppData, nav: NavController) {
    val records = Stats.records(data.history)
    if (records.isEmpty()) item {
        Card(Modifier.fillMaxWidth()) {
            Text(L("Noch keine Rekorde", "No records yet"), style = MaterialTheme.typography.titleLarge)
            Text(L("Nach dem ersten Training steht hier dein bester Satz für jede Übung.", "After your first workout, your best set for every exercise shows up here."), color = Ink.muted)
        }
    }
    if (records.isNotEmpty()) item(key = "share") {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        GhostButton(L("Als Bild teilen", "Share as image"), Modifier.fillMaxWidth().padding(bottom = 4.dp), Icons.Rounded.Image) {
            // The four most recent records, with the totals that led there.
            val card = ShareImage.Card(
                title = L("Meine Rekorde", "My records"),
                date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern(L("EEEE, d. MMMM yyyy", "EEEE, MMMM d, yyyy"), I18n.locale)),
                stats = listOf(
                    "${data.history.size}" to L("Trainings", "Workouts"),
                    "${records.size}" to L("Rekorde", "Records"),
                    Stats.fmtVolume(data.history.sumOf { it.volume }) to L("Volumen", "Volume"),
                ),
                heading = L("Neueste Rekorde", "Latest records"),
                rows = records.take(4).map { (App.repo[it.exerciseId]?.title ?: it.exerciseId) to ShareImage.setText(it.best) },
            )
            scope.launch { runCatching { ShareImage.share(context, card) } }
        }
    }
    items(records, key = { "r" + it.exerciseId }) { r ->
        val ex = App.repo[r.exerciseId]
        Card(Modifier.fillMaxWidth(), onClick = { nav.navigate("exercise/${r.exerciseId}") }, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(ex, 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex?.title ?: r.exerciseId, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.date.format(shortFmt), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (r.best.weight > 0) "${Stats.fmtKg(r.best.weight)} × ${r.best.reps}" else L("${r.best.reps} Wdh.", "${r.best.reps} reps"),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (r.best.weight > 0) Text("1RM ~${Stats.fmtKg(Stats.roundTo(r.e1rm, 0.5))} kg", color = Ink.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun LazyListScope.achievements(data: AppData) {
    val list = Progress.achievements(data)
    item(key = "challenges") {
        val c = remember(data.history, data.settings, data.profile) { Motivation.challenge(data, App.repo) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                Eyebrow(L("Wochen-Challenges", "Weekly challenges"))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${data.challengesDone.size}", style = MaterialTheme.typography.headlineLarge, color = Ink.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(L("geschafft", "completed"), color = Ink.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    L("Jeden Montag gibt es eine neue, passend zu deinem Wochenziel.", "A new one every Monday, sized to your weekly goal."),
                    style = MaterialTheme.typography.bodySmall, color = Ink.muted,
                )
            }
            ChallengeCard(c, data.challengesDone.size)
            FreezeCard(data)
        }
    }
    item {
        Text(L("${list.count { it.unlocked }} von ${list.size} freigeschaltet", "${list.count { it.unlocked }} of ${list.size} unlocked"), color = Ink.muted, modifier = Modifier.padding(vertical = 4.dp))
    }
    items(list, key = { "a" + it.title }) { a ->
        Card(Modifier.fillMaxWidth(), color = if (a.unlocked) Ink.raised else Ink.surface, padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(Pill).background(if (a.unlocked) Ink.accent else Ink.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (a.unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null,
                        tint = if (a.unlocked) Ink.bg else Ink.faint, modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, color = if (a.unlocked) Ink.text else Ink.muted)
                    Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                    if (!a.unlocked) {
                        Spacer(Modifier.height(8.dp))
                        Meter(a.progress, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Streak freezes explained: how many are in stock, how they are earned and when the next one comes. */
@Composable
private fun FreezeCard(data: AppData) {
    val streak = Stats.weekStreak(data.history, data.settings.weeklyGoal, data.frozenWeeks)
    val toNext = Motivation.FREEZE_EVERY - data.streakRun % Motivation.FREEZE_EVERY
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(L("Serie und Joker", "Streak and freezes"), Modifier.weight(1f))
            Tag(Motivation.freezeLabel(data.freezes), if (data.freezes > 0) Ink.accent else Ink.muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (streak == 1) L("1 Woche in Folge", "1 week in a row") else L("$streak Wochen in Folge", "$streak weeks in a row"),
            style = MaterialTheme.typography.titleLarge, color = Ink.coral,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            L(
                "Alle ${Motivation.FREEZE_EVERY} Wochen in Folge mit erreichtem Wochenziel gibt es einen Joker, höchstens ${Motivation.FREEZE_MAX} auf Vorrat. Verpasst du eine Woche, springt automatisch ein Joker ein und die Serie bleibt.",
                "Every ${Motivation.FREEZE_EVERY} weeks in a row with your weekly goal met earn a freeze, up to ${Motivation.FREEZE_MAX} in stock. Miss a week and a freeze steps in by itself, so the streak lives on.",
            ),
            style = MaterialTheme.typography.bodySmall, color = Ink.muted,
        )
        Spacer(Modifier.height(12.dp))
        if (data.freezes >= Motivation.FREEZE_MAX) {
            Text(L("Vorrat voll", "Stock full"), style = MaterialTheme.typography.labelLarge, color = Ink.accent)
        } else {
            Meter((Motivation.FREEZE_EVERY - toNext) / Motivation.FREEZE_EVERY.toFloat(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                if (toNext == 1) L("Noch 1 Woche bis zum nächsten Joker", "1 more week to the next freeze")
                else L("Noch $toNext Wochen bis zum nächsten Joker", "$toNext more weeks to the next freeze"),
                style = MaterialTheme.typography.labelMedium, color = Ink.muted,
            )
        }
        if (data.frozenWeeks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val n = data.frozenWeeks.size
            Text(
                if (n == 1) L("Bisher 1 Woche gerettet", "1 week saved so far") else L("Bisher $n Wochen gerettet", "$n weeks saved so far"),
                style = MaterialTheme.typography.labelMedium, color = Ink.faint,
            )
        }
    }
}

private fun LazyListScope.body(data: AppData, open: String?, onOpen: (String?) -> Unit, onAdd: (String) -> Unit) {
    item(key = "weight") {
        TrendCard(
            L("Körpergewicht", "Body weight"), "kg", data.bodyWeights.map { it.at to it.kg }, open == "weight",
            chartHeight = 100.dp, onToggle = { onOpen(if (open == "weight") null else "weight") }, onAdd = { onAdd("weight") },
            onDelete = App.store::deleteBodyWeight,
        )
    }
    item(key = "goal") { WeightGoalCard(data) }
    item(key = "photos") { ProgressPhotos() }
    item { Eyebrow(L("Umfänge", "Measurements"), Modifier.padding(top = 8.dp)) }
    Progress.measures.forEach { (kind, label) ->
        item(key = "m-$kind") {
            TrendCard(
                label, "cm", data.measurements.filter { it.kind == kind }.map { it.at to it.cm }, open == kind,
                chartHeight = 64.dp, onToggle = { onOpen(if (open == kind) null else kind) }, onAdd = { onAdd(kind) },
                onDelete = { App.store.deleteMeasurement(kind, it) },
            )
        }
    }
}

/**
 * Latest value of one body metric with its change since the first entry and a small chart; tapping
 * the card lists every entry so single ones can be deleted. [entries] are (time, value), newest first.
 */
@Composable
private fun TrendCard(
    title: String,
    unit: String,
    entries: List<Pair<Long, Double>>,
    open: Boolean,
    chartHeight: Dp,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth().animateContentSize(), onClick = if (entries.isNotEmpty()) onToggle else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(title)
                val latest = entries.firstOrNull()
                if (latest != null) Text("${Stats.fmtKg(latest.second)} $unit", style = MaterialTheme.typography.headlineMedium)
                else Text(L("Noch kein Eintrag", "No entry yet"), style = MaterialTheme.typography.bodyLarge, color = Ink.faint)
                if (entries.size >= 2) {
                    val diff = Stats.roundTo(entries.first().second - entries.last().second, 0.1)
                    val since = Stats.day(entries.last().first).format(shortFmt)
                    val sign = if (diff > 0) "+" else ""
                    Text(L("$sign${Stats.fmtKg(diff)} $unit seit $since", "$sign${Stats.fmtKg(diff)} $unit since $since"), color = Ink.muted)
                }
            }
            RoundButton(Icons.Rounded.Add, L("Eintragen", "Add entry"), size = 48.dp, onClick = onAdd)
        }
        if (entries.size >= 2) {
            Spacer(Modifier.height(16.dp))
            TrendChart(entries.reversed().map { it.second.toFloat() }, Modifier.fillMaxWidth().height(chartHeight))
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            entries.forEach { (at, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Stats.day(at).format(dayFmt), Modifier.weight(1f), color = Ink.muted)
                    Text("${Stats.fmtKg(v)} $unit", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Close, L("Löschen", "Delete"), tint = Ink.faint, modifier = Modifier.clip(Pill).clickable { Haptics.tick(); onDelete(at) })
                }
            }
        }
    }
}

/** Line chart with a soft fill below; values oldest first, scaled between their own min and max. */
@Composable
private fun TrendChart(values: List<Float>, modifier: Modifier = Modifier) {
    val accent = Ink.accent
    Canvas(modifier) {
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 6.dp.toPx()
        val pts = values.mapIndexed { i, v ->
            Offset(
                pad + i * (size.width - 2 * pad) / (values.size - 1).coerceAtLeast(1),
                pad + (1 - (v - min) / range) * (size.height - 2 * pad),
            )
        }
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f))))
        drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(accent, 4.5.dp.toPx(), pts.last())
    }
}

/** Insights entry, weekly volume and sets per muscle, above the workout list. */
private fun LazyListScope.overview(data: AppData, nav: NavController) {
    if (data.history.isNotEmpty()) item(key = "insights") { InsightsTile(data) { nav.navigate("insights") { launchSingleTop = true } } }
    if (data.history.isEmpty()) {
        if (data.cardio.isNotEmpty()) item { Card(Modifier.fillMaxWidth()) { CardioWeek(data.cardio) } }
        return
    }
    val volume = Stats.weeklyVolume(data.history)
    item {
        Card(Modifier.fillMaxWidth()) {
            Eyebrow(L("Volumen pro Woche", "Volume per week"))
            Spacer(Modifier.height(14.dp))
            Bars(volume.map { it.second.toFloat() }, volume.map { L("KW", "W") + it.first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) })
        }
    }
    item { Heatmap(Progress.heatmap(data.history, App.repo)) }
    item { MonthlyRecap(Progress.monthRecaps(data.history, App.repo)) }
    if (data.cardio.isNotEmpty()) item { Card(Modifier.fillMaxWidth()) { CardioWeek(data.cardio) } }
    val muscles = Stats.muscleSets(data.history, App.repo).entries.sortedByDescending { it.value }.take(6)
    if (muscles.isNotEmpty()) item {
        Card(Modifier.fillMaxWidth()) {
            Eyebrow(L("Sätze pro Muskel · 7 Tage", "Sets per muscle · 7 days"))
            Spacer(Modifier.height(12.dp))
            val max = muscles.first().value.toFloat()
            muscles.forEach { (m, n) ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Labels.muscle(m), Modifier.width(118.dp), style = MaterialTheme.typography.bodyMedium)
                    Meter(n / max, Modifier.weight(1f))
                    Text("$n", Modifier.width(34.dp), textAlign = TextAlign.End, color = Ink.muted)
                }
            }
        }
    }
    item { Eyebrow(L("Trainings", "Workouts"), Modifier.padding(top = 8.dp)) }
}

/** Muscles as tiles, from cold (untrained) to hot (most sets) over the last four weeks. */
@Composable
private fun Heatmap(counts: Map<String, Int>) {
    val max = counts.values.maxOrNull()?.takeIf { it > 0 } ?: 1
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(L("Muskel-Heatmap · 28 Tage", "Muscle heatmap · 28 days"))
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (m, n) ->
                        val f = n / max.toFloat()
                        val hot = f > 0.6f
                        Column(
                            Modifier.weight(1f).clip(RowShape)
                                .background(if (n == 0) Ink.raised else Ink.accent.copy(alpha = 0.16f + 0.84f * f))
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                        ) {
                            Text(Labels.muscle(m), style = MaterialTheme.typography.labelMedium, color = if (hot) Ink.bg else Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$n", style = MaterialTheme.typography.titleMedium, color = if (hot) Ink.bg else if (n == 0) Ink.faint else Ink.text)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Kalt", "Cold"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
            Box(
                Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).clip(Pill)
                    .background(Brush.horizontalGradient(listOf(Ink.raised, Ink.accent.copy(alpha = 0.4f), Ink.accent)))
            )
            Text(L("Heiß · ${Stats.sets(max)}", "Hot · ${Stats.sets(max)}"), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
        }
    }
}

/** This month and the last one, each compared with the month before it. */
@Composable
private fun MonthlyRecap(recaps: List<Progress.MonthRecap>) {
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(L("Monatsrückblick", "Monthly recap"))
        recaps.zipWithNext().take(2).forEachIndexed { i, (cur, prev) ->
            Spacer(Modifier.height(if (i == 0) 12.dp else 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cur.month.format(monthNameFmt).replaceFirstChar { it.titlecase(I18n.locale) }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (i == 0) Tag(L("Läuft", "In progress"))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecapCell(cur.workouts.toString(), L("Trainings", "Workouts"), cur.workouts.toDouble(), prev.workouts.toDouble(), false, Modifier.weight(1f))
                RecapCell(Stats.fmtVolume(cur.volume), L("Volumen", "Volume"), cur.volume, prev.volume, true, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecapCell(if (cur.timeMs > 0) Stats.fmtDuration(cur.timeMs) else "0 min", L("Trainingszeit", "Time trained"), cur.timeMs.toDouble(), prev.timeMs.toDouble(), true, Modifier.weight(1f))
                RecapCell(cur.records.toString(), L("Neue Rekorde", "New records"), cur.records.toDouble(), prev.records.toDouble(), false, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                cur.topMuscle?.let { L("Meist trainiert: ${Labels.muscle(it)}", "Most trained: ${Labels.muscle(it)}") } ?: L("Noch kein Training", "No workouts yet"),
                color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RecapCell(value: String, label: String, cur: Double, prev: Double, percent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.clip(RowShape).background(Ink.raised).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Change(cur, prev, percent)
    }
}

/** Up or down against the month before: a percentage for amounts, the difference for counts. */
@Composable
private fun Change(cur: Double, prev: Double, percent: Boolean) {
    if (cur == prev) {
        Text(L("Wie im Vormonat", "Same as before"), style = MaterialTheme.typography.labelSmall, color = Ink.faint, maxLines = 1)
        return
    }
    val up = cur > prev
    val text = when {
        percent && prev > 0 -> "${abs((cur - prev) / prev * 100).roundToInt()} %"
        percent -> L("neu", "new")
        else -> abs(cur - prev).roundToInt().toString()
    }
    val color = if (up) Ink.accent else Ink.muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            if (up) L("Mehr als im Vormonat", "Up on last month") else L("Weniger als im Vormonat", "Down on last month"),
            tint = color, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Target weight: progress from the first entry, the recent pace and when the goal is reached at it. */
@Composable
private fun WeightGoalCard(data: AppData) {
    var editing by remember { mutableStateOf(false) }
    val goal = data.settings.goalWeight
    val o = WeightGoal.outlook(data.bodyWeights.map { it.at to it.kg }, goal)
    Card(Modifier.fillMaxWidth(), onClick = { editing = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(L("Zielgewicht", "Goal weight"))
                if (goal <= 0) Text(L("Kein Ziel gesetzt", "No goal set"), style = MaterialTheme.typography.bodyLarge, color = Ink.faint)
                else Text("${Stats.fmtKg(goal)} kg", style = MaterialTheme.typography.headlineMedium)
            }
            RoundButton(Icons.Rounded.Edit, L("Ziel ändern", "Change goal"), size = 48.dp) { editing = true }
        }
        if (o != null) {
            Spacer(Modifier.height(12.dp))
            Meter(o.progress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            val left = Stats.fmtKg(Stats.roundTo(kotlin.math.abs(o.remaining), 0.1))
            Text(
                if (o.reached) L("Ziel erreicht. Halte es oder setz ein neues.", "Goal reached. Hold it or set a new one.")
                else L("Noch $left kg", "$left kg to go"),
                style = MaterialTheme.typography.titleMedium, color = if (o.reached) Ink.accent else Ink.text,
            )
            o.perWeek?.let { p ->
                val sign = if (p > 0) "+" else ""
                Text(L("Tempo: $sign${Stats.fmtKg(Stats.roundTo(p, 0.1))} kg pro Woche", "Pace: $sign${Stats.fmtKg(Stats.roundTo(p, 0.1))} kg a week"), color = Ink.muted)
            } ?: Text(L("Für ein Tempo braucht es 3 Einträge über mindestens eine Woche.", "A pace needs 3 entries over at least a week."), color = Ink.faint, style = MaterialTheme.typography.bodySmall)
            o.eta?.let { Text(L("So erreichst du es etwa am ${it.format(dayFmt)}.", "At this pace you get there around ${it.format(dayFmt)}."), color = Ink.muted) }
            if (!o.reached && o.perWeek != null && o.eta == null) Text(L("Das Tempo geht gerade in die andere Richtung.", "The pace is going the other way right now."), color = Ink.coral, style = MaterialTheme.typography.bodySmall)
            if (o.tooFast) Text(
                L("Ziemlich schnell. Etwas langsamer schont Muskeln und Kraft.", "Quite fast. A little slower protects muscle and strength."),
                color = Ink.coral, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (editing) {
        var kg by remember { mutableStateOf(if (goal > 0) Stats.fmtKg(goal) else "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(L("Zielgewicht", "Goal weight")) },
            text = {
                OutlinedTextField(kg, { kg = it }, label = { Text("kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = {
                    kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let { v ->
                        Haptics.success(); App.store.updateSettings { it.copy(goalWeight = v) }
                    }
                    editing = false
                }) { Text(L("Speichern", "Save")) }
            },
            dismissButton = {
                if (goal > 0) TextButton(onClick = { App.store.updateSettings { it.copy(goalWeight = 0.0) }; editing = false }) { Text(L("Ziel entfernen", "Remove goal"), color = Ink.red) }
                else TextButton(onClick = { editing = false }) { Text(L("Abbrechen", "Cancel")) }
            },
            containerColor = Ink.raised,
        )
    }
}
