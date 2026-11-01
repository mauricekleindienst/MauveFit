package dev.mauve.fit.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.ExerciseEntry
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import dev.mauve.fit.data.SetEntry
import dev.mauve.fit.data.Stats
import dev.mauve.fit.data.Workout
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Focus mode: one exercise per page (swipe between them, or tap a segment), sets are ticked off
 * with a big check, and ticking one starts the rest timer at the bottom.
 */
@Composable
fun WorkoutScreen(data: AppData, nav: NavController) {
    val w = data.active
    if (w == null) {
        // Only leave if this screen is still on top; after saving, the summary has already replaced it.
        LaunchedEffect(Unit) { if (nav.currentBackStackEntry?.destination?.route == "workout") nav.popBackStack() }
        return
    }
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(data.settings.keepScreenOn) {
        view.keepScreenOn = data.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    // Buzz when the rest timer runs out, then clear it. Restarts whenever the end time changes (+15 s).
    LaunchedEffect(w.restEndsAt) {
        val end = w.restEndsAt ?: return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0))
        Haptics.restOver()
        App.store.updateActive { it.copy(restEndsAt = null) }
    }

    // The page count must follow exercises added during the workout, so read it through state.
    val pageCount by rememberUpdatedState(w.entries.size + 1)
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    var finishDialog by remember { mutableStateOf(false) }
    var leaveDialog by remember { mutableStateOf(false) }

    // A light tick each time a new exercise page settles.
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.drop(1).collect { Haptics.tick() } }

    fun updateEntry(i: Int, block: (ExerciseEntry) -> ExerciseEntry) =
        App.store.updateActive { it.copy(entries = it.entries.toMutableList().also { l -> if (i in l.indices) l[i] = block(l[i]) }) }

    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Rounded.Close, L("Training verwerfen", "Discard workout")) { leaveDialog = true }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clock(now - w.startedAt), style = MaterialTheme.typography.headlineSmall)
                    Text(w.name, style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 1)
                }
                Box(
                    Modifier.clip(Pill).background(Ink.accent).clickable { Haptics.click(); finishDialog = true }.padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(L("Fertig", "Done"), color = Ink.bg, fontWeight = FontWeight.Bold) }
            }
            // One segment per exercise; fill shows completed sets.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                w.entries.forEachIndexed { i, e ->
                    val done = e.sets.count { it.done } / e.sets.size.coerceAtLeast(1).toFloat()
                    Box(Modifier.weight(1f).height(18.dp).clickable { Haptics.tick(); scope.launch { pager.animateScrollToPage(i) } }, contentAlignment = Alignment.Center) {
                        Meter(done, Modifier.fillMaxWidth(), if (i == pager.currentPage) Ink.accent else Ink.accent.copy(alpha = 0.55f))
                    }
                }
            }

            HorizontalPager(pager, Modifier.weight(1f), key = { if (it < w.entries.size) "e$it-${w.entries[it].exerciseId}" else "add" }) { page ->
                if (page >= w.entries.size) AddPage(w, nav)
                else ExercisePage(
                    index = page,
                    total = w.entries.size,
                    entry = w.entries[page],
                    data = data,
                    onChange = { block -> updateEntry(page, block) },
                    onSetDone = { _ ->
                        // The coach sets the rest: short after warm-ups, longer after a set that fell short.
                        val entry = App.store.data.value.active?.entries?.getOrNull(page) ?: w.entries[page]
                        val set = entry.sets.lastOrNull { it.done } ?: entry.sets.first()
                        val rest = Coach.restFor(entry, set)
                        App.store.updateActive {
                            it.copy(restEndsAt = System.currentTimeMillis() + rest * 1000L, restTotalSec = rest)
                        }
                    },
                    onSwap = { id -> App.store.swapExercise(page, id) },
                    onWarmup = { App.store.addWarmups(page) },
                    onAllDone = {
                        scope.launch { delay(500); pager.animateScrollToPage(page + 1) }
                    },
                    onOpen = { nav.navigate("exercise/${w.entries[page].exerciseId}") },
                    onRemove = {
                        App.store.updateActive { it.copy(entries = it.entries.filterIndexed { j, _ -> j != page }) }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = w.restEndsAt != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            RestBar(((w.restEndsAt ?: now) - now).coerceAtLeast(0), w.restTotalSec)
        }
    }

    if (finishDialog) FinishDialog(w, data, onDismiss = { finishDialog = false }) {
        finishDialog = false
        val id = App.store.finishWorkout()
        Haptics.success()
        if (id != null) nav.navigate("summary/$id") { popUpTo("workout") { inclusive = true } } else nav.popBackStack()
    }
    if (leaveDialog) AlertDialog(
        onDismissRequest = { leaveDialog = false },
        title = { Text(L("Training verwerfen?", "Discard workout?")) },
        text = { Text(L("Alle Sätze dieses Trainings gehen verloren.", "All sets of this workout will be lost.")) },
        confirmButton = {
            TextButton(onClick = { Haptics.warn(); leaveDialog = false; App.store.discardWorkout(); nav.popBackStack() }) { Text(L("Verwerfen", "Discard"), color = Ink.red) }
        },
        dismissButton = { TextButton(onClick = { leaveDialog = false }) { Text(L("Weiter trainieren", "Keep training")) } },
        containerColor = Ink.raised,
    )
}

