package dev.mauve.fit.ui

import dev.mauve.fit.system.HealthSync
import androidx.compose.material.icons.rounded.Sync

import dev.mauve.fit.data.Kit

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.PermissionController
import dev.mauve.fit.App
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.Focus
import dev.mauve.fit.data.Goal
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Level
import dev.mauve.fit.data.Limit
import dev.mauve.fit.data.Profile
import dev.mauve.fit.data.Setup
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.system.Recovery
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Set once the intro asked for notifications, so the app does not ask again right after it. */
var introAskedNotifications = false

private const val WELCOME = 0
private const val GOAL = 1
private const val LEVEL = 2
private const val DAYS = 3
private const val PLACE = 4
private const val FOCUS = 5
private const val LIMITS = 6
private const val BODY = 7
private const val CONNECT = 8
private const val PLAN = 9

/** Session lengths offered; 0 (none picked) lets the goal decide. */
private val lengths = listOf(30, 45, 60, 75)

/**
 * First-run intro: a welcome page, then one question per screen, then the generated program to
 * confirm. With [redo] (from the settings) it starts at the first question, prefilled from
 * [initial], and the back button at the start leaves without changing anything.
 */
@Composable
fun IntroScreen(initial: Profile, redo: Boolean = false, onDone: () -> Unit) {
    val first = if (redo) GOAL else WELCOME
    val start = remember { App.store.data.value }
    var step by rememberSaveable { mutableIntStateOf(first) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }
    var level by rememberSaveable { mutableStateOf(initial.level) }
    var days by rememberSaveable { mutableIntStateOf(initial.days) }
    var minutes by rememberSaveable { mutableIntStateOf(initial.sessionMinutes) }
    var setup by rememberSaveable { mutableStateOf(initial.setup) }
    var kit by rememberSaveable { mutableStateOf(initial.kit) }
    var focus by rememberSaveable { mutableStateOf(initial.focus) }
    var limits by rememberSaveable { mutableStateOf(initial.limits) }
    // Body data as typed, so a half-written "72," survives; parsed when the plan is taken over.
    var weight by rememberSaveable { mutableStateOf(start.bodyWeights.firstOrNull()?.kg?.let(Stats::fmtKg).orEmpty()) }
    var goalWeight by rememberSaveable { mutableStateOf(start.settings.goalWeight.takeIf { it > 0 }?.let(Stats::fmtKg).orEmpty()) }
    var height by rememberSaveable { mutableStateOf(initial.heightCm.takeIf { it > 0 }?.toString().orEmpty()) }
    var born by rememberSaveable { mutableStateOf(initial.birthYear.takeIf { it > 0 }?.toString().orEmpty()) }
    var remind by rememberSaveable { mutableStateOf(start.settings.reminder) }
    var remindDays by rememberSaveable { mutableStateOf(start.settings.reminderDays) }
    var remindHour by rememberSaveable { mutableIntStateOf(start.settings.reminderHour) }
    var remindMinute by rememberSaveable { mutableIntStateOf(start.settings.reminderMinute) }
    var seed by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > first) { step-- }

    fun profile() = initial.copy(
        goal = goal, level = level, days = days, setup = setup, kit = kit,
        sessionMinutes = minutes, focus = focus, limits = limits,
        heightCm = height.toIntOrNull()?.takeIf { it in 100..250 } ?: 0,
        birthYear = born.toIntOrNull()?.takeIf { it in 1920..LocalDate.now().year - 10 } ?: 0,
    )

    fun finish() {
        val p = profile()
        val program = Coach.buildProgram(App.repo, p, seed)
        val kg = kilos(weight)
        if (kg != null && kg != App.store.data.value.bodyWeights.firstOrNull()?.kg) App.store.addBodyWeight(kg)
        App.store.updateSettings {
            it.copy(
                goalWeight = kilos(goalWeight) ?: 0.0,
                reminder = remind, reminderDays = remindDays.ifEmpty { it.reminderDays },
                reminderHour = remindHour, reminderMinute = remindMinute,
            )
        }
        // Last: this ends the intro, which removes this screen.
        App.store.completeOnboarding(p, program)
        Haptics.success()
        onDone()
    }

    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > WELCOME) Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back")) { if (step > first) step-- else onDone() }
            Spacer(Modifier.width(16.dp))
            Meter(step / PLAN.toFloat(), Modifier.weight(1f))
            Spacer(Modifier.width(14.dp))
            Text(L("$step von $PLAN", "$step of $PLAN"), style = MaterialTheme.typography.labelMedium, color = Ink.muted)
        }
        AnimatedContent(
            step, Modifier.weight(1f), label = "intro",
            // Forward slides in from the right, back from the left.
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { dir * it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -dir * it / 3 } + fadeOut(tween(200)))
            },
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (s) {
                    WELCOME -> Welcome()
                    GOAL -> Question(L("Was ist dein Ziel?", "What is your goal?"), L("Davon hängen Sätze, Wiederholungen und Pausen ab.", "It sets your sets, reps and rest times.")) {
                        Goal.entries.forEach { Option(it.label, it.blurb, goal == it, goalEffect(it)) { goal = it } }
                    }
                    LEVEL -> Question(L("Wie erfahren bist du?", "How experienced are you?"), L("Einsteiger bekommen weniger Sätze und einfachere Übungen.", "Beginners get fewer sets and simpler exercises.")) {
                        Level.entries.forEach { Option(it.label, it.blurb, level == it, levelEffect(it)) { level = it } }
                    }
                    DAYS -> DaysStep(profile(), days, minutes, { days = it }, { minutes = it })
                    PLACE -> Question(L("Wo trainierst du?", "Where do you train?"), L("Der Coach wählt nur Übungen, die du mit deiner Ausstattung machen kannst.", "The coach only picks exercises you can do with your equipment.")) {
                        Setup.entries.forEach { option ->
                            // The chosen place also counts the equipment switched off below.
                            val only = if (option == setup) kit else emptySet()
                            val count = remember(option, only) {
                                App.repo.all.count { option.allows(it.equipment) && (only.isEmpty() || Kit.of(it.equipment)?.key in only) }
                            }
                            Option(option.label, option.blurb, setup == option, L("$count Übungen stehen zur Wahl", "$count exercises to choose from")) {
                                // Kit chosen for another place may not exist here; an empty match would allow nothing.
                                if (option != setup) kit = emptySet()
                                setup = option
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        KitPicker(setup, kit) { kit = it }
                    }
                    FOCUS -> FocusStep(focus) { focus = it }
                    LIMITS -> Question(
                        L("Etwas schonen?", "Anything to spare?"),
                        L("Übungen, die diese Gelenke stark belasten, lässt der Coach weg.", "The coach leaves out exercises that load these joints heavily."),
                        optional = true,
                    ) {
                        Limit.entries.forEach { l ->
                            val count = remember(l) { App.repo.all.count(l::hits) }
                            Option(l.label, l.blurb, l.key in limits, L("$count Übungen fallen weg", "$count exercises left out"), multi = true, media = App.repo[l.sample]) {
                                limits = if (l.key in limits) limits - l.key else limits + l.key
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            L("Kein Ersatz für ärztlichen Rat: Bei Schmerzen lass es bitte abklären.", "No substitute for medical advice: if something hurts, get it checked."),
                            color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    BODY -> BodyStep(weight, goalWeight, height, born, { weight = it }, { goalWeight = it }, { height = it }, { born = it })
                    CONNECT -> ConnectStep(
                        remind, remindDays, remindHour, remindMinute,
                        onRemind = { on ->
                            remind = on
                            // A sensible start: the reminder days follow the chosen number of training days.
                            if (on && !start.settings.reminder) remindDays = suggestedDays(days)
                        },
                        onDays = { remindDays = it },
                        onTime = { h, m -> remindHour = h; remindMinute = m },
                    )
                    else -> ProgramPreview(profile(), seed) { seed++ }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            val optional = step in FOCUS..CONNECT
            val empty = when (step) {
                FOCUS -> focus.isEmpty()
                LIMITS -> limits.isEmpty()
                BODY -> listOf(weight, goalWeight, height, born).all { it.isBlank() }
                else -> false
            }
            PrimaryButton(
                when {
                    step == WELCOME -> L("Los geht's", "Let's go")
                    step == PLAN -> L("Plan übernehmen", "Use this plan")
                    optional && empty -> L("Überspringen", "Skip")
                    else -> L("Weiter", "Next")
                },
                Modifier.fillMaxWidth(),
                if (step == PLAN) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            ) { if (step < PLAN) step++ else finish() }
            if (step == WELCOME) RestoreLink(onDone)
        }
    }
}

// --- Welcome -------------------------------------------------------------------------------

@Composable
private fun Welcome() {
    // A grid of live animations as the hero; the tiles pop in one after the other.
    val ids = listOf("0025", "0043", "0652", "0662", "0334", "0313")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ids.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, id ->
                    val a by animateFloatAsState(if (shown) 1f else 0f, tween(520, delayMillis = (r * 3 + c) * 90), label = "tile")
                    ExerciseMedia(
                        App.repo[id],
                        Modifier.weight(1f).aspectRatio(1f).graphicsLayer { alpha = a; scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a },
                        corner = 22.dp,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Eyebrow("Mauve Fit", color = Ink.accent)
    Spacer(Modifier.height(4.dp))
    Text(L("Dein Training.\nDein Coach.", "Your training.\nYour coach."), style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        L("Ein paar Fragen, dann steht dein Plan. Er passt sich an, während du stärker wirst.", "A few questions and your plan is ready. It adapts as you get stronger."),
        color = Ink.muted, style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(22.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Promise(Icons.Rounded.Timer, L("Fertig in unter zwei Minuten", "Set up in under two minutes"), L("Ziel, Tage, Ort, fertig. Alles Weitere ist optional.", "Goal, days, place, done. Everything else is optional."))
        Promise(Icons.Rounded.FitnessCenter, L("Über 1.300 Übungen mit Animation", "Over 1,300 animated exercises"), L("Passend zu deiner Ausstattung und deinen Gelenken.", "Matched to your equipment and your joints."))
        Promise(Icons.AutoMirrored.Rounded.TrendingUp, L("Gewichte steigen mit dir", "Weights grow with you"), L("Der Coach schlägt jedes Mal die nächste Steigerung vor.", "The coach suggests the next step up every time."))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Promise(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconDisc(icon)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
    }
}

@Composable
private fun IconDisc(icon: ImageVector) {
    Box(Modifier.size(40.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(22.dp))
    }
}

/** "I have a backup": restores it and skips the intro; the activity restarts for its language and theme. */
@Composable
private fun RestoreLink(onDone: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text != null && App.store.import(text)) {
            // A backup from before the intro was finished still counts as set up.
            if (!App.store.data.value.profile.onboarded) {
                App.store.update { it.copy(profile = it.profile.copy(onboarded = true)) }
                App.store.flush()
            }
            I18n.apply(App.store.data.value.settings.language)
            Haptics.success()
            onDone()
            (context as? Activity)?.recreate()
        } else {
            failed = true
            Haptics.warn()
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().clip(Pill).clickable { Haptics.tick(); failed = false; picker.launch(arrayOf("application/json", "*/*")) }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Upload, null, tint = Ink.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(L("Ich habe ein Backup", "I have a backup"), style = MaterialTheme.typography.titleSmall, color = Ink.muted)
    }
    AnimatedVisibility(failed) {
        Text(
            L("Das ist kein Mauve-Fit-Backup.", "This is not a Mauve Fit backup."),
            Modifier.fillMaxWidth(), color = Ink.coral, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// --- Questions -----------------------------------------------------------------------------

@Composable
private fun Question(title: String, sub: String, optional: Boolean = false, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    if (optional) { Eyebrow(L("Optional", "Optional"), color = Ink.accent); Spacer(Modifier.height(4.dp)) }
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(sub, color = Ink.muted, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    Spacer(Modifier.height(8.dp))
}

/**
 * A big answer card: title, a line of context and, under it, what the answer changes in the plan.
 * [multi] draws a square check for answers that can be combined.
 */
@Composable
private fun Option(
    title: String, sub: String, selected: Boolean, effect: String? = null,
    multi: Boolean = false, media: Exercise? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().pressable { Haptics.click(); onClick() }.clip(RowShape)
            .background(if (selected) Ink.raised else Ink.surface)
            .border(2.dp, if (selected) Ink.accent else Ink.surface, RowShape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media != null) { Thumb(media, 52.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(sub, color = Ink.muted, style = MaterialTheme.typography.bodyMedium)
            if (effect != null) {
                Spacer(Modifier.height(6.dp))
                Text(effect, color = if (selected) Ink.accent else Ink.faint, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(26.dp).clip(if (multi) RoundedCornerShape(8.dp) else Pill).background(if (selected) Ink.accent else Ink.line),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(16.dp)) }
    }
}

private fun goalEffect(g: Goal) = when (g) {
    Goal.Strength -> L("Grundübungen 5 × 5, drei Minuten Pause", "Main lifts 5 × 5, three minutes of rest")
    Goal.Muscle -> L("4 × 8 zum Start, danach 3 × 12", "4 × 8 to open, then 3 × 12")
    Goal.Fitness -> L("3 × 12, eine Minute Pause", "3 × 12, one minute of rest")
    Goal.FatLoss -> L("3 × 15, nur 45 Sekunden Pause", "3 × 15, only 45 seconds of rest")
}

private fun levelEffect(l: Level) = when (l) {
    Level.Beginner -> L("Ein Satz weniger, Maschinen bevorzugt", "One set less, machines preferred")
    Level.Intermediate -> L("Volle Sätze, freie Gewichte im Mix", "Full sets, a mix of free weights")
    Level.Advanced -> L("Volle Sätze, die Langhantel im Mittelpunkt", "Full sets, built around the barbell")
}

/** Training days, then how long a session may take; the plan's real numbers show underneath. */
@Composable
private fun DaysStep(profile: Profile, days: Int, minutes: Int, onDays: (Int) -> Unit, onMinutes: (Int) -> Unit) {
    Question(L("Wie oft und wie lange?", "How often and how long?"), L("Daraus werden dein Wochenziel und die Aufteilung der Muskelgruppen.", "This sets your weekly goal and how muscle groups are split.")) {
        Eyebrow(L("Tage pro Woche", "Days per week"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (2..6).forEach { n ->
                Tile(Modifier.weight(1f).aspectRatio(1f), days == n, { onDays(n) }) {
                    Text("$n", style = MaterialTheme.typography.headlineMedium, color = if (days == n) Ink.bg else Ink.text)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(Coach.splitLabel(profile), style = MaterialTheme.typography.titleLarge, color = Ink.accent)
        Text(
            when {
                days <= 2 -> L("Zwei Ganzkörper-Einheiten decken alles ab.", "Two full-body sessions cover everything.")
                days == 3 && profile.level == Level.Beginner -> L("Drei Ganzkörper-Einheiten, ideal zum Lernen der Übungen.", "Three full-body sessions, ideal for learning the exercises.")
                days == 4 -> L("Oben und unten im Wechsel, jede Muskelgruppe zweimal pro Woche.", "Upper and lower body alternating, every muscle group twice a week.")
                else -> L("Drücken, Ziehen und Beine im Wechsel.", "Push, pull and legs in rotation.")
            },
            color = Ink.muted,
        )
        Spacer(Modifier.height(18.dp))
        Eyebrow(L("Zeit pro Einheit", "Time per session"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            lengths.forEach { m ->
                // Tapping the chosen length again hands the decision back to the goal.
                Tile(Modifier.weight(1f).height(76.dp), minutes == m, { onMinutes(if (minutes == m) 0 else m) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$m", style = MaterialTheme.typography.headlineSmall, color = if (minutes == m) Ink.bg else Ink.text)
                        Text("min", style = MaterialTheme.typography.labelSmall, color = if (minutes == m) Ink.bg else Ink.muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val plan = remember(profile) { Coach.buildProgram(App.repo, profile) }
        if (plan.isNotEmpty()) {
            val ex = plan.map { it.exercises.size }.average().roundToInt()
            val sets = plan.map { r -> r.exercises.sumOf { it.sets } }.average().roundToInt()
            val min = plan.map { estMinutes(it) }.average().roundToInt()
            Text(
                if (minutes == 0) L(
                    "Ohne Vorgabe richtet sich die Länge nach deinem Ziel: etwa $ex Übungen, $sets Sätze, $min Minuten.",
                    "Without a choice your goal sets the length: about $ex exercises, $sets sets, $min minutes.",
                ) else L("Pro Einheit etwa $ex Übungen und $sets Sätze, rund $min Minuten.", "About $ex exercises and $sets sets per session, around $min minutes."),
                color = Ink.muted,
            )
        }
    }
}

/** Square answer for numbers: accent fill when chosen. */
@Composable
private fun Tile(modifier: Modifier, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.pressable { Haptics.tick(); onClick() }.clip(RowShape).background(if (selected) Ink.accent else Ink.surface),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Body areas to emphasise, as picture tiles; at most three, or nothing would stand out. */
@Composable
private fun FocusStep(focus: Set<String>, onChange: (Set<String>) -> Unit) {
    Question(
        L("Worauf willst du achten?", "What do you want to emphasise?"),
        L("Bis zu drei Bereiche bekommen einen Satz und eine Übung mehr.", "Up to three areas get an extra set and an extra exercise."),
        optional = true,
    ) {
        Focus.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f ->
                    val on = f.key in focus
                    Column(
                        Modifier.weight(1f).pressable {
                            when {
                                on -> { Haptics.click(); onChange(focus - f.key) }
                                focus.size < 3 -> { Haptics.click(); onChange(focus + f.key) }
                                else -> Haptics.warn()
                            }
                        }.clip(RowShape).background(if (on) Ink.raised else Ink.surface)
                            .border(2.dp, if (on) Ink.accent else Ink.surface, RowShape).padding(10.dp),
                    ) {
                        ExerciseMedia(App.repo[f.sample], Modifier.fillMaxWidth().height(92.dp), animated = false, corner = 14.dp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) Ink.accent else Ink.line),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (focus.isEmpty()) L("Ohne Auswahl trainiert der Coach alles gleichmäßig.", "With nothing picked the coach trains everything evenly.")
            else L("Mehr für ${names(focus)} an den Tagen, die sie trainieren.", "More for ${names(focus)} on the days that train them."),
            color = if (focus.isEmpty()) Ink.muted else Ink.accent,
        )
    }
}

/** "Brust, Arme und Po" from focus keys. */
private fun names(focus: Set<String>): String {
    val labels = Focus.entries.filter { it.key in focus }.map { it.label }
    return if (labels.size < 2) labels.joinToString() else labels.dropLast(1).joinToString(", ") + L(" und ", " and ") + labels.last()
}

/** "72,5" or "72.5" in kg; null when empty or not a plausible body weight. */
private fun kilos(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }

@Composable
private fun BodyStep(
    weight: String, goalWeight: String, height: String, born: String,
    onWeight: (String) -> Unit, onGoal: (String) -> Unit, onHeight: (String) -> Unit, onBorn: (String) -> Unit,
) {
    Question(
        L("Ein paar Körperdaten", "A few body numbers"),
        L("Damit starten dein Gewichtsverlauf und dein Ziel. Alles bleibt auf deinem Gerät.", "They start your weight log and goal. Everything stays on your device."),
        optional = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(L("Gewicht", "Weight"), weight, "kg", Modifier.weight(1f), decimal = true, onWeight)
            NumberField(L("Zielgewicht", "Goal weight"), goalWeight, "kg", Modifier.weight(1f), decimal = true, onGoal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(L("Größe", "Height"), height, "cm", Modifier.weight(1f), decimal = false, onHeight)
            NumberField(L("Geburtsjahr", "Birth year"), born, "", Modifier.weight(1f), decimal = false, onBorn)
        }
        Spacer(Modifier.height(4.dp))
        val now = kilos(weight)
        val target = kilos(goalWeight)
        Text(
            when {
                now != null && target != null && abs(now - target) >= 0.1 ->
                    if (target < now) L("Noch ${Stats.fmtKg(now - target)} kg bis zum Ziel. Der Fortschritt zeigt dir den Weg.", "${Stats.fmtKg(now - target)} kg to go. Progress shows you the way.")
                    else L("${Stats.fmtKg(target - now)} kg aufbauen. Der Fortschritt zeigt dir den Weg.", "${Stats.fmtKg(target - now)} kg to gain. Progress shows you the way.")
                now != null -> L("Dein Gewichtsverlauf startet mit ${Stats.fmtKg(now)} kg.", "Your weight log starts at ${Stats.fmtKg(now)} kg.")
                else -> L("Du kannst das jederzeit später im Fortschritt eintragen.", "You can add this any time later under progress.")
            },
            color = if (now != null) Ink.accent else Ink.muted,
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, suffix: String, modifier: Modifier, decimal: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(if (decimal) 5 else 4)) }, modifier,
        label = { Text(label, maxLines = 1) }, singleLine = true, shape = RowShape,
        suffix = { Text(suffix, color = Ink.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

/** Reminder days that spread [days] sessions over the week (1 = Monday). */
private fun suggestedDays(days: Int): Set<Int> = when (days) {
    2 -> setOf(2, 5)
    3 -> setOf(1, 3, 5)
    4 -> setOf(1, 2, 4, 5)
    5 -> setOf(1, 2, 3, 5, 6)
    else -> setOf(1, 2, 3, 4, 5, 6)
}

/** Watch recovery, notifications and a reminder: one tap each, all optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectStep(
    remind: Boolean, remindDays: Set<Int>, hour: Int, minute: Int,
    onRemind: (Boolean) -> Unit, onDays: (Set<Int>) -> Unit, onTime: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { health = Recovery.granted(context) }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        health = got.containsAll(Recovery.permissions)
        if (health == true) { Haptics.success(); scope.launch { Recovery.refresh(context) } }
    }
    var share by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { share = App.store.data.value.settings.healthWrite && HealthSync.granted(context) }
    val askShare = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        share = got.containsAll(HealthSync.permissions)
        if (share) { Haptics.success(); App.store.updateSettings { it.copy(healthWrite = true) } }
    }
    var notify by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notify) Haptics.success()
    }
    fun requestNotify() {
        if (Build.VERSION.SDK_INT >= 33 && !notify) { introAskedNotifications = true; askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Question(L("Verbinden", "Connect"), L("Mach den Coach schlauer. Alles lässt sich später in den Einstellungen ändern.", "Make the coach smarter. All of it can be changed later in the settings."), optional = true) {
        if (Recovery.available(context)) ConnectCard(
            Icons.Rounded.Favorite, L("Erholung von der Uhr", "Recovery from your watch"),
            L("Schlaf, Ruhepuls und HRV aus Health Connect. An schlechten Tagen startest du etwas leichter.", "Sleep, resting pulse and HRV from Health Connect. On poor days you start a little lighter."),
            done = health == true, action = L("Verbinden", "Connect"), doneLabel = L("Verbunden", "Connected"),
        ) { askHealth.launch(Recovery.permissions) }
        if (Recovery.available(context)) ConnectCard(
            Icons.Rounded.Sync, L("Trainings teilen", "Share workouts"),
            L("Trainings und Gewicht landen in Health Connect, z. B. für Samsung Health.", "Workouts and weight go to Health Connect, e.g. for Samsung Health."),
            done = share, action = L("Teilen", "Share"), doneLabel = L("Geteilt", "Sharing"),
        ) { askShare.launch(HealthSync.permissions) }
        if (Build.VERSION.SDK_INT >= 33) ConnectCard(
            Icons.Rounded.Notifications, L("Benachrichtigungen", "Notifications"),
            L("Für den Pausentimer im Hintergrund und Erinnerungen.", "For the rest timer in the background and reminders."),
            done = notify, action = L("Erlauben", "Allow"), doneLabel = L("Erlaubt", "Allowed"),
        ) { requestNotify() }
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconDisc(Icons.Rounded.Timer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(L("Trainingserinnerung", "Workout reminder"), style = MaterialTheme.typography.titleMedium)
                    Text(L("Nur an Tagen ohne Training", "Only on days without a workout"), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                }
                Switch(
                    remind, { on -> Haptics.click(); onRemind(on); if (on) requestNotify() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.bg, checkedTrackColor = Ink.accent,
                        uncheckedThumbColor = Ink.muted, uncheckedTrackColor = Ink.raised, uncheckedBorderColor = Ink.line,
                    ),
                )
            }
            AnimatedVisibility(remind) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (if (I18n.german) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")).forEachIndexed { i, n ->
                            val day = i + 1
                            val on = day in remindDays
                            Box(
                                Modifier.size(36.dp).clip(Pill).background(if (on) Ink.accent else Ink.raised)
                                    .clickable { Haptics.tick(); onDays(if (on) remindDays - day else remindDays + day) },
                                contentAlignment = Alignment.Center,
                            ) { Text(n, color = if (on) Ink.bg else Ink.muted, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 12, 17, 19).forEach { h -> Chip("%02d:00".format(h), hour == h && minute == 0) { onTime(h, 0) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectCard(icon: ImageVector, title: String, sub: String, done: Boolean, action: String, doneLabel: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisc(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (done) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(doneLabel, color = Ink.accent, style = MaterialTheme.typography.titleSmall)
        } else GhostButton(action, Modifier.fillMaxWidth(), onClick = onClick)
    }
}

// --- Plan ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramPreview(profile: Profile, seed: Int, onShuffle: () -> Unit) {
    val program = remember(profile, seed) { Coach.buildProgram(App.repo, profile, seed) }
    Spacer(Modifier.height(16.dp))
    Text(L("Dein Plan", "Your plan"), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
        L(
            "${Coach.splitLabel(profile)} · ${profile.days}× pro Woche · ${profile.goal.label}",
            "${Coach.splitLabel(profile)} · ${profile.days}× per week · ${profile.goal.label}",
        ),
        color = Ink.muted, style = MaterialTheme.typography.bodyLarge,
    )
    // What the optional answers did, so the plan reads as made for this person.
    val tags = buildList {
        if (profile.sessionMinutes > 0) add(L("Etwa ${profile.sessionMinutes} min", "About ${profile.sessionMinutes} min"))
        Focus.entries.filter { it.key in profile.focus }.forEach { add(L("Fokus ${it.label}", "Focus on ${it.label.lowercase()}")) }
        Limit.entries.filter { it.key in profile.limits }.forEach { add(L("${it.label} geschont", "Easy on ${it.label.lowercase()}")) }
    }
    if (tags.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { Tag(it, Ink.accent) }
        }
    }
    Spacer(Modifier.height(20.dp))
    program.forEach { r ->
        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleLarge)
                    Text(r.note, style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 1)
                }
                Text("${estMinutes(r)} min", color = Ink.muted)
            }
            Spacer(Modifier.height(10.dp))
            r.exercises.forEach { p ->
                val e: Exercise? = App.repo[p.exerciseId]
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(e, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(e?.title ?: p.exerciseId, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    Text("${p.sets} × ${p.reps}", color = Ink.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    GhostButton(L("Neu würfeln", "Shuffle"), Modifier.fillMaxWidth(), Icons.Rounded.Refresh, onShuffle)
    Spacer(Modifier.height(8.dp))
    Text(
        L("Jede Übung lässt sich später tauschen, jeder Plan bearbeiten.", "Every exercise can be swapped later, every plan edited."),
        color = Ink.faint, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
}
