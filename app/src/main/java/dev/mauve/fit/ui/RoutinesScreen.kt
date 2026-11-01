package dev.mauve.fit.ui

import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import dev.mauve.fit.data.PlanExercise
import dev.mauve.fit.data.Routine
import dev.mauve.fit.data.Supersets
import dev.mauve.fit.data.Templates
import dev.mauve.fit.data.linkedWithNext
import dev.mauve.fit.data.toggleSupersetWithNext
import dev.mauve.fit.data.withSupersetsCleaned

@Composable
fun RoutinesScreen(data: AppData, nav: NavController) {
    var chooseTemplate by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenTitle(L("Pläne und Übungen", "Plans and exercises"), L("Training", "Training"), Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutTile(L("Schnelltraining", "Quick workout"), L("Coach baut es", "Coach builds it"), Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { nav.navigate("quick") }
                    ShortcutTile(L("Übungen", "Exercises"), L("${App.repo.all.size} mit Animation", "${App.repo.all.size} animated"), Icons.Rounded.Search, Modifier.weight(1f)) { nav.navigate("library") }
                }
                ToolsTile { nav.navigate("tools") }
            }
        }
        item { CardioCard(data) }
        item { BlockPlanner(data) }
        if (data.active == null) item {
            GhostButton(L("Freies Training starten", "Start a free workout"), Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow) {
                App.store.startWorkout(null)
                nav.navigate("workout")
            }
        }
        item {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(L("${data.routines.size} Pläne", "${data.routines.size} plans"))
                    Text(L("Deine Pläne", "Your plans"), style = MaterialTheme.typography.headlineSmall)
                }
                RoundButton(Icons.Rounded.Add, L("Neuer Plan", "New plan"), size = 44.dp) { nav.navigate("routine/new") }
            }
        }
        if (data.routines.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Text(L("Noch kein Plan", "No plan yet"), style = MaterialTheme.typography.titleLarge)
                Text(L("Lege einen Plan an und füge Übungen aus der Bibliothek hinzu.", "Create a plan and add exercises from the library."), color = Ink.muted)
            }
        }
        items(data.routines, key = { it.id }) { r ->
            var menu by remember { mutableStateOf(false) }
            var confirm by remember { mutableStateOf(false) }
            Box {
            Card(onClick = { nav.navigate("routine/${r.id}") }, onLongClick = { menu = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleLarge)
                        val muscles = r.exercises.mapNotNull { App.repo[it.exerciseId]?.target }.distinct()
                        Text(
                            r.note.ifBlank { muscles.take(4).joinToString(" · ") { Labels.muscle(it) }.ifBlank { L("Leer", "Empty") } },
                            color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (data.active == null && r.exercises.isNotEmpty()) RoundButton(Icons.Rounded.PlayArrow, L("Starten", "Start"), size = 46.dp) {
                        App.store.startWorkout(r)
                        nav.navigate("workout")
                    }
                }
                if (r.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.exercises.take(5).forEach { Thumb(App.repo[it.exerciseId], 50.dp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        L(
                            "${r.exercises.size} Übungen · ${r.exercises.sumOf { it.sets }} Sätze · ca. ${estMinutes(r)} min",
                            "${r.exercises.size} exercises · ${r.exercises.sumOf { it.sets }} sets · about ${estMinutes(r)} min",
                        ),
                        color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Press and hold: the plan's actions without opening it.
            DropdownMenu(menu, { menu = false }, containerColor = Ink.raised) {
                if (data.active == null && r.exercises.isNotEmpty()) DropdownMenuItem({ Text(L("Starten", "Start")) }, {
                    menu = false; App.store.startWorkout(r); nav.navigate("workout")
                }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) })
                DropdownMenuItem({ Text(L("Bearbeiten", "Edit")) }, { menu = false; nav.navigate("routine/${r.id}") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                DropdownMenuItem({ Text(L("Duplizieren", "Duplicate")) }, { menu = false; Haptics.click(); App.store.duplicateRoutine(r.id) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
                DropdownMenuItem({ Text(L("Löschen", "Delete"), color = Ink.red) }, { menu = false; confirm = true },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Ink.red) })
            }
            }
            if (confirm) AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(L("Plan löschen?", "Delete plan?")) },
                text = { Text(L("„${r.name}“ wird entfernt. Dein Verlauf bleibt erhalten.", "“${r.name}” will be removed. Your history stays.")) },
                confirmButton = { TextButton(onClick = { Haptics.warn(); App.store.deleteRoutine(r.id); confirm = false }) { Text(L("Löschen", "Delete"), color = Ink.red) } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(L("Abbrechen", "Cancel")) } },
                containerColor = Ink.raised,
            )
        }
        item {
            GhostButton(L("Vorlage hinzufügen", "Add template"), Modifier.fillMaxWidth(), Icons.Rounded.LibraryAdd) { chooseTemplate = true }
        }
    }

    if (chooseTemplate) TemplateDialog(onDismiss = { chooseTemplate = false }) { pack ->
        App.store.addTemplate(pack)
        Haptics.success()
        chooseTemplate = false
    }
}

