package dev.mauve.fit.ui

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
    val tip = remember(data.history, data.settings) { Coach.homeTip(data, App.repo) }

    fun start(r: Routine?) {
        App.store.startWorkout(r)
        nav.navigate("workout")
    }

    Box(Modifier.fillMaxSize()) {
    HeroGrid(Modifier.fillMaxWidth().height(320.dp))
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
                    is Coach.Action.AddExercise -> App.store.addToRoutine(action.routineId, action.exerciseId)
                }
            }
        }

        if (data.active == null && next != null) item {
            NextWorkoutCard(next, onShort = {
                App.store.startWorkout(next, short = true)
                nav.navigate("workout")
            }) { start(next) }
        }

        if (data.active == null) item {
            Card(onClick = { nav.navigate("quick") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(Pill).background(Ink.raised), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Ink.accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("Schnelltraining", "Quick workout"), style = MaterialTheme.typography.titleLarge)
                        Text(L("Bereich und Zeit wählen, der Coach baut das Training", "Pick a focus and time, the coach builds the workout"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.muted)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("$thisWeek/$goal", L("diese Woche", "this week"), Modifier.weight(1f), if (thisWeek >= goal) Ink.accent else Ink.text)
                Stat("${Stats.weekStreak(history, goal)}", L("Serie", "streak"), Modifier.weight(1f), Ink.coral)
                Stat(Stats.fmtVolume(volume.last().second), L("Volumen", "volume"), Modifier.weight(1f))
            }
        }

        item {
            Card(onClick = { nav.navigate("nutrition") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(Pill).background(Ink.raised), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Restaurant, null, tint = Ink.accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("Ernährung und Meal Prep", "Nutrition and meal prep"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            data.bodyWeights.firstOrNull()?.let { Nutrition.targets(it.kg, data.profile.goal) }
                                ?.let { L("${it.kcal} kcal · ${it.protein} g Protein am Tag", "${it.kcal} kcal · ${it.protein} g protein a day") }
                                ?: L("Tagesziele, Rezepte, Einkaufsliste", "Daily targets, recipes, shopping list"),
                            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.muted)
                }
            }
        }

        if (history.isNotEmpty()) item { RecoveryCard(Progress.recovery(history, App.repo)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(L("Pläne", "Plans"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (data.active == null) Text(
                    L("Freies Training", "Free workout"), color = Ink.accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { start(null) },
                )
            }
        }
        data.routines.forEach { r ->
            item(key = r.id) {
                Card(onClick = { nav.navigate("routine/${r.id}") }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(App.repo[r.exercises.firstOrNull()?.exerciseId.orEmpty()], 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleLarge)
                            Text(L("${r.exercises.size} Übungen · ${estMinutes(r)} min", "${r.exercises.size} exercises · ${estMinutes(r)} min"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (data.active == null) RoundButton(Icons.Rounded.PlayArrow, L("Starten", "Start")) { start(r) }
                    }
                }
            }
        }

        item {
            Card(Modifier.padding(top = 8.dp)) {
                Eyebrow(L("Volumen pro Woche", "Volume per week"))
                Spacer(Modifier.height(14.dp))
                Bars(volume.map { it.second.toFloat() }, volume.map { L("KW", "W") + it.first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) })
            }
        }

        val muscles = Stats.muscleSets(history, App.repo).entries.sortedByDescending { it.value }.take(6)
        if (muscles.isNotEmpty()) item {
            Card {
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
    Card(padding = PaddingValues(0.dp)) {
        Box {
            ExerciseMedia(hero, Modifier.fillMaxWidth().aspectRatio(1.6f), corner = 0.dp)
            Box(
                Modifier.padding(14.dp).clip(RoundedCornerShape(10.dp)).background(Ink.bg).padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(L("NÄCHSTES TRAINING", "NEXT WORKOUT"), style = MaterialTheme.typography.labelSmall, color = Ink.accent) }
        }
        Column(Modifier.padding(18.dp)) {
            Text(r.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(r.note.ifBlank { null }, L("${r.exercises.size} Übungen", "${r.exercises.size} exercises"), "${estMinutes(r)} min").joinToString(" · "),
                color = Ink.muted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.exercises.drop(1).take(5).forEach { Thumb(App.repo[it.exerciseId], 46.dp) }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(L("Training starten", "Start workout"), Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.ArrowForward, onStart)
            Spacer(Modifier.height(8.dp))
            // For days with little time: the coach trims the plan.
            val short = Coach.shortVersion(r)
            GhostButton(L("Wenig Zeit? Kurzversion · ${estMinutes(short)}\u00A0min", "Short on time? Quick version · ${estMinutes(short)}\u00A0min"),
                Modifier.fillMaxWidth(), onClick = onShort)
        }
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
