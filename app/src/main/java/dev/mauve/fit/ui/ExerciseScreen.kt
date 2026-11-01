package dev.mauve.fit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import dev.mauve.fit.data.Stats

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseScreen(id: String, data: AppData, nav: NavController) {
    val e = App.repo[id] ?: return
    val sessions = remember(data.history, id) { Stats.sessions(data.history, id) }
    var choosePlan by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().background(Ink.bg).background(Grain).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back")) { nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                val fav = id in data.favorites
                Box(
                    Modifier.size(40.dp).clip(Pill).background(Ink.raised).clickable { Haptics.click(); App.store.toggleFavorite(id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, L("Favorit", "Favourite"), tint = if (fav) Ink.red else Ink.text, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
                RoundButton(Icons.Rounded.PlaylistAdd, L("Zu Plan hinzufügen", "Add to plan")) { choosePlan = true }
            }
        }
        item {
            ExerciseMedia(e, Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(mediaStageHeight()), corner = 28.dp)
            Text(
                Exercise.ATTRIBUTION, Modifier.padding(start = 24.dp, top = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = Ink.faint,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(e.title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(Labels.muscle(e.target), Ink.accent)
                    e.secondary.forEach { Tag(Labels.muscle(it)) }
                    Tag(Labels.equipment(e.equipment), Ink.mauve)
                }
                added?.let { Text(L("Zu „$it“ hinzugefügt", "Added to “$it”"), color = Ink.accent, modifier = Modifier.padding(top = 10.dp)) }
            }
        }

        if (sessions.isNotEmpty()) item {
            val best = sessions.maxBy { if (e.bodyweight) it.best.reps.toDouble() else it.e1rm }
            Card(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
                Eyebrow(L("Dein Bestwert", "Your best"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (!e.bodyweight) Column {
                        Text("${Stats.fmtKg(best.e1rm)} kg", style = MaterialTheme.typography.headlineMedium, color = Ink.accent)
                        Text(L("geschätztes 1RM", "estimated 1RM"), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                    }
                    Column {
                        Text(
                            if (e.bodyweight) L("${best.best.reps} Wdh.", "${best.best.reps} reps") else "${Stats.fmtKg(best.best.weight)} × ${best.best.reps}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(L("bester Satz", "best set"), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                    }
                    Column {
                        Text("${sessions.size}", style = MaterialTheme.typography.headlineMedium)
                        Text(L("Einheiten", "sessions"), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                    }
                }
                if (sessions.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Sparkline(
                        sessions.map { (if (e.bodyweight) it.best.reps.toDouble() else it.e1rm).toFloat() },
                        Modifier.fillMaxWidth().height(80.dp),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Eyebrow(L("Ausführung", "How to"))
                Spacer(Modifier.height(8.dp))
                e.steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Box(Modifier.size(28.dp).clip(Pill).background(Ink.raised), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Ink.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 3.dp))
                    }
                }
            }
        }
    }

    if (choosePlan) AlertDialog(
        onDismissRequest = { choosePlan = false },
        title = { Text(L("Zu welchem Plan?", "Add to which plan?")) },
        text = {
            Column {
                data.routines.forEach { r ->
                    Text(
                        r.name,
                        Modifier.fillMaxWidth().clip(Pill).clickable {
                            App.store.addToRoutine(r.id, id); added = r.name; choosePlan = false
                        }.padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { choosePlan = false }) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** Smooth progress line with a dot on the latest value. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 8.dp.toPx()
        val pts = values.mapIndexed { i, v ->
            Offset(
                pad + i * (size.width - 2 * pad) / (values.size - 1).coerceAtLeast(1),
                pad + (1 - (v - min) / range) * (size.height - 2 * pad),
            )
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        drawPath(path, Ink.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Ink.accent, 5.dp.toPx(), pts.last())
    }
}
