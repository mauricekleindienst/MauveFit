package dev.mauve.fit.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(data: AppData, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Workout?>(null) }
    var addWeight by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle(L("${data.history.size} Trainings", "${data.history.size} workouts"), L("Verlauf", "History"))
        }
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().clip(Pill).background(Ink.surface).padding(4.dp)) {
                listOf(L("Trainings", "Workouts"), L("Rekorde", "Records"), L("Erfolge", "Achievements"), L("Körper", "Body")).forEachIndexed { i, t ->
                    Box(
                        Modifier.weight(1f).clip(Pill).background(if (tab == i) Ink.accent else Ink.surface)
                            .clickable { Haptics.tick(); tab = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t, color = if (tab == i) Ink.bg else Ink.muted, fontWeight = FontWeight.SemiBold) }
                }
            }
            }
        }
        when (tab) {
            0 -> workouts(data, expanded, { expanded = it }, { confirm = it })
            1 -> records(data, nav)
            2 -> achievements(data)
            else -> body(data) { addWeight = true }
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
    if (addWeight) {
        var kg by remember { mutableStateOf(data.bodyWeights.firstOrNull()?.let { Stats.fmtKg(it.kg) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { addWeight = false },
            title = { Text(L("Körpergewicht", "Body weight")) },
            text = {
                OutlinedTextField(kg, { kg = it }, label = { Text("kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = {
                    kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..350.0 }?.let(App.store::addBodyWeight)
                    addWeight = false
                }) { Text(L("Speichern", "Save")) }
            },
            dismissButton = { TextButton(onClick = { addWeight = false }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }
}

private fun LazyListScope.workouts(data: AppData, expanded: String?, onExpand: (String?) -> Unit, onDelete: (Workout) -> Unit) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Stats.fmtVolume(data.history.sumOf { it.volume }), L("bewegt", "lifted"), Modifier.weight(1f), Ink.accent)
            Stat(data.history.sumOf { it.doneSets.size }.toString(), L("Sätze", "sets"), Modifier.weight(1f))
            Stat(Stats.fmtDuration(data.history.sumOf { it.durationMs }), L("Zeit", "time"), Modifier.weight(1f))
        }
    }
    if (data.history.isEmpty()) item {
        Card(Modifier.fillMaxWidth()) {
            Text(L("Noch keine Trainings", "No workouts yet"), style = MaterialTheme.typography.titleLarge)
            Text(L("Jeder abgehakte Satz landet hier, mit Volumen, Dauer und Rekorden.", "Every set you tick off lands here, with volume, duration and records."), color = Ink.muted)
        }
    }
    data.history.groupBy { Stats.day(it.startedAt).withDayOfMonth(1) }.forEach { (month, list) ->
        item(key = "m$month") { Eyebrow(month.format(monthFmt), Modifier.padding(top = 14.dp, bottom = 2.dp)) }
        items(list, key = { it.id }) { w ->
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
                                    e.sets.filter { !it.warmup }.joinToString("  ") { if (ex?.bodyweight == true) L("${it.reps} Wdh.", "${it.reps} reps") else "${Stats.fmtKg(it.weight)}×${it.reps}" },
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
    items(records, key = { "r" + it.exerciseId }) { r ->
        val ex = App.repo[r.exerciseId]
        Card(Modifier.fillMaxWidth(), onClick = { nav.navigate("exercise/${r.exerciseId}") }, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(ex, 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex?.title ?: r.exerciseId, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.date.format(shortFmt), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (r.best.weight > 0) "${Stats.fmtKg(r.best.weight)} × ${r.best.reps}" else L("${r.best.reps} Wdh.", "${r.best.reps} reps"),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (r.best.weight > 0) Text("1RM ~${Stats.fmtKg(Stats.roundTo(r.e1rm, 0.5))} kg", color = Ink.accent, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.EmojiEvents, null, tint = Ink.faint)
            }
        }
    }
}

private fun LazyListScope.achievements(data: AppData) {
    val list = Progress.achievements(data)
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

private fun LazyListScope.body(data: AppData, onAdd: () -> Unit) {
    val list = data.bodyWeights
    item {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(L("Körpergewicht", "Body weight"))
                    Text(list.firstOrNull()?.let { "${Stats.fmtKg(it.kg)} kg" } ?: L("Noch kein Eintrag", "No entry yet"), style = MaterialTheme.typography.headlineMedium)
                    if (list.size >= 2) {
                        val diff = list.first().kg - list.last().kg
                        Text(
                            L(
                                "${if (diff > 0) "+" else ""}${Stats.fmtKg(Stats.roundTo(diff, 0.1))} kg seit ${Stats.day(list.last().at).format(shortFmt)}",
                                "${if (diff > 0) "+" else ""}${Stats.fmtKg(Stats.roundTo(diff, 0.1))} kg since ${Stats.day(list.last().at).format(shortFmt)}",
                            ),
                            color = Ink.muted,
                        )
                    }
                }
                RoundButton(Icons.Rounded.Add, L("Eintragen", "Log weight"), size = 48.dp, onClick = onAdd)
            }
            if (list.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Sparkline(list.reversed().map { it.kg.toFloat() }, Modifier.fillMaxWidth().height(100.dp))
            }
        }
    }
    items(list, key = { "b" + it.at }) { b ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Stats.day(b.at).format(dayFmt), Modifier.weight(1f), color = Ink.muted)
            Text("${Stats.fmtKg(b.kg)} kg", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Close, L("Löschen", "Delete"), tint = Ink.faint, modifier = Modifier.clickable { App.store.deleteBodyWeight(b.at) })
        }
    }
}
