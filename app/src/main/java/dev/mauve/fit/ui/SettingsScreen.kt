package dev.mauve.fit.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.foundation.ExperimentalFoundationApi
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.wearable.Wearable
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.system.Media
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Coach
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(data: AppData, nav: NavController) {
    val s = data.settings
    val p = data.profile
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var watch by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        watch = runCatching { Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.displayName }.getOrNull()
    }
    val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        message = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(App.store.export().toByteArray()) }
        }.fold({ L("Backup gespeichert", "Backup saved") }, { L("Backup fehlgeschlagen", "Backup failed") })
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
        message = if (text != null && App.store.import(text)) L("Backup wiederhergestellt", "Backup restored") else L("Das ist kein Mauve-Fit-Backup", "This is not a Mauve Fit backup")
    }
    fun set(block: (dev.mauve.fit.data.Settings) -> dev.mauve.fit.data.Settings) = App.store.updateSettings(block)

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
            }
        }
        item {
            ScreenTitle("Mauve Fit", L("Einstellungen", "Settings"))
            message?.let { Text(it, color = Ink.accent, modifier = Modifier.padding(top = 8.dp)) }
        }

        item {
            Section(L("Profil und Plan", "Profile and plan"), Icons.Rounded.AutoAwesome) {
                Text("${p.goal.label} · ${p.level.label}", style = MaterialTheme.typography.titleLarge)
                Text(
                    L(
                        "${p.days}× pro Woche · ${p.setup.label} · ${Coach.splitLabel(p)}",
                        "${p.days}× per week · ${p.setup.label} · ${Coach.splitLabel(p)}",
                    ),
                    color = Ink.muted,
                )
                Spacer(Modifier.height(12.dp))
                GhostButton(L("Plan neu erstellen", "Rebuild plan"), Modifier.fillMaxWidth()) { nav.navigate("intro") }
            }
        }

        item {
            // Named in both languages so it can be found whichever one is active.
            Section("Sprache / Language", Icons.Rounded.Language) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "de" to "Deutsch", "en" to "English").forEach { (value, label) ->
                        val on = s.language == value
                        Box(
                            Modifier.weight(1f).clip(Pill).background(if (on) Ink.accent else Ink.raised)
                                .clickable {
                                    Haptics.click()
                                    if (!on) {
                                        App.store.updateSettings { it.copy(language = value) }
                                        I18n.apply(value)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (on) Ink.bg else Ink.text, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }

        item {
            Section(L("Darstellung", "Appearance"), Icons.Rounded.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to L("System", "System"), "dark" to L("Dunkel", "Dark"), "light" to L("Hell", "Light")).forEach { (key, label) ->
                        val on = s.theme == key
                        Box(
                            Modifier.weight(1f).clip(Pill).background(if (on) Ink.accent else Ink.raised)
                                .clickable {
                                    Haptics.click()
                                    if (!on) {
                                        App.store.updateSettings { it.copy(theme = key) }
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (on) Ink.bg else Ink.text, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Eyebrow(L("Akzentfarbe", "Accent colour"))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accent.entries.forEach { a ->
                        val on = Ink.accentChoice == a
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(42.dp).clip(Pill).background(if (Ink.dark) a.onInk else a.onPaper)
                                    .border(2.dp, if (on) Ink.text else Ink.bg, Pill)
                                    .clickable {
                                        Haptics.click()
                                        if (!on) {
                                            App.store.updateSettings { it.copy(accent = a.key) }
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.height(4.dp))
                            Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (on) Ink.text else Ink.muted)
                        }
                    }
                }
            }
        }

        item {
            Section(L("Training", "Training"), Icons.Rounded.AutoAwesome) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper(L("Wochenziel", "Weekly goal"), "${s.weeklyGoal}", Modifier.weight(1f),
                        { set { it.copy(weeklyGoal = (it.weeklyGoal - 1).coerceAtLeast(1)) } },
                        { set { it.copy(weeklyGoal = (it.weeklyGoal + 1).coerceAtMost(7)) } })
                    Stepper(L("Pause", "Rest"), restLabel(s.defaultRest), Modifier.weight(1f),
                        { set { it.copy(defaultRest = (it.defaultRest - 15).coerceAtLeast(15)) } },
                        { set { it.copy(defaultRest = (it.defaultRest + 15).coerceAtMost(600)) } })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val steps = listOf(0.5, 1.0, 1.25, 2.5, 5.0)
                    Stepper(L("Schritt kg", "Step kg"), Stats.fmtKg(s.weightStep), Modifier.weight(1f),
                        { set { it.copy(weightStep = steps.lastOrNull { x -> x < it.weightStep } ?: steps.first()) } },
                        { set { it.copy(weightStep = steps.firstOrNull { x -> x > it.weightStep } ?: steps.last()) } })
                    Stepper(L("Stange kg", "Bar kg"), Stats.fmtKg(s.barWeight), Modifier.weight(1f),
                        { set { it.copy(barWeight = (it.barWeight - 2.5).coerceAtLeast(5.0)) } },
                        { set { it.copy(barWeight = (it.barWeight + 2.5).coerceAtMost(25.0)) } })
                }
                Spacer(Modifier.height(4.dp))
                Toggle(L("Bildschirm anlassen", "Keep screen on"), L("Während eines Trainings", "During a workout"), s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
                Toggle(L("Vibration", "Vibration"), L("Bei Satz und Pausenende", "On set and rest end"), s.vibrate) { v -> set { it.copy(vibrate = v) } }
                Toggle(L("Ton", "Sound"), L("Benachrichtigung, wenn die Pause im Hintergrund endet", "Notification when rest ends in the background"), s.sound) { v -> set { it.copy(sound = v) } }
            }
        }

        item {
            Section(L("Erinnerung", "Reminder"), Icons.Rounded.Notifications) {
                if (!notificationsAllowed) {
                    Text(
                        L(
                            "Benachrichtigungen sind aus. Ohne sie gibt es keinen Pausentimer im Hintergrund und keine Erinnerungen.",
                            "Notifications are off. Without them there is no background rest timer and no reminders.",
                        ),
                        color = Ink.coral,
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton(L("Erlauben", "Allow"), Modifier.fillMaxWidth()) {
                        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Toggle(L("Trainingserinnerung", "Workout reminder"), L("Nur an Tagen ohne Training", "Only on days without a workout"), s.reminder) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    set { it.copy(reminder = v) }
                }
                if (s.reminder) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (if (I18n.german) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")).forEachIndexed { i, n ->
                            val day = i + 1
                            val on = day in s.reminderDays
                            Box(
                                Modifier.size(38.dp).clip(Pill).background(if (on) Ink.accent else Ink.raised)
                                    .clickable { Haptics.tick(); set { it.copy(reminderDays = if (on) it.reminderDays - day else it.reminderDays + day) } },
                                contentAlignment = Alignment.Center,
                            ) { Text(n, color = if (on) Ink.bg else Ink.muted, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stepper(L("Stunde", "Hour"), "%02d".format(s.reminderHour), Modifier.weight(1f),
                            { set { it.copy(reminderHour = (it.reminderHour + 23) % 24) } },
                            { set { it.copy(reminderHour = (it.reminderHour + 1) % 24) } })
                        Stepper(L("Minute", "Minute"), "%02d".format(s.reminderMinute), Modifier.weight(1f),
                            { set { it.copy(reminderMinute = (it.reminderMinute + 45) % 60) } },
                            { set { it.copy(reminderMinute = (it.reminderMinute + 15) % 60) } })
                    }
                }
            }
        }

        item {
            Section(L("Animationen", "Animations"), Icons.Rounded.Download) {
                val progress by Media.progress.collectAsState()
                Text(
                    L(
                        "Die Animationen kommen direkt aus dem Datensatz auf GitHub und bleiben danach in der App gespeichert. " +
                            "Übungen aus deinen Plänen lädt die App von selbst vor, damit das Training auch ohne Netz läuft.",
                        "The animations come straight from the dataset on GitHub and are then stored in the app. " +
                            "Exercises from your plans are preloaded, so workouts run without a connection too.",
                    ),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                val p = progress
                if (p == null) GhostButton(L("Alle offline speichern (ca. 130 MB)", "Save all offline (about 130 MB)"), Modifier.fillMaxWidth(), Icons.Rounded.Download) {
                    Media.downloadAll(context)
                } else {
                    Text(L("${p.first} von ${p.second} Übungen", "${p.first} of ${p.second} exercises"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Meter(p.first / p.second.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(L("Abbrechen", "Cancel"), color = Ink.red, modifier = Modifier.clickable { Haptics.click(); Media.cancel() })
                }
                // Recomputed whenever a download finishes or the cache is cleared.
                var cleared by remember { mutableStateOf(0) }
                val size = remember(progress == null, cleared) { Media.cacheSize(context) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Gespeichert: ${"%.1f".format(size / 1_048_576.0).replace('.', ',')} MB", "Stored: ${"%.1f".format(size / 1_048_576.0)} MB"), Modifier.weight(1f), color = Ink.muted)
                    Text(
                        L("Speicher leeren", "Clear storage"), color = Ink.red,
                        modifier = Modifier.clip(Pill).clickable { confirmClear = true }.padding(8.dp),
                    )
                }
                if (confirmClear) AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text(L("Animationen löschen?", "Delete animations?")) },
                    text = { Text(L("Alle gespeicherten Animationen werden entfernt und bei Bedarf neu aus GitHub geladen. Deine Trainingsdaten bleiben.", "All stored animations are removed and reloaded from GitHub when needed. Your training data stays.")) },
                    confirmButton = {
                        TextButton(onClick = { Haptics.warn(); Media.clear(context); cleared++; confirmClear = false }) { Text(L("Löschen", "Delete"), color = Ink.red) }
                    },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(L("Abbrechen", "Cancel")) } },
                    containerColor = Ink.raised,
                )
            }
        }

        item {
            Section("Galaxy Watch", Icons.Rounded.Watch) {
                Text(watch?.let { L("Verbunden: $it", "Connected: $it") } ?: L("Keine Uhr verbunden", "No watch connected"), style = MaterialTheme.typography.titleMedium,
                    color = if (watch != null) Ink.accent else Ink.text)
                Text(
                    L(
                        "Installiere Mauve Fit auf der Uhr. Laufende Trainings erscheinen dort automatisch, Sätze lassen sich am Handgelenk abhaken.",
                        "Install Mauve Fit on the watch. Running workouts appear there automatically and you can tick off sets on your wrist.",
                    ),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Section(L("Daten", "Data"), Icons.Rounded.Download) {
                Text(
                    L(
                        "${data.history.size} Trainings, ${data.routines.size} Pläne, ${data.bodyWeights.size} Gewichtseinträge. Alles bleibt auf diesem Gerät.",
                        "${data.history.size} workouts, ${data.routines.size} plans, ${data.bodyWeights.size} weight entries. Everything stays on this device.",
                    ),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(L("Sichern", "Back up"), Modifier.weight(1f), Icons.Rounded.Download) {
                        exporter.launch("mauvefit-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json")
                    }
                    GhostButton(L("Laden", "Restore"), Modifier.weight(1f), Icons.Rounded.Upload) { importer.launch(arrayOf("application/json", "*/*")) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(Pill).clickable { confirmReset = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = Ink.red)
                    Spacer(Modifier.width(8.dp))
                    Text(L("Alle Daten löschen", "Delete all data"), color = Ink.red)
                }
            }
        }

        item {
            Section(L("Über", "About"), Icons.Rounded.Info) {
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                Text("Mauve Fit ${version.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    L(
                        "Übungsdaten: exercises-dataset von hasaneyldrm (MIT-Lizenz). ${Exercise.ATTRIBUTION}. " +
                            "Schrift: Space Grotesk (SIL Open Font License). Keine Konten, kein Tracking, keine Werbung.",
                        "Exercise data: exercises-dataset by hasaneyldrm (MIT licence). ${Exercise.ATTRIBUTION}. " +
                            "Font: Space Grotesk (SIL Open Font License). No accounts, no tracking, no ads.",
                    ),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L("Datensatz auf GitHub", "Dataset on GitHub"), color = Ink.accent,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hasaneyldrm/exercises-dataset")))
                    },
                )
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(L("Alle Daten löschen?", "Delete all data?")) },
        text = { Text(L("Trainings, Pläne, Rekorde und Einstellungen werden entfernt. Das lässt sich nur mit einem Backup rückgängig machen.", "Workouts, plans, records and settings are removed. Only a backup can undo this.")) },
        confirmButton = {
            TextButton(onClick = { Haptics.warn(); confirmReset = false; App.store.reset(); nav.navigate("home") { popUpTo(0) } }) { Text(L("Löschen", "Delete"), color = Ink.red) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Eyebrow(title)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { Haptics.click(); onChange(!value) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            value, { Haptics.click(); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.bg, checkedTrackColor = Ink.accent,
                uncheckedThumbColor = Ink.muted, uncheckedTrackColor = Ink.raised, uncheckedBorderColor = Ink.line,
            ),
        )
    }
}
