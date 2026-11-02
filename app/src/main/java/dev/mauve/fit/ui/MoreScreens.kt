package dev.mauve.fit.ui

import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.rememberCoroutineScope
import dev.mauve.fit.data.Motivation
import dev.mauve.fit.system.ShareImage
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import dev.mauve.fit.data.Stats

/** Shown after saving a workout: the numbers, new records and what was trained. */
@Composable
fun SummaryScreen(id: String, data: AppData, nav: NavController) {
    val w = data.history.firstOrNull { it.id == id } ?: return
    var feel by rememberSaveable { mutableIntStateOf(w.feel) }
    var note by rememberSaveable { mutableStateOf(w.note) }
    // Saved when the summary goes away, not only on "Done": leaving with Back lost both.
    val latestFeel by androidx.compose.runtime.rememberUpdatedState(feel)
    val latestNote by androidx.compose.runtime.rememberUpdatedState(note)
    androidx.compose.runtime.DisposableEffect(w.id) {
        onDispose { if (latestFeel != w.feel || latestNote.trim() != w.note) App.store.setFeel(w.id, latestFeel, latestNote.trim()) }
    }
    val earlier = data.history.filter { it.startedAt < w.startedAt }
    val records = w.entries.mapNotNull { e ->
        val best = e.sets.filter { !it.warmup && it.weight > 0 }.maxByOrNull { Stats.e1rm(it) } ?: return@mapNotNull null
        val previous = Stats.sessions(earlier, e.exerciseId).maxOfOrNull { it.e1rm } ?: return@mapNotNull null
        if (Stats.e1rm(best) > previous + 0.01) e.exerciseId to best else null
    }
    val muscles = w.entries.groupBy { App.repo[it.exerciseId]?.target ?: "" }
        .mapValues { (_, es) -> es.sumOf { e -> e.sets.count { !it.warmup } } }.entries.sortedByDescending { it.value }
    // The weekly challenge, and whether this very workout completed it.
    val challenge = remember(data.history, data.settings, data.profile) { Motivation.challenge(data, App.repo) }
    val challengeWon = remember(id, challenge) {
        challenge.done && !Motivation.challenge(data.copy(history = data.history.filterNot { it.id == id }), App.repo).done
    }
    LaunchedEffect(id) {
        if (records.isNotEmpty()) { kotlinx.coroutines.delay(350); Haptics.record() }
        if (challenge.done && App.store.completeChallenge(challenge.week)) {
            kotlinx.coroutines.delay(if (records.isNotEmpty()) 900 else 350)
            Haptics.success()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun shareImage() {
        // New records first; without any, the best set of each exercise.
        val rows = records.ifEmpty {
            w.entries.groupBy { it.exerciseId }.mapNotNull { (exId, es) ->
                es.flatMap { it.sets }.filter { !it.warmup }.maxByOrNull { Stats.e1rm(it) * 1000 + it.reps }?.let { exId to it }
            }.sortedByDescending { (_, s) -> Stats.e1rm(s) }
        }.take(4).map { (exId, s) -> (App.repo[exId]?.title ?: exId) to ShareImage.setText(s) }
        val card = ShareImage.Card(
            title = w.name,
            date = Stats.day(w.startedAt).format(java.time.format.DateTimeFormatter.ofPattern(L("EEEE, d. MMMM yyyy", "EEEE, MMMM d, yyyy"), dev.mauve.fit.data.I18n.locale)),
            stats = listOf(
                Stats.fmtDuration(w.durationMs) to L("Dauer", "Duration"),
                "${w.doneSets.size}" to if (w.doneSets.size == 1) L("Satz", "Set") else L("Sätze", "Sets"),
                Stats.headline(w) to if (w.volume > 0) L("Volumen", "Volume") else L("Gesamt", "Total"),
            ),
            heading = if (records.isNotEmpty()) L("Neue Rekorde", "New records") else L("Beste Sätze", "Best sets"),
            rows = rows,
        )
        scope.launch { runCatching { ShareImage.share(context, card) } }
    }
    fun share() {
        val lines = buildList {
            add("${w.name} · ${Stats.day(w.startedAt).format(java.time.format.DateTimeFormatter.ofPattern(if (dev.mauve.fit.data.I18n.german) "d. MMMM" else "MMMM d"))}")
            add(L("${Stats.fmtDuration(w.durationMs)} · ${Stats.sets(w.doneSets.size)} · ${Stats.fmtVolume(w.volume)} bewegt",
                "${Stats.fmtDuration(w.durationMs)} · ${Stats.sets(w.doneSets.size)} · ${Stats.fmtVolume(w.volume)} lifted"))
            add("")
            w.entries.filter { e -> e.sets.any { it.done } }.forEach { e ->
                val ex = App.repo[e.exerciseId]
                add("${ex?.title ?: e.exerciseId}: " + e.sets.filter { it.done && !it.warmup }.joinToString("  ") { Stats.setLabel(it, ex?.bodyweight == true) })
            }
            if (records.isNotEmpty()) {
                add("")
                add(L("Neue Rekorde: ", "New records: ") + records.joinToString(", ") { (exId, _) -> App.repo[exId]?.title ?: exId })
            }
            add("")
            add(L("Mit Mauve Fit trainiert", "Trained with Mauve Fit"))
        }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, lines.joinToString("\n"))
        context.startActivity(android.content.Intent.createChooser(send, L("Training teilen", "Share workout")))
    }
    val headline = when {
        records.isNotEmpty() -> L("Rekordtag.", "Record day.")
        w.doneSets.size >= 20 -> L("Stark durchgezogen.", "Strong session.")
        else -> L("Erledigt.", "Done.")
    }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Eyebrow(w.name, color = Ink.accent)
            Text(headline, style = MaterialTheme.typography.displayMedium)
            Text(
                L(
                    "Einheit Nr. ${data.history.size} · ${Stats.workoutsThisWeek(data.history)} von ${data.settings.weeklyGoal} diese Woche",
                    "Session #${data.history.size} · ${Stats.workoutsThisWeek(data.history)} of ${data.settings.weeklyGoal} this week",
                ),
                color = Ink.muted,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Stats.fmtDuration(w.durationMs), L("Dauer", "Duration"), Modifier.weight(1f))
                Stat("${w.doneSets.size}", if (w.doneSets.size == 1) L("Satz", "Set") else L("Sätze", "Sets"), Modifier.weight(1f))
                Stat(Stats.fmtVolume(w.volume), L("Volumen", "Volume"), Modifier.weight(1f), Ink.accent)
            }
        }
        item {
            GhostButton(L("Als Bild teilen", "Share as image"), Modifier.fillMaxWidth(), Icons.Rounded.Image) { shareImage() }
            Spacer(Modifier.height(10.dp))
            GhostButton(L("Training teilen", "Share workout"), Modifier.fillMaxWidth(), Icons.Rounded.Share) { Haptics.click(); share() }
        }
        if (challengeWon) item {
            Card(color = Ink.raised) {
                Eyebrow(L("Wochen-Challenge geschafft", "Weekly challenge done"), color = Ink.accent)
                Text(challenge.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    L("Damit hast du ${data.challengesDone.size.coerceAtLeast(1)} Challenges geschafft. Nächsten Montag wartet die nächste.",
                        "That makes ${data.challengesDone.size.coerceAtLeast(1)} challenges done. A new one waits next Monday."),
                    color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (records.isNotEmpty()) item {
            Card(color = Ink.accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Ink.bg)
                    Spacer(Modifier.width(8.dp))
                    Eyebrow(L("Neue Rekorde", "New records"), color = Ink.bg)
                }
                Spacer(Modifier.height(8.dp))
                records.forEach { (exId, s) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(App.repo[exId]?.title ?: exId, Modifier.weight(1f), color = Ink.bg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${Stats.fmtKg(s.weight)} kg × ${s.reps}", color = Ink.bg)
                    }
                }
            }
        }
        item {
            Card {
                Eyebrow(L("Trainierte Muskeln", "Muscles trained"))
                Spacer(Modifier.height(10.dp))
                val max = muscles.firstOrNull()?.value?.toFloat() ?: 1f
                muscles.forEach { (m, n) ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Labels.muscle(m), Modifier.width(118.dp))
                        Meter(n / max, Modifier.weight(1f))
                        Text("$n", Modifier.width(34.dp), textAlign = TextAlign.End, color = Ink.muted)
                    }
                }
            }
        }
        item {
            Card {
                Eyebrow(L("Wie war's?", "How was it?"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to L("Leicht", "Easy"), 2 to L("Gut", "Good"), 3 to L("Hart", "Hard")).forEach { (v, label) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (feel == v) Ink.accent else Ink.raised)
                                .clickable { Haptics.tick(); feel = if (feel == v) 0 else v }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (feel == v) Ink.bg else Ink.text, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.text.BasicTextField(
                    note, { note = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.raised).padding(14.dp),
                    decorationBox = { inner -> if (note.isEmpty()) Text(L("Notiz, z. B. Schulter gezwickt", "Note, e.g. shoulder twinged"), color = Ink.faint); inner() },
                )
            }
        }
        item {
            PrimaryButton(L("Fertig", "Done"), Modifier.fillMaxWidth().padding(top = 8.dp)) {
                App.store.setFeel(w.id, feel, note.trim())
                nav.navigate("home") { popUpTo("home") { inclusive = true } }
            }
        }
    }
}

