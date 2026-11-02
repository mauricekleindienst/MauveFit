package dev.mauve.fit.ui

import dev.mauve.fit.system.CrashLog
import dev.mauve.fit.data.CsvExport
import dev.mauve.fit.data.Cycle
import dev.mauve.fit.system.CycleHealth
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneOffset
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Favorite
import dev.mauve.fit.system.Recovery
import dev.mauve.fit.system.HealthSync
import androidx.compose.material.icons.rounded.Sync
import androidx.health.connect.client.PermissionController
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
    // Re-checked on every return to the screen, e.g. after allowing them in the system settings.
    // areNotificationsEnabled also covers notifications switched off for the app as a whole.
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Files go through the IO thread: a backup on Google Drive may first have to download, and on
    // the main thread that freezes the app.
    val io = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        io.launch {
            message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(App.store.export().toByteArray()) } }
            }.fold({ L("Backup gespeichert", "Backup saved") }, { L("Backup fehlgeschlagen", "Backup failed") })
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        io.launch {
            message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(CsvExport.sets(App.store.data.value, App.repo::get).toByteArray()) } }
            }.fold({ L("CSV gespeichert", "CSV saved") }, { L("CSV-Export fehlgeschlagen", "CSV export failed") })
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        io.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readBackup(context, uri)?.let(App.store::import) == true }
            message = if (ok) L("Backup wiederhergestellt", "Backup restored") else L("Das ist kein Mauve-Fit-Backup", "This is not a Mauve Fit backup")
            // The backup's language, theme and accent apply now, as after a restore in the intro.
            if (ok) {
                I18n.apply(App.store.data.value.settings.language)
                App.store.relocalizePlans()
                dev.mauve.fit.system.createChannels(context)
                (context as? android.app.Activity)?.recreate()
            }
        }
    }
    fun set(block: (dev.mauve.fit.data.Settings) -> dev.mauve.fit.data.Settings) = App.store.updateSettings(block)

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 16.dp)) {
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
                Spacer(Modifier.height(16.dp))
                KitPicker(p.setup, p.kit) { App.store.setKit(it) }
                Spacer(Modifier.height(6.dp))
                Text(L("Gilt für Schnelltraining und Tauschen sofort, für deine Pläne nach „Plan neu erstellen“.",
                    "Applies to quick workouts and swaps right away, to your plans after \"Rebuild plan\"."),
                    color = Ink.faint, style = MaterialTheme.typography.bodySmall)
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
                                        App.store.relocalizePlans()
                                        // Channel names show in the system settings; they follow the language too.
                                        dev.mauve.fit.system.createChannels(context)
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
                    Accent.entries.filter { it != Accent.Wallpaper || android.os.Build.VERSION.SDK_INT >= 31 }.forEach { a ->
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

        item { RecoverySection() }
        item { HealthWriteSection() }
        item { CycleSection() }
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
                    val steps = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0)
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
                    GhostButton(L("In den Einstellungen erlauben", "Allow in settings"), Modifier.fillMaxWidth()) {
                        // After two refusals Android no longer shows the prompt; its settings page always works.
                        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Toggle(L("Wochenbericht", "Weekly report"), L("Montags um 9 Uhr: deine Zahlen der letzten Woche", "Mondays at 9: your numbers from last week"), s.weeklyReport) { v ->
                    set { it.copy(weeklyReport = v) }
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
                val size by androidx.compose.runtime.produceState(0L, progress == null, cleared) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Media.cacheSize(context) }
                }
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
                        TextButton(onClick = {
                            Haptics.warn(); confirmClear = false
                            // Deleting a few hundred MB is disk work, not something for the UI thread.
                            io.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Media.clear(context) }; cleared++ }
                        }) { Text(L("Löschen", "Delete"), color = Ink.red) }
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
                        "${data.history.size} ${if (data.history.size == 1) "Training" else "Trainings"}, ${data.routines.size} ${if (data.routines.size == 1) "Plan" else "Pläne"}, ${data.bodyWeights.size} ${if (data.bodyWeights.size == 1) "Gewichtseintrag" else "Gewichtseinträge"}. Alles bleibt auf diesem Gerät.",
                        "${data.history.size} ${if (data.history.size == 1) "workout" else "workouts"}, ${data.routines.size} ${if (data.routines.size == 1) "plan" else "plans"}, ${data.bodyWeights.size} ${if (data.bodyWeights.size == 1) "weight entry" else "weight entries"}. Everything stays on this device.",
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
                if (data.history.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    // One row per set, for Excel, Sheets or another app; the backup above stays the way to restore.
                    GhostButton(L("Verlauf als CSV exportieren", "Export history as CSV"), Modifier.fillMaxWidth(), Icons.Rounded.Download) {
                        csv.launch("mauvefit-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.csv")
                    }
                }
                // Crashes stay on the phone until the user decides to share them.
                var crashes by remember { mutableStateOf(CrashLog.list(context).size) }
                if (crashes > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(L("$crashes ${if (crashes == 1) "Absturzbericht" else "Absturzberichte"} gespeichert", "$crashes crash ${if (crashes == 1) "report" else "reports"} saved"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(L("Teilen", "Share"), Modifier.weight(1f)) {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, CrashLog.text(context))
                            context.startActivity(Intent.createChooser(send, L("Absturzbericht teilen", "Share crash report")))
                        }
                        GhostButton(L("Löschen", "Delete"), Modifier.weight(1f)) { Haptics.tick(); CrashLog.clear(context); crashes = 0 }
                    }
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
                        "Übungsdaten: exercises-dataset, © 2026 Hasan Emir Yıldırım (MIT-Lizenz). Animationen ${Exercise.ATTRIBUTION}. " +
                            "Schrift: Archivo (SIL Open Font License). Keine Konten, kein Tracking, keine Werbung.",
                        "Exercise data: exercises-dataset, © 2026 Hasan Emir Yıldırım (MIT licence). Animations ${Exercise.ATTRIBUTION}. " +
                            "Font: Archivo (SIL Open Font License). No accounts, no tracking, no ads.",
                    ),
                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L("Datensatz auf GitHub", "Dataset on GitHub"), color = Ink.accent,
                    modifier = Modifier.clickable {
                        // No browser installed or enabled must not crash the settings.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hasaneyldrm/exercises-dataset"))) }
                    },
                )
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(L("Alle Daten löschen?", "Delete all data?")) },
        text = { Text(L("Trainings, Pläne, Rekorde, Fortschrittsfotos und Einstellungen werden entfernt. Das lässt sich nur mit einem Backup rückgängig machen (Fotos sind nicht darin).", "Workouts, plans, records, progress photos and settings are removed. Only a backup can undo this (photos are not in it).")) },
        confirmButton = {
            TextButton(onClick = { Haptics.warn(); confirmReset = false; App.store.reset(); App.store.clearCycle()
                // Language, theme and accent are back to "system": apply them now, not after a restart.
                I18n.apply("system"); dev.mauve.fit.system.createChannels(context)
                nav.navigate("home") { popUpTo(0) }
                (context as? android.app.Activity)?.recreate()
            }) { Text(L("Löschen", "Delete"), color = Ink.red) }
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

/** Connects Health Connect so sleep, resting pulse and HRV from the watch feed the readiness score. */
@Composable
private fun RecoverySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    val today by Recovery.today.collectAsState()
    OnResume { granted = Recovery.granted(context) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        granted = got.containsAll(Recovery.permissions)
        if (granted == true) { Haptics.success(); scope.launch { Recovery.refresh(context) } }
    }
    Section(L("Erholung von der Uhr", "Recovery from your watch"), Icons.Rounded.Favorite) {
        Text(
            L("Schlaf, Ruhepuls und HRV aus Health Connect ergeben jeden Morgen einen Erholungswert. Bei Samsung Health: Einstellungen, Health Connect, Daten teilen einschalten.",
                "Sleep, resting heart rate and HRV from Health Connect make a readiness score every morning. In Samsung Health: Settings, Health Connect, turn on data sharing."),
            color = Ink.muted, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        when {
            !Recovery.available(context) -> Text(L("Health Connect ist auf diesem Gerät nicht verfügbar.", "Health Connect is not available on this device."), color = Ink.faint)
            granted == true -> {
                Text(
                    today?.let { L("Verbunden · heute ${it.score} von 100", "Connected · today ${it.score} of 100") }
                        ?: L("Verbunden · noch keine Daten von letzter Nacht", "Connected · no data from last night yet"),
                    color = Ink.accent, style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton(L("Jetzt aktualisieren", "Refresh now"), Modifier.fillMaxWidth()) { scope.launch { Recovery.refresh(context) } }
                Spacer(Modifier.height(8.dp))
                val auto = App.store.data.collectAsState().value.settings.autoRegulate
                Toggle(L("Gewichte anpassen", "Adjust weights"), L("Bei schlechter Erholung 5 bis 10 % leichter starten", "Start 5 to 10 % lighter on poorly recovered days"), auto) { v ->
                    App.store.updateSettings { it.copy(autoRegulate = v) }
                }
            }
            else -> PrimaryButton(L("Mit Health Connect verbinden", "Connect Health Connect"), Modifier.fillMaxWidth()) { ask.launch(Recovery.permissions) }
        }
    }
}

/** Shares finished workouts and body weights with Health Connect; off until the person turns it on. */
@Composable
private fun HealthWriteSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on = App.store.data.collectAsState().value.settings.healthWrite
    val status by HealthSync.status.collectAsState()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(on) { HealthSync.load(context); granted = HealthSync.granted(context) }
    OnResume { granted = HealthSync.granted(context) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        granted = got.containsAll(HealthSync.permissions)
        if (granted == true) {
            Haptics.success()
            App.store.updateSettings { it.copy(healthWrite = true) }
            scope.launch { HealthSync.sync(context) }
        }
    }
    if (!Recovery.available(context)) return
    Section(L("Mit Health Connect teilen", "Share with Health Connect"), Icons.Rounded.Sync) {
        Toggle(
            L("Trainings und Gewicht schreiben", "Write workouts and weight"),
            L("Samsung Health, Fit und andere Apps sehen deine Einheiten der letzten 90 Tage", "Samsung Health, Fit and other apps see your sessions from the last 90 days"),
            on && granted == true,
        ) { v ->
            if (!v) { App.store.updateSettings { it.copy(healthWrite = false) }; HealthSync.forget(context) }
            else if (granted == true) { App.store.updateSettings { it.copy(healthWrite = true) }; scope.launch { HealthSync.sync(context) } }
            else ask.launch(HealthSync.permissions)
        }
        if (on && granted == true) {
            Spacer(Modifier.height(4.dp))
            val at = status?.at ?: 0
            val time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            Text(
                when {
                    status?.error != null -> L("Schreiben fehlgeschlagen: ${status?.error}", "Writing failed: ${status?.error}")
                    at > 0 -> L("Zuletzt geschrieben: ${time.format(at)}", "Last written: ${time.format(at)}")
                    else -> L("Neue Trainings und Gewichte werden automatisch geschrieben.", "New workouts and weights are written automatically.")
                },
                color = if (status?.error != null) Ink.red else Ink.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Opt-in cycle-based training. Off by default; the date comes from Health Connect or is entered by
 * hand. Switching it off deletes what was stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c by App.store.cycle.collectAsState()
    var confirmOff by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(c.on, c.source) {
        if (c.on && c.source == "health") {
            granted = CycleHealth.granted(context)
            if (granted == true) CycleHealth.sync(context)
        }
    }
    OnResume { if (c.on && c.source == "health") granted = CycleHealth.granted(context) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        granted = got.containsAll(CycleHealth.permissions)
        if (granted == true) { Haptics.success(); scope.launch { CycleHealth.sync(context) } }
    }
    val dateFmt = DateTimeFormatter.ofPattern(L("d. MMMM yyyy", "MMMM d, yyyy"), I18n.locale)
    val start = Cycle.parse(c.lastStart)

    Section(L("Zyklus", "Cycle"), Icons.Rounded.CalendarMonth) {
        Toggle(L("Zyklusbasiertes Training", "Cycle-based training"), L("Aus, bis du es einschaltest", "Off until you turn it on"), c.on) { v ->
            if (v) App.store.setCycleOn(true)
            else if (c.lastStart.isNotBlank()) confirmOff = true
            else App.store.setCycleOn(false)
        }
        Text(
            L(
                "Wenn du möchtest, zeigt dir die App deine Zyklusphase, den Zyklustag und einen kurzen Hinweis für Training und Ernährung. " +
                    "Dafür braucht sie nur den ersten Tag deiner letzten Periode und deine übliche Zykluslänge. Die Angaben bleiben auf diesem Telefon, " +
                    "sind in keinem Backup enthalten und werden nie geteilt. Ausschalten löscht sie.",
                "If you like, the app shows your cycle phase, the cycle day and a short hint for training and food. " +
                    "All it needs is the first day of your last period and your usual cycle length. This stays on this phone, " +
                    "is left out of every backup and is never shared. Turning it off deletes it.",
            ),
            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
        )
        if (c.on) {
            Spacer(Modifier.height(12.dp))
            val hc = CycleHealth.available(context)
            if (hc) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(L("Selbst eintragen", "Enter myself"), c.source == "manual") { App.store.updateCycle { it.copy(source = "manual") } }
                    Chip("Health Connect", c.source == "health") { App.store.updateCycle { it.copy(source = "health") } }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (c.source == "health" && hc) {
                if (granted == true) {
                    Text(
                        start?.let { L("Verbunden · letzte Periode ab ${it.format(dateFmt)}", "Connected · last period from ${it.format(dateFmt)}") }
                            ?: L("Verbunden · noch keine Periodendaten in Health Connect", "Connected · no period data in Health Connect yet"),
                        color = Ink.accent, style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton(L("Jetzt aktualisieren", "Refresh now"), Modifier.fillMaxWidth()) { scope.launch { CycleHealth.sync(context) } }
                } else {
                    Text(L("Liest nur den Beginn deiner Perioden, sonst nichts.", "Reads only the start of your periods, nothing else."),
                        color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(L("Periodendaten freigeben", "Allow period data"), Modifier.fillMaxWidth()) { ask.launch(CycleHealth.permissions) }
                }
            } else {
                Text(L("Erster Tag der letzten Periode", "First day of your last period"), color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                Text(start?.format(dateFmt) ?: L("Noch nicht eingetragen", "Not entered yet"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                GhostButton(L("Datum wählen", "Pick date"), Modifier.fillMaxWidth()) { picking = true }
            }
            Spacer(Modifier.height(10.dp))
            Stepper(L("Übliche Zykluslänge", "Usual cycle length"), L("${c.length} Tage", "${c.length} days"), Modifier.fillMaxWidth(),
                { App.store.updateCycle { it.copy(length = (it.length - 1).coerceAtLeast(Cycle.MIN_LENGTH)) } },
                { App.store.updateCycle { it.copy(length = (it.length + 1).coerceAtMost(Cycle.MAX_LENGTH)) } })
            Spacer(Modifier.height(8.dp))
            Text(Coach.cycleDisclaimer, color = Ink.faint, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (picking) {
        // The picker works in UTC midnights; only today and the past can be chosen.
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (start ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        val colors = DatePickerDefaults.colors(
            containerColor = Ink.raised,
            selectedDayContainerColor = Ink.accent, selectedDayContentColor = Ink.bg,
            todayDateBorderColor = Ink.accent, todayContentColor = Ink.accent,
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        Haptics.click()
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        App.store.updateCycle { it.copy(lastStart = day.toString()) }
                    }
                    picking = false
                }) { Text(L("Übernehmen", "Save"), color = Ink.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(L("Abbrechen", "Cancel")) } },
            colors = colors,
        ) { DatePicker(state, colors = colors) }
    }

    if (confirmOff) AlertDialog(
        onDismissRequest = { confirmOff = false },
        title = { Text(L("Zyklusdaten löschen?", "Delete cycle data?")) },
        text = { Text(L("Das Startdatum und die Zykluslänge werden von diesem Telefon gelöscht. Einen Health-Connect-Zugriff kannst du in Health Connect entziehen.",
            "The start date and cycle length are deleted from this phone. Health Connect access can be removed in Health Connect.")) },
        confirmButton = {
            TextButton(onClick = { Haptics.warn(); App.store.setCycleOn(false); confirmOff = false }) { Text(L("Ausschalten und löschen", "Turn off and delete"), color = Ink.red) }
        },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** Reads a picked backup file; null if it cannot be read or is far too big to be one (a video, say). */
internal fun readBackup(context: android.content.Context, uri: Uri): String? = runCatching {
    // Read in chunks with a cap (readNBytes needs Android 13; the app runs from Android 10).
    context.contentResolver.openInputStream(uri)!!.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BACKUP) return@use null
        }
        out.toByteArray().decodeToString()
    }
}.getOrNull()

private const val MAX_BACKUP = 50 * 1024 * 1024
