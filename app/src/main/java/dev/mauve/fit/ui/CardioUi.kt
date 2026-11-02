package dev.mauve.fit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Cardio
import dev.mauve.fit.data.CardioKind
import dev.mauve.fit.data.CardioSession
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.CardioSync
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.system.Recovery
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val cardioDayFmt get() = DateTimeFormatter.ofPattern(L("EEEE, d. MMMM", "EEEE, MMMM d"), I18n.locale)

val CardioKind.icon: ImageVector
    get() = when (this) {
        CardioKind.Run -> Icons.AutoMirrored.Rounded.DirectionsRun
        CardioKind.Bike -> Icons.AutoMirrored.Rounded.DirectionsBike
        CardioKind.Row -> Icons.Rounded.Rowing
        CardioKind.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
        CardioKind.Swim -> Icons.Rounded.Pool
        CardioKind.Other -> Icons.Rounded.MonitorHeart
    }

/**
 * Cardio on the Training tab: this week's minutes per day, logging by hand and, once Health
 * Connect is connected for readiness, taking sessions over from the watch.
 */
@Composable
fun CardioCard(data: AppData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf(false) }
    // Watch import is offered only to people who already connected Health Connect.
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    OnResume { connected = Recovery.granted(context) }

    fun import() = scope.launch {
        val found = CardioSync.recent(context)
        if (found == null) { status = L("Kein Zugriff auf Trainings in Health Connect.", "No access to workouts in Health Connect."); return@launch }
        val added = App.store.importCardio(found)
        if (added > 0) Haptics.success() else Haptics.tick()
        status = when (added) {
            0 -> L("Nichts Neues aus den letzten 14 Tagen.", "Nothing new from the last 14 days.")
            1 -> L("1 Einheit übernommen.", "1 session imported.")
            else -> L("$added Einheiten übernommen.", "$added sessions imported.")
        }
    }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch {
            if (CardioSync.granted(context)) import()
            else status = L("Ohne Zugriff auf Trainings geht die Übernahme nicht.", "Importing needs access to workouts.")
        }
    }

    Card(Modifier.fillMaxWidth()) {
        CardioWeek(data.cardio)
        Spacer(Modifier.height(10.dp))
        Text(
            L("Cardio zählt nicht zum Wochenziel, das bleibt beim Krafttraining. Der Coach berücksichtigt es aber bei der Erholung der Beine.",
                "Cardio does not count toward the weekly goal, which stays with strength training. The coach does factor it into leg recovery."),
            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(L("Cardio eintragen", "Log cardio"), Modifier.weight(1f), Icons.Rounded.Add) { logging = true }
            if (connected) GhostButton(L("Von der Uhr", "From watch"), Modifier.weight(1f), Icons.Rounded.Watch) {
                status = null
                scope.launch { if (CardioSync.granted(context)) import() else ask.launch(CardioSync.permissions) }
            }
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Ink.accent, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (logging) CardioDialog(onDismiss = { logging = false }) { s ->
        App.store.addCardio(s)
        Haptics.success()
        logging = false
    }
}

/** "Cardio this week: 95 min" with a bar per day, today in the accent. */
@Composable
fun CardioWeek(cardio: List<CardioSession>) {
    val today = LocalDate.now()
    val minutes = Cardio.weekMinutes(cardio, today)
    val total = minutes.sum()
    Eyebrow(L("Cardio diese Woche", "Cardio this week"))
    Text(Cardio.fmtMinutes(total), style = MaterialTheme.typography.headlineMedium, color = if (total > 0) Ink.mauve else Ink.text)
    Spacer(Modifier.height(12.dp))
    val max = minutes.maxOrNull()?.takeIf { it > 0 } ?: 1
    Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        minutes.forEachIndexed { i, m ->
            val isToday = today.dayOfWeek.value - 1 == i
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((m / max.toFloat()).coerceAtLeast(0.06f)).clip(RoundedCornerShape(6.dp))
                            .background(if (m == 0) Ink.raised else Ink.mauve)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, I18n.locale),
                    style = MaterialTheme.typography.labelSmall, color = if (isToday) Ink.accent else Ink.faint, maxLines = 1,
                )
            }
        }
    }
}

/** Log sheet: kind, minutes, and optionally distance and effort. The session ends now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioDialog(onDismiss: () -> Unit, onSave: (CardioSession) -> Unit) {
    var kind by remember { mutableStateOf(CardioKind.Run) }
    var minutes by remember { mutableIntStateOf(30) }
    var distance by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf<Cardio.Effort?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Cardio eintragen", "Log cardio")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardioKind.entries.forEach { k -> Chip(k.label, k == kind) { kind = k } }
                }
                Stepper(L("Dauer", "Duration"), Cardio.fmtMinutes(minutes), Modifier.fillMaxWidth(),
                    { minutes = (minutes - 5).coerceAtLeast(5) }) { minutes = (minutes + 5).coerceAtMost(600) }
                OutlinedTextField(
                    distance, { distance = it }, label = { Text(L("Strecke in km (optional)", "Distance in km (optional)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Eyebrow(L("Anstrengung (optional)", "Effort (optional)"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Tapping the chosen one again clears it.
                    Cardio.Effort.entries.forEach { e -> Chip(e.label, e == effort) { effort = if (effort == e) null else e } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val km = distance.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..500.0 }
                onSave(CardioSession(at = System.currentTimeMillis() - minutes * 60_000L, kind = kind, minutes = minutes, distanceKm = km, rpe = effort?.rpe))
            }) { Text(L("Speichern", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** A cardio session in the workout list: a mauve icon and minutes instead of volume in the accent. */
@Composable
fun CardioRow(s: CardioSession, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), color = Ink.surface, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(Pill).background(Ink.mauve.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(s.kind.icon, null, tint = Ink.mauve, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.note.ifBlank { s.kind.label },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val parts = listOfNotNull(
                    Stats.day(s.at).format(cardioDayFmt),
                    Cardio.Effort.of(s.rpe)?.label,
                    s.avgHr?.let { "$it bpm" },
                    if (s.source == "health") L("Uhr", "Watch") else null,
                )
                Text(parts.joinToString(" · "), color = Ink.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Cardio.fmtMinutes(s.minutes), style = MaterialTheme.typography.titleMedium, color = Ink.mauve)
                s.distanceKm?.let { Text(Cardio.fmtKm(it), color = Ink.faint, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(Icons.Rounded.DeleteOutline, L("Cardio löschen", "Delete cardio"), size = 36.dp, onClick = onDelete)
        }
    }
}
