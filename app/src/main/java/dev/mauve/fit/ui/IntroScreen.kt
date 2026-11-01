package dev.mauve.fit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.Goal
import dev.mauve.fit.data.Level
import dev.mauve.fit.data.Profile
import dev.mauve.fit.data.Setup

/** First-run intro: a welcome page, four questions, then the generated program to confirm. */
@Composable
fun IntroScreen(initial: Profile, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }
    var level by rememberSaveable { mutableStateOf(initial.level) }
    var days by rememberSaveable { mutableIntStateOf(initial.days) }
    var setup by rememberSaveable { mutableStateOf(initial.setup) }
    var seed by rememberSaveable { mutableIntStateOf(0) }
    val last = 5
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding().navigationBarsPadding()) {
        if (step > 0) Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back")) { step-- }
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..last).forEach { i -> Meter(if (i <= step) 1f else 0f, Modifier.weight(1f)) }
            }
        }
        AnimatedContent(
            step, Modifier.weight(1f), label = "intro",
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (s) {
                    0 -> Welcome()
                    1 -> Question(L("Was ist dein Ziel?", "What is your goal?"), L("Davon hängen Sätze, Wiederholungen und Pausen ab.", "It sets your sets, reps and rest times.")) {
                        Goal.entries.forEach { Option(it.label, it.blurb, goal == it) { goal = it } }
                    }
                    2 -> Question(L("Wie erfahren bist du?", "How experienced are you?"), L("Einsteiger bekommen weniger Sätze und einfachere Übungen.", "Beginners get fewer sets and simpler exercises.")) {
                        Level.entries.forEach { Option(it.label, it.blurb, level == it) { level = it } }
                    }
                    3 -> Question(L("Wie oft pro Woche?", "How often per week?"), L("Daraus wird dein Wochenziel und die Aufteilung der Muskelgruppen.", "This sets your weekly goal and how muscle groups are split.")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            (2..6).forEach { n ->
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (days == n) Ink.accent else Ink.surface).clickable { Haptics.tick(); days = n },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$n", style = MaterialTheme.typography.headlineMedium, color = if (days == n) Ink.bg else Ink.text) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(Coach.splitLabel(Profile(goal, level, days, setup)), style = MaterialTheme.typography.titleLarge, color = Ink.accent)
                        Text(
                            when {
                                days <= 2 -> L("Zwei Ganzkörper-Einheiten decken alles ab.", "Two full-body sessions cover everything.")
                                days == 3 && level == Level.Beginner -> L("Drei Ganzkörper-Einheiten, ideal zum Lernen der Übungen.", "Three full-body sessions, ideal for learning the exercises.")
                                days == 4 -> L("Oben und unten im Wechsel, jede Muskelgruppe zweimal pro Woche.", "Upper and lower body alternating, every muscle group twice a week.")
                                else -> L("Drücken, Ziehen und Beine im Wechsel.", "Push, pull and legs in rotation.")
                            },
                            color = Ink.muted,
                        )
                    }
                    4 -> Question(L("Wo trainierst du?", "Where do you train?"), L("Der Coach wählt nur Übungen, die du mit deiner Ausstattung machen kannst.", "The coach only picks exercises you can do with your equipment.")) {
                        Setup.entries.forEach { Option(it.label, it.blurb, setup == it) { setup = it } }
                    }
                    else -> ProgramPreview(Profile(goal, level, days, setup), seed) { seed++ }
                }
            }
        }
        Box(Modifier.padding(24.dp)) {
            PrimaryButton(
                when (step) { 0 -> L("Los geht's", "Let's go"); last -> L("Plan übernehmen", "Use this plan"); else -> L("Weiter", "Next") },
                Modifier.fillMaxWidth(),
                if (step == last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            ) {
                if (step < last) step++
                else {
                    val profile = Profile(goal, level, days, setup)
                    App.store.completeOnboarding(profile, Coach.buildProgram(App.repo, profile, seed))
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    // A grid of live animations as the hero.
    val ids = listOf("0025", "0043", "0652", "0662", "0334", "0313")
    Spacer(Modifier.height(32.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ids.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { ExerciseMedia(App.repo[it], Modifier.weight(1f).aspectRatio(1f), corner = 22.dp) }
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Eyebrow("Mauve Fit", color = Ink.accent)
    Text(L("Dein Training.\nDein Coach.", "Your training.\nYour coach."), style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        L(
            "Vier kurze Fragen, dann stellt der Coach deinen Plan zusammen: über 1.300 Übungen mit Animation, " +
                "automatische Steigerung, Pausentimer und Unterstützung für die Galaxy Watch.",
            "Four quick questions, then the coach builds your plan: over 1,300 animated exercises, " +
                "automatic progression, a rest timer and Galaxy Watch support.",
        ),
        color = Ink.muted, style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun Question(title: String, sub: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(sub, color = Ink.muted, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(28.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun Option(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Ink.raised else Ink.surface)
            .border(2.dp, if (selected) Ink.accent else Ink.surface, RoundedCornerShape(10.dp))
            .clickable { Haptics.click(); onClick() }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(sub, color = Ink.muted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.size(26.dp).clip(Pill).background(if (selected) Ink.accent else Ink.raised),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(16.dp)) }
    }
}

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
    Spacer(Modifier.height(20.dp))
    program.forEach { r ->
        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${estMinutes(r)} min", color = Ink.muted)
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
    GhostButton(L("Andere Übungen", "Other exercises"), Modifier.fillMaxWidth(), Icons.Rounded.Refresh, onShuffle)
    Spacer(Modifier.height(8.dp))
}
