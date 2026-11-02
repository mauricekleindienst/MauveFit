package dev.mauve.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mauve.fit.data.L
import dev.mauve.fit.system.Updater
import kotlinx.coroutines.launch

/**
 * Update from GitHub: in the settings always (check, download, install), on Home only once a newer
 * release is known.
 */
@Composable
fun UpdateSection(home: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    if (home && state !is Updater.State.Available && state !is Updater.State.Downloading && state !is Updater.State.Ready) return

    Column(Modifier.fillMaxWidth()) {
        if (!home) Text(L("Installiert: Version ${Updater.current(context)}", "Installed: version ${Updater.current(context)}"),
            color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        when (val s = state) {
            is Updater.State.Available -> {
                if (home) Eyebrow(L("Update verfügbar", "Update available"), color = Ink.accent)
                Text(L("Version ${s.release.version} ist da.", "Version ${s.release.version} is out."), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(L("Laden und installieren", "Download and install"), Modifier.fillMaxWidth(), Icons.Rounded.SystemUpdate) {
                    scope.launch { Updater.download(context, s.release) }
                }
            }
            is Updater.State.Downloading -> {
                Text(L("Lädt Version ${s.release.version} …", "Downloading version ${s.release.version} …"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Meter(s.progress, Modifier.fillMaxWidth())
            }
            is Updater.State.Ready -> {
                Text(L("Version ${s.release.version} ist geladen.", "Version ${s.release.version} is downloaded."), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(L("Installieren", "Install"), Modifier.fillMaxWidth(), Icons.Rounded.SystemUpdate) { Updater.install(context, s.file) }
                Spacer(Modifier.height(6.dp))
                Text(L("Beim ersten Mal fragt Android, ob Mauve Fit Apps installieren darf. Danach hier noch einmal tippen.",
                    "The first time, Android asks whether Mauve Fit may install apps. Then tap here once more."),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                if (s == Updater.State.UpToDate) Text(L("Du hast die neueste Version.", "You have the latest version."), style = MaterialTheme.typography.bodyMedium)
                if (s is Updater.State.Failed) Text(s.message, color = Ink.red, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    if (s == Updater.State.Checking) L("Suche …", "Checking …") else L("Nach Updates suchen", "Check for updates"),
                    Modifier.fillMaxWidth(), Icons.Rounded.SystemUpdate,
                ) { if (s != Updater.State.Checking) scope.launch { Updater.check(context) } }
            }
        }
    }
}