/** The coach builds a one-off workout from a body region and a time budget. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickWorkoutScreen(data: AppData, nav: NavController) {
    var region by rememberSaveable { mutableStateOf(Coach.regions.keys.first()) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var seed by rememberSaveable { mutableIntStateOf(1) }
    var saved by remember { mutableStateOf(false) }
    val routine = remember(region, minutes, seed, data.profile) { Coach.quickWorkout(App.repo, data.profile, region, minutes, seed) }

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
        item {
            ScreenTitle("Coach", L("Schnelltraining", "Quick workout"))
            Text(
                L(
                    "Wähle Bereich und Zeit, der Coach stellt passende Übungen für ${data.profile.setup.label.lowercase()} zusammen.",
                    "Pick a focus and time, the coach puts together matching exercises for ${data.profile.setup.label.lowercase()}.",
                ),
                color = Ink.muted,
            )
        }
        item {
            Eyebrow(L("Bereich", "Focus"), Modifier.padding(top = 8.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Coach.regions.keys.toList()) { r -> Chip(Coach.regionLabel(r), r == region) { region = r; saved = false } }
            }
            Eyebrow(L("Dauer", "Duration"), Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { m -> Chip("$m min", m == minutes) { minutes = m; saved = false } }
            }
        }
        item {
            Card(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(routine.name, style = MaterialTheme.typography.titleLarge)
                        Text(L("${routine.exercises.size} Übungen · ca. ${estMinutes(routine)} min", "${routine.exercises.size} exercises · about ${estMinutes(routine)} min"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    RoundButton(Icons.Rounded.Refresh, L("Neu würfeln", "Shuffle")) { seed++; saved = false }
                }
                Spacer(Modifier.height(10.dp))
                routine.exercises.forEach { p ->
                    val e = App.repo[p.exerciseId]
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(e, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e?.title ?: p.exerciseId, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(e?.let { Labels.muscle(it.target) }.orEmpty(), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${p.sets} × ${p.reps}", color = Ink.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(if (saved) L("Gespeichert", "Saved") else L("Als Plan", "Save as plan"), Modifier.weight(1f), Icons.Rounded.BookmarkAdd) {
                    if (!saved) { App.store.saveRoutine(routine.copy(id = dev.mauve.fit.data.newId())); saved = true }
                }
                // A workout already running would win and this one would be lost silently.
                val busy = data.active != null
                PrimaryButton(L("Starten", "Start"), Modifier.weight(1f), Icons.Rounded.PlayArrow, enabled = !busy && routine.exercises.isNotEmpty()) {
                    App.store.startWorkout(routine)
                    nav.navigate("workout") { popUpTo("quick") { inclusive = true } }
                }
            }
            if (data.active != null) Text(
                L("Es läuft schon ein Training. Beende es zuerst oder speichere das hier als Plan.",
                    "A workout is already running. Finish it first or save this one as a plan."),
                Modifier.padding(top = 8.dp), color = Ink.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Card for the coach's message; colour follows the tone. */
@Composable
fun CoachCard(tip: Coach.Tip, modifier: Modifier = Modifier, onAction: ((Coach.Action) -> Unit)? = null) {
    val accent = when (tip.tone) {
        Coach.Tone.Push -> Ink.accent
        Coach.Tone.Rest -> Ink.mauve
        Coach.Tone.Win -> Ink.coral
        Coach.Tone.Info -> Ink.text
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.surface).padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(38.dp).clip(Pill).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow("Coach · ${tip.title}", color = accent)
            Spacer(Modifier.height(4.dp))
            Text(tip.body, style = MaterialTheme.typography.bodyMedium)
            val action = tip.action
            if (action != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(Pill).background(accent).clickable { Haptics.click(); onAction(action) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(action.label, color = Ink.bg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
