package dev.mauve.fit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.system.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Progress photos, picked with the system photo picker and copied into app storage: they never leave
 * the phone and need no storage permission. Named by the time they were added.
 */
@Composable
fun ProgressPhotos() {
    val context = LocalContext.current
    val dir = remember { File(context.filesDir, "photos").apply { mkdirs() } }
    var photos by remember { mutableStateOf(list(dir)) }
    var viewing by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "${System.currentTimeMillis()}.jpg").outputStream().use { input.copyTo(it) }
                }
            }
            Haptics.success()
            photos = list(dir)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(L("Fortschrittsfotos", "Progress photos"), style = MaterialTheme.typography.titleMedium)
                Text(L("Bleiben nur auf diesem Handy", "Stay on this phone only"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
            }
            RoundButton(Icons.Rounded.AddAPhoto, L("Foto hinzufügen", "Add photo")) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(photos, key = { it.name }) { f ->
                    Column(Modifier.width(96.dp)) {
                        AsyncImage(
                            f, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RowShape).pressable { Haptics.click(); viewing = f },
                        )
                        Text(dateOf(f), Modifier.padding(top = 4.dp), color = Ink.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    viewing?.let { f ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(dateOf(f)) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(f, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RowShape))
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text(L("Schließen", "Close")) } },
            dismissButton = {
                TextButton(onClick = { Haptics.warn(); f.delete(); photos = list(dir); viewing = null }) { Text(L("Löschen", "Delete"), color = Ink.red) }
            },
            containerColor = Ink.raised,
        )
    }
}

private fun list(dir: File) = dir.listFiles { f -> f.extension == "jpg" }.orEmpty().sortedByDescending { it.name }

private fun dateOf(f: File): String {
    val at = f.nameWithoutExtension.toLongOrNull() ?: f.lastModified()
    val fmt = DateTimeFormatter.ofPattern(if (I18n.german) "d. MMM yyyy" else "MMM d, yyyy")
    return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
}