/** Ready-made splits; picking one adds its plans next to the user's own. */
@Composable
private fun TemplateDialog(onDismiss: () -> Unit, onPick: (Templates.Pack) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Vorlage hinzufügen", "Add template")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("Die Pläne kommen zu deinen dazu, du kannst sie danach frei bearbeiten.", "The plans are added to yours; edit them freely afterwards."), color = Ink.muted)
                Templates.packs.forEach { pack ->
                    val plans = remember(pack.key) { pack.routines() }
                    Card(Modifier.fillMaxWidth(), onClick = { onPick(pack) }, color = Ink.surface, padding = PaddingValues(16.dp)) {
                        Text(pack.name, style = MaterialTheme.typography.titleLarge)
                        Text(pack.blurb, color = Ink.muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(plans.joinToString(" · ") { it.name }, color = Ink.faint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** 90 -> "1:30", 45 -> "45 s". */
fun restLabel(sec: Int): String = if (sec < 60) "$sec s" else "%d:%02d".format(sec / 60, sec % 60)

/** Edits a plan in place: every change goes straight to the store. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoutineEditScreen(id: String, data: AppData, nav: NavController) {
    var replacing by remember { mutableStateOf<Int?>(null) }
    var noting by remember { mutableStateOf<String?>(null) }
    if (id == "new") {
        LaunchedEffect(Unit) {
            val r = Routine(name = L("Neuer Plan", "New plan"))
            App.store.saveRoutine(r)
            nav.navigate("routine/${r.id}") { popUpTo("routine/new") { inclusive = true } }
        }
        return
    }
    val r = data.routines.firstOrNull { it.id == id } ?: return
    noting?.let { id -> NoteDialog(id, data.exerciseNotes[id].orEmpty()) { noting = null } }
    replacing?.let { i ->
        r.exercises.getOrNull(i)?.let { p ->
            SwapDialog(p.exerciseId, data, L("Übung ersetzen", "Replace exercise"), onPick = { App.store.replaceInRoutine(r.id, i, it); replacing = null }) { replacing = null }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    // Moves and removals can break a superset apart; cleaning keeps only real neighbours linked.
    fun save(block: (Routine) -> Routine) = App.store.saveRoutine(block(r).let { it.copy(exercises = it.exercises.withSupersetsCleaned()) })
    val groups = r.exercises.map { it.superset }
    fun setAt(i: Int, p: PlanExercise) = save { it.copy(exercises = it.exercises.toMutableList().also { l -> l[i] = p }) }
    fun move(from: Int, to: Int) = save { it.copy(exercises = it.exercises.toMutableList().also { l -> l.add(to, l.removeAt(from)) }) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Row(
                Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                RoundButton(Icons.Rounded.DeleteOutline, L("Plan löschen", "Delete plan"), size = 44.dp) { confirmDelete = true }
            }
        }
        item {
            Eyebrow(L("Plan bearbeiten", "Edit plan"))
            BasicTextField(
                r.name, { v -> save { it.copy(name = v) } },
                textStyle = MaterialTheme.typography.displaySmall.copy(color = Ink.text),
                cursorBrush = SolidColor(Ink.accent), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            BasicTextField(
                r.note, { v -> save { it.copy(note = v) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.muted),
                cursorBrush = SolidColor(Ink.accent), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                decorationBox = { inner -> if (r.note.isEmpty()) Text(L("Notiz hinzufügen", "Add a note"), color = Ink.faint); inner() },
            )
        }
        itemsIndexed(r.exercises, key = { i, p -> "$i-${p.exerciseId}" }) { i, p ->
            val e = App.repo[p.exerciseId]
            val linked = r.exercises.linkedWithNext(i)
            Card(padding = PaddingValues(14.dp)) {
                Supersets.label(groups, i)?.let { label ->
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = Ink.mauve, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Eyebrow(L("Supersatz $label", "Superset $label"), color = Ink.mauve)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(e, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    // The exercise's own note (seat height, grip...) sits right under its name.
                    val note = data.exerciseNotes[p.exerciseId].orEmpty()
                    Column(Modifier.weight(1f)) {
                        Text(
                            e?.title ?: p.exerciseId, Modifier.clickable { nav.navigate("exercise/${p.exerciseId}") },
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (note.isNotBlank()) Text(
                            note, Modifier.padding(top = 2.dp).clickable { noting = p.exerciseId },
                            color = Ink.accent, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Reorder and remove live in one menu so the name keeps its room.
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        RoundButton(Icons.Rounded.MoreVert, L("Optionen", "Options"), size = 38.dp) { menu = true }
                        DropdownMenu(menu, { menu = false }, containerColor = Ink.raised) {
                            if (i > 0) DropdownMenuItem({ Text(L("Nach oben", "Move up")) }, { menu = false; move(i, i - 1) },
                                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) })
                            if (i < r.exercises.lastIndex) DropdownMenuItem({ Text(L("Nach unten", "Move down")) }, { menu = false; move(i, i + 1) },
                                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) })
                            if (i < r.exercises.lastIndex) DropdownMenuItem(
                                { Text(if (linked) L("Supersatz lösen", "Unlink superset") else L("Mit nächster Übung als Supersatz", "Superset with next")) },
                                { menu = false; Haptics.click(); save { it.copy(exercises = it.exercises.toggleSupersetWithNext(i)) } },
                                leadingIcon = { Icon(if (linked) Icons.Rounded.LinkOff else Icons.Rounded.Link, null) },
                            )
                            DropdownMenuItem({ Text(L("Notiz", "Note")) }, { menu = false; noting = p.exerciseId },
                                leadingIcon = { Icon(Icons.Rounded.EditNote, null) })
                            DropdownMenuItem({ Text(L("Ersetzen", "Replace")) }, { menu = false; replacing = i },
                                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) })
                            DropdownMenuItem({ Text(L("Entfernen", "Remove"), color = Ink.red) }, {
                                menu = false
                                save { it.copy(exercises = it.exercises.filterIndexed { j, _ -> j != i }) }
                            }, leadingIcon = { Icon(Icons.Rounded.Close, null, tint = Ink.red) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper(L("Sätze", "Sets"), "${p.sets}", Modifier.weight(1f), { setAt(i, p.copy(sets = (p.sets - 1).coerceAtLeast(1))) }) { setAt(i, p.copy(sets = (p.sets + 1).coerceAtMost(10))) }
                    Stepper(L("Wdh.", "Reps"), "${p.reps}", Modifier.weight(1f), { setAt(i, p.copy(reps = (p.reps - 1).coerceAtLeast(1))) }) { setAt(i, p.copy(reps = p.reps + 1)) }
                    Stepper(L("Pause", "Rest"), restLabel(p.restSec), Modifier.weight(1f), { setAt(i, p.copy(restSec = (p.restSec - 15).coerceAtLeast(0))) }) { setAt(i, p.copy(restSec = p.restSec + 15)) }
                }
                if (linked) Text(
                    L("Im Wechsel mit der nächsten Übung, Pause erst danach", "Alternates with the next exercise, rest comes after it"),
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = Ink.mauve,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(L("Übung", "Exercise"), Modifier.weight(1f), Icons.Rounded.Add) { nav.navigate("pick/${r.id}") }
                if (data.active == null && r.exercises.isNotEmpty()) PrimaryButton(L("Starten", "Start"), Modifier.weight(1f), Icons.Rounded.PlayArrow) {
                    App.store.startWorkout(r)
                    nav.navigate("workout") { popUpTo("routine/${r.id}") { inclusive = true } }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(L("Plan löschen?", "Delete plan?")) },
        text = { Text(L("„${r.name}“ wird entfernt. Dein Verlauf bleibt erhalten.", "“${r.name}” will be removed. Your history stays.")) },
        confirmButton = {
            TextButton(onClick = { Haptics.warn(); confirmDelete = false; nav.popBackStack(); App.store.deleteRoutine(r.id) }) { Text(L("Löschen", "Delete"), color = Ink.red) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/**
 * Training block: plan the next weeks as phases instead of the same session every week. While a
 * block runs, starting any plan applies the week's sets, reps and load.
 */
@Composable
private fun BlockPlanner(data: AppData) {
    val running = Coach.blockWeek(data)
    if (running != null) {
        BlockCard(running)
        Spacer(Modifier.height(8.dp))
        GhostButton(L("Block beenden", "End block"), Modifier.fillMaxWidth()) { Haptics.warn(); App.store.endBlock() }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(L("Trainingsblock", "Training block"))
        Text(L("Plane die nächsten Wochen", "Plan the next weeks"), style = MaterialTheme.typography.titleLarge)
        Text(
            L("Grundlage, Aufbau, Spitze, Entlastung: deine Pläne passen sich jede Woche automatisch an.",
                "Base, build, peak, deload: your plans adjust automatically each week."),
            color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(L("4 Wochen", "4 weeks"), Modifier.weight(1f)) { Haptics.success(); App.store.startBlock(4) }
            GhostButton(L("6 Wochen", "6 weeks"), Modifier.weight(1f)) { Haptics.success(); App.store.startBlock(6) }
        }
    }
}