@Composable
private fun ExercisePage(
    index: Int,
    total: Int,
    entry: ExerciseEntry,
    data: AppData,
    onChange: ((ExerciseEntry) -> ExerciseEntry) -> Unit,
    onSetDone: (warmup: Boolean) -> Unit,
    onAllDone: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSwap: (String) -> Unit,
    onWarmup: () -> Unit,
) {
    val history = data.history
    val step = data.settings.weightStep
    val e = App.repo[entry.exerciseId]
    val bodyweight = e?.bodyweight == true
    val last = remember(history, entry.exerciseId) { Stats.lastSets(history, entry.exerciseId) }
    val targetReps = entry.sets.firstOrNull { !it.warmup }?.reps ?: 10
    val tip = remember(history, entry.exerciseId) { Coach.exerciseTip(history, entry.exerciseId, targetReps, bodyweight) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var swapping by remember { mutableStateOf(false) }
    val current = entry.sets.indexOfFirst { !it.done }
    // Weight advice after a set that clearly over- or undershot its target; hidden once applied or dismissed.
    val advice = Coach.setAdvice(entry, step)
    val doneCount = entry.sets.count { it.done }
    var handledAt by remember(entry.exerciseId) { mutableStateOf(-1) }
    val canWarmUp = !bodyweight && entry.sets.none { it.warmup } && entry.sets.none { it.done } &&
        (entry.sets.firstOrNull()?.weight ?: 0.0) > data.settings.barWeight

    /** Changes one set; the same change carries to later unticked sets that still had the old value. */
    fun edit(i: Int, weight: Double? = null, reps: Int? = null) = onChange { en ->
        val old = en.sets[i]
        en.copy(sets = en.sets.mapIndexed { j, s ->
            when {
                j == i -> s.copy(weight = weight ?: s.weight, reps = reps ?: s.reps)
                j > i && !s.done -> s.copy(
                    weight = if (weight != null && s.weight == old.weight) weight else s.weight,
                    reps = if (reps != null && s.reps == old.reps) reps else s.reps,
                )
                else -> s
            }
        })
    }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 170.dp)) {
        item {
            ExerciseMedia(e, Modifier.fillMaxWidth().aspectRatio(1.35f).clickable(onClick = onOpen), corner = 28.dp)
            Spacer(Modifier.height(16.dp))
            Eyebrow(
                L(
                    "Übung ${index + 1} von $total · ${e?.let { Labels.muscle(it.target) } ?: ""}",
                    "Exercise ${index + 1} of $total · ${e?.let { Labels.muscle(it.target) } ?: ""}",
                ),
                color = Ink.accent,
            )
            Text(e?.title ?: entry.exerciseId, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (last.isNotEmpty()) Text(
                L("Letztes Mal: ", "Last time: ") + last.joinToString("  ") { if (bodyweight) "${it.reps}" else "${Stats.fmtKg(it.weight)}×${it.reps}" },
                color = Ink.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(L("Tauschen", "Swap"), Icons.Rounded.SwapHoriz) { swapping = true }
                if (canWarmUp) ActionChip(L("Aufwärmen", "Warm-up"), Icons.Rounded.LocalFireDepartment, onWarmup)
            }
            Spacer(Modifier.height(12.dp))
            CoachLine(tip)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 12.dp)) {
                Text(L("SATZ", "SET"), Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = Ink.faint)
                if (!bodyweight) Text("KG", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Ink.faint, textAlign = TextAlign.Center)
                Text(L("WDH.", "REPS"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Ink.faint, textAlign = TextAlign.Center)
                Spacer(Modifier.width(52.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
        itemsIndexed(entry.sets) { i, s ->
            val bg by animateColorAsState(if (s.done) Ink.raised else Ink.surface, label = "set")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                    .then(if (i == current) Modifier.border(1.5.dp, Ink.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = if (s.warmup) "W" else "${entry.sets.take(i + 1).count { !it.warmup }}"
                Text(label, Modifier.width(40.dp), style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono), color = when {
                    s.warmup -> Ink.mauve
                    s.done -> Ink.accent
                    else -> Ink.muted
                })
                if (!bodyweight) ValueCell(Stats.fmtKg(s.weight), Modifier.weight(1f),
                    onMinus = { edit(i, weight = (s.weight - step).coerceAtLeast(0.0)) },
                    onPlus = { edit(i, weight = s.weight + step) },
                    onEdit = { editing = i })
                ValueCell("${s.reps}", Modifier.weight(1f),
                    onMinus = { edit(i, reps = (s.reps - 1).coerceAtLeast(0)) },
                    onPlus = { edit(i, reps = s.reps + 1) },
                    onEdit = { editing = i })
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (s.done) Ink.accent else Ink.raised)
                        .clickable {
                            onChange { en -> en.copy(sets = en.sets.toMutableList().also { it[i] = it[i].copy(done = !s.done) }) }
                            if (s.done) Haptics.tick()
                            else if (entry.sets.withIndex().all { (j, x) -> j == i || x.done }) Haptics.success() else Haptics.heavy()
                            if (!s.done) {
                                onSetDone(s.warmup)
                                if (entry.sets.withIndex().all { (j, x) -> j == i || x.done }) onAllDone()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, L("Satz erledigt", "Set done"), tint = if (s.done) Ink.bg else Ink.muted) }
            }
        }
        if (advice != null && handledAt != doneCount) item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(Ink.surface)
                    .border(1.dp, Ink.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(advice.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(Pill).background(Ink.accent).clickable {
                        Haptics.click()
                        onChange { en -> en.copy(sets = en.sets.map { x -> if (!x.done && !x.warmup) x.copy(weight = (x.weight + advice.delta).coerceAtLeast(0.0)) else x }) }
                        handledAt = doneCount
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        (if (advice.delta > 0) "+" else "") + Stats.fmtKg(advice.delta) + " kg",
                        color = Ink.bg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(32.dp).clip(Pill).clickable { Haptics.tick(); handledAt = doneCount }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, L("Ausblenden", "Dismiss"), tint = Ink.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(L("Satz", "Set"), Modifier.weight(1f), Icons.Rounded.Add) {
                    onChange { en -> en.copy(sets = en.sets + (en.sets.lastOrNull()?.copy(done = false) ?: SetEntry(reps = 10))) }
                }
                if (entry.sets.size > 1) GhostButton(L("Satz", "Set"), Modifier.weight(1f), Icons.Rounded.Remove) {
                    onChange { en -> en.copy(sets = en.sets.dropLast(1)) }
                } else GhostButton(L("Übung entfernen", "Remove exercise"), Modifier.weight(1f), onClick = onRemove)
            }
        }
    }

    if (swapping) SwapDialog(entry.exerciseId, data, onPick = { onSwap(it); swapping = false }) { swapping = false }

    editing?.let { i ->
        val s = entry.sets.getOrNull(i) ?: return@let
        var kg by remember(i) { mutableStateOf(if (s.weight > 0) Stats.fmtKg(s.weight) else "") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(i) { runCatching { focus.requestFocus() } }
        var reps by remember(i) { mutableStateOf("${s.reps}") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(L("Satz ${i + 1}", "Set ${i + 1}")) },
            text = {
                Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!bodyweight) OutlinedTextField(kg, { kg = it }, Modifier.weight(1f).focusRequester(focus), label = { Text("kg") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(reps, { reps = it }, Modifier.weight(1f).then(if (bodyweight) Modifier.focusRequester(focus) else Modifier), label = { Text(L("Wdh.", "Reps")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (Coach.usesBarbell(e)) PlateView(kg.replace(',', '.').toDoubleOrNull() ?: 0.0, data.settings.barWeight)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    edit(i, weight = kg.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0), reps = reps.toIntOrNull()?.coerceAtLeast(0))
                    editing = null
                }) { Text(L("Übernehmen", "Apply")) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }
}

/** Alternatives in a dialog so the workout stays in place. */
@Composable
private fun SwapDialog(exerciseId: String, data: AppData, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = remember(exerciseId) { Coach.alternatives(App.repo, exerciseId, data.profile.setup) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Übung tauschen", "Swap exercise")) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                item { Text(L("Gleicher Zielmuskel, passend zu deiner Ausstattung.", "Same target muscle, matching your equipment."), color = Ink.muted, modifier = Modifier.padding(bottom = 8.dp)) }
                items(options.size) { i ->
                    val o = options[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Haptics.click(); onPick(o.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(o, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(Labels.equipment(o.equipment), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** Barbell loading per side, drawn as plates sized by weight. */
@Composable
private fun PlateView(total: Double, bar: Double) {
    val plates = Coach.plates(total, bar)
    Column(Modifier.padding(top = 16.dp)) {
        Text(L("PRO SEITE", "PER SIDE"), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        when {
            plates == null -> Text(L("Weniger als die Stange (${Stats.fmtKg(bar)} kg)", "Less than the bar (${Stats.fmtKg(bar)} kg)"), color = Ink.muted)
            plates.isEmpty() -> Text(L("Nur die Stange", "Just the bar"), color = Ink.muted)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(18.dp).height(8.dp).background(Ink.faint))
                plates.forEach { p ->
                    val color = when (p) {
                        25.0 -> Ink.red
                        20.0 -> Color(0xFF5B8CFF)
                        15.0 -> Color(0xFFFFD43B)
                        10.0 -> Ink.accent
                        else -> Ink.text
                    }
                    Box(Modifier.width(if (p >= 10) 12.dp else 8.dp).height((24 + p * 2.2).dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
                Spacer(Modifier.width(10.dp))
                Text(plates.joinToString(" + ") { Stats.fmtKg(it) }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(Pill).background(Ink.surface).clickable { Haptics.click(); onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CoachLine(tip: Coach.Tip) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink.surface).padding(12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = if (tip.tone == Coach.Tone.Push) Ink.accent else Ink.mauve, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("${tip.title}: ${tip.body}", style = MaterialTheme.typography.bodySmall)
    }
}

/** A value with − and + on either side; tapping the value opens exact entry. */
@Composable
private fun ValueCell(value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(32.dp).clip(Pill).clickable { Haptics.tick(); onMinus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, L("Weniger", "Less"), tint = Ink.faint, modifier = Modifier.size(18.dp))
        }
        // A fixed-width target, so even a short value like "–" is easy to hit.
        Box(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).clickable { Haptics.click(); onEdit() },
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
        Box(Modifier.size(32.dp).clip(Pill).clickable { Haptics.tick(); onPlus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, L("Mehr", "More"), tint = Ink.faint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddPage(w: Workout, nav: NavController) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${w.doneSets.size}", style = MaterialTheme.typography.displayLarge, color = Ink.accent)
        Text(L("Sätze erledigt", "sets done"), color = Ink.muted)
        if (w.volume > 0) {
            Spacer(Modifier.height(12.dp))
            Text(Stats.fmtVolume(w.volume) + L(" bewegt", " lifted"), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(32.dp))
        GhostButton(L("Übung hinzufügen", "Add exercise"), Modifier.fillMaxWidth(), Icons.Rounded.Add) { nav.navigate("pick/workout") }
    }
}

@Composable
private fun RestBar(leftMs: Long, totalSec: Int) {
    Row(
        Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Ink.raised).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(leftMs / (totalSec * 1000f).coerceAtLeast(1f), Modifier.size(56.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(L("Pause", "Rest"), color = Ink.accent)
            Text(clock(leftMs), style = MaterialTheme.typography.headlineMedium)
        }
        Box(
            Modifier.clip(Pill).background(Ink.surface).clickable {
                Haptics.tick()
                App.store.updateActive { it.copy(restEndsAt = (it.restEndsAt ?: System.currentTimeMillis()) + 15_000, restTotalSec = it.restTotalSec + 15) }
            }.padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text("+15 s", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(8.dp))
        RoundButton(Icons.Rounded.SkipNext, L("Pause überspringen", "Skip rest"), size = 44.dp) { App.store.updateActive { it.copy(restEndsAt = null) } }
    }
}

@Composable
private fun FinishDialog(w: Workout, data: AppData, onDismiss: () -> Unit, onFinish: () -> Unit) {
    // A record is a done set whose estimated 1RM beats every earlier session of that exercise.
    val records = w.entries.mapNotNull { e ->
        val best = e.sets.filter { it.done && it.weight > 0 }.maxByOrNull { Stats.e1rm(it) } ?: return@mapNotNull null
        val previous = Stats.sessions(data.history, e.exerciseId).maxOfOrNull { it.e1rm } ?: return@mapNotNull null
        if (Stats.e1rm(best) > previous) (App.repo[e.exerciseId]?.title ?: e.exerciseId) to best else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EmojiEvents, null, tint = Ink.accent) },
        title = { Text(L("Training beenden?", "Finish workout?")) },
        text = {
            Column {
                Text(
                    L(
                        "${w.doneSets.size} Sätze · ${Stats.fmtVolume(w.volume)} · ${Stats.fmtDuration(System.currentTimeMillis() - w.startedAt)}",
                        "${w.doneSets.size} sets · ${Stats.fmtVolume(w.volume)} · ${Stats.fmtDuration(System.currentTimeMillis() - w.startedAt)}",
                    )
                )
                if (records.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Eyebrow(L("Neue Rekorde", "New records"), color = Ink.accent)
                    records.forEach { (name, s) -> Text("$name — ${Stats.fmtKg(s.weight)} kg × ${s.reps}") }
                }
                if (w.doneSets.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(L("Noch kein Satz abgehakt – das Training wird nicht gespeichert.", "No set ticked off yet – the workout will not be saved."), color = Ink.coral)
                }
            }
        },
        confirmButton = { TextButton(onClick = onFinish) { Text(if (w.doneSets.isEmpty()) L("Beenden", "End") else L("Speichern", "Save"), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Weiter", "Continue")) } },
        containerColor = Ink.raised,
    )
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private fun vibrate(context: Context, pattern: LongArray) {
    val vm = context.getSystemService(VibratorManager::class.java) ?: return
    vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
