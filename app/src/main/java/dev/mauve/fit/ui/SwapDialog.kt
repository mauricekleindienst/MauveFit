package dev.mauve.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Kit
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import dev.mauve.fit.system.Haptics

/**
 * Pick a replacement for an exercise: first what fits the user's equipment preference, then the
 * same movement per equipment group, e.g. the machine version of a dumbbell press.
 */
@Composable
fun SwapDialog(exerciseId: String, data: AppData, title: String = L("Übung tauschen", "Swap exercise"), onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val fitting = remember(exerciseId, data.profile) { Coach.alternatives(App.repo, exerciseId, data.profile::allows) }
    val byKit = remember(exerciseId) { Coach.alternativesByKit(App.repo, exerciseId) }
    var group by remember { mutableStateOf<Kit?>(null) }
    val options = group?.let { byKit[it] } ?: fitting
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(L("Passend", "For you"), group == null) { group = null }
                    byKit.keys.forEach { k -> Chip(k.label, group == k) { group = k } }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    if (options.isEmpty()) item {
                        Text(L("Nichts Passendes mit deiner Ausstattung. Wähle oben eine Gerätegruppe.", "Nothing fits your equipment. Pick an equipment group above."), color = Ink.muted)
                    }
                    items(options, key = { it.id }) { o ->
                        Row(
                            Modifier.fillMaxWidth().clip(RowShape).clickable { Haptics.click(); onPick(o.id) }.padding(vertical = 8.dp),
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
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}
