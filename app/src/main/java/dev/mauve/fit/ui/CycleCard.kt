package dev.mauve.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Cycle
import dev.mauve.fit.data.CycleSettings
import dev.mauve.fit.data.L
import dev.mauve.fit.system.CycleHealth
import java.time.LocalDate

/**
 * Opt-in cycle hint on Home: phase, day x of n and one practical line. Only shown while cycle-based
 * training is on; with a start more than two cycles old it asks for a fresh date instead of guessing.
 */
@Composable
fun CycleCard(c: CycleSettings, today: LocalDate = LocalDate.now(), onUpdate: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(c.on, c.source) { if (c.source == "health") CycleHealth.sync(context) }
    val s = Cycle.status(c, today)
    Card(Modifier.fillMaxWidth()) {
        if (s == null || s.stale) {
            Eyebrow(L("Zyklus", "Cycle"))
            Text(L("Bitte Periode aktualisieren", "Please update your period"), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (s == null) L("Es ist noch kein Startdatum eingetragen.", "No start date has been entered yet.")
                else L("Der letzte Start liegt mehr als zwei Zyklen zurück, die Phase wäre nur geraten.", "The last start is more than two cycles ago, so the phase would only be a guess."),
                color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(L("Datum eintragen", "Enter date"), Modifier.fillMaxWidth(), onClick = onUpdate)
            return@Card
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow(L("Zyklus", "Cycle"))
                Text(s.phase.label, style = MaterialTheme.typography.headlineSmall)
            }
            Text(L("Tag ${s.day} von ${s.length}", "Day ${s.day} of ${s.length}"), color = Ink.muted, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        // One segment per phase, as long as the phase; the current one in the accent.
        val spans = (1..s.length).groupBy { Cycle.phase(it, s.length) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Cycle.Phase.entries.forEach { ph ->
                val days = spans[ph]?.size ?: return@forEach
                Box(Modifier.weight(days.toFloat()).height(8.dp).clip(Pill).background(if (ph == s.phase) Ink.accent else Ink.raised))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(Coach.cycleTraining(s.phase), style = MaterialTheme.typography.bodyMedium)
        if (s.projected) {
            Spacer(Modifier.height(4.dp))
            Text(L("Vom letzten eingetragenen Start weitergezählt.", "Counted on from the last entered start."), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(Coach.cycleDisclaimer, color = Ink.faint, style = MaterialTheme.typography.bodySmall)
    }
}
