package dev.mauve.fit.system

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import dev.mauve.fit.App
import dev.mauve.fit.MainActivity
import dev.mauve.fit.R
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import dev.mauve.fit.watch.WatchSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val CH_WORKOUT = "workout"
private const val CH_REST = "rest"
private const val CH_REST_VIBRATE = "rest_vibrate"
private const val CH_REST_QUIET = "rest_quiet"
private const val CH_REMIND = "reminder"
private const val ID_WORKOUT = 1
private const val ID_REST = 2
private const val ID_REMIND = 3

/** True while an activity of the app is on screen; alerts are skipped then (the screen buzzes itself). */
object Foreground {
    @Volatile var visible = false
}

fun createChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_WORKOUT, L("Laufendes Training", "Running workout"), NotificationManager.IMPORTANCE_LOW).apply {
        description = L("Zeigt Satz und Pausentimer, solange ein Training läuft", "Shows the set and rest timer while a workout runs")
        setShowBadge(false)
    })
    nm.createNotificationChannel(NotificationChannel(CH_REST, L("Pause vorbei", "Rest over"), NotificationManager.IMPORTANCE_HIGH).apply {
        description = L("Meldet das Ende der Pause, wenn die App im Hintergrund ist", "Alerts when a rest ends while the app is in the background")
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 150, 250)
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
        )
    })
    nm.createNotificationChannel(NotificationChannel(CH_REST_VIBRATE, L("Pause vorbei (nur Vibration)", "Rest over (vibration only)"), NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 150, 250)
        setSound(null, null)
    })
    nm.createNotificationChannel(NotificationChannel(CH_REST_QUIET, L("Pause vorbei (lautlos)", "Rest over (silent)"), NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(false)
        setSound(null, null)
    })
    nm.createNotificationChannel(NotificationChannel(CH_REMIND, L("Trainingserinnerung", "Workout reminder"), NotificationManager.IMPORTANCE_DEFAULT))
}

private fun openApp(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/**
 * Keeps an ongoing notification while a workout runs: current set with buttons, and the rest
 * countdown. When a rest ends with the app in the background it posts an alert.
 */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var restJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("cmd")?.let(WatchSync::handle)
        startForeground(ID_WORKOUT, build(App.store.data.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (restJob == null) {
            App.store.data.onEach { d ->
                if (d.active == null) { stopSelf(); return@onEach }
                getSystemService(NotificationManager::class.java).notify(ID_WORKOUT, build(d))
            }.launchIn(scope)
            restJob = App.store.data.map { it.active?.restEndsAt }.distinctUntilChanged().onEach { end ->
                if (end == null) return@onEach
                scope.launch {
                    delay((end - System.currentTimeMillis()).coerceAtLeast(0))
                    if (App.store.data.value.active?.restEndsAt == end && !Foreground.visible) restOver(App.store.data.value)
                }
            }.launchIn(scope)
        }
        return START_STICKY
    }

    private fun action(cmd: String, label: String, req: Int) = Notification.Action.Builder(
        null, label,
        PendingIntent.getService(this, req, Intent(this, WorkoutService::class.java).putExtra("cmd", cmd), PendingIntent.FLAG_IMMUTABLE),
    ).build()

    private fun build(d: AppData): Notification {
        val w = d.active
        val s = WatchSync.state(d)
        val resting = w?.restEndsAt?.let { it > System.currentTimeMillis() } == true
        val b = Notification.Builder(this, CH_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_fit)
            .setContentIntent(openApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setColor(0xFFDB9062.toInt())
        if (w == null) return b.setContentTitle("Mauve Fit").build()
        return if (resting) b.setContentTitle(L("Pause · ${w.name}", "Rest · ${w.name}"))
            .setContentText(L("Als Nächstes: ${s.exercise}", "Up next: ${s.exercise}"))
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(w.restEndsAt!!).setShowWhen(true)
            .addAction(action("plus15", "+15 s", 1)).addAction(action("skip", L("Überspringen", "Skip"), 2))
            .build()
        else b.setContentTitle(s.exercise)
            .setContentText(
                (if (s.warmup) L("Aufwärmen", "Warm-up") else L("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}")) +
                    if (s.bodyweight) L(" · ${s.reps} Wdh.", " · ${s.reps} reps") else " · ${Stats.fmtKg(s.weight)} kg × ${s.reps}",
            )
            .setUsesChronometer(true).setWhen(w.startedAt).setShowWhen(true)
            .addAction(action("done", L("Satz erledigt", "Set done"), 3))
            .build()
    }

    private fun restOver(d: AppData) {
        val s = WatchSync.state(d)
        val channel = when {
            d.settings.sound -> CH_REST
            d.settings.vibrate -> CH_REST_VIBRATE
            else -> CH_REST_QUIET
        }
        val n = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_fit)
            .setContentTitle(L("Pause vorbei", "Rest over"))
            .setContentText("${s.exercise} · " + if (s.warmup) L("Aufwärmen", "Warm-up") else L("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}"))
            .setContentIntent(openApp(this))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setColor(0xFFDB9062.toInt())
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_REST, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Starts or stops the service as workouts begin and end. */
        fun follow(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                App.store.data.map { it.active != null }.distinctUntilChanged().onEach { active ->
                    if (active) runCatching { context.startForegroundService(Intent(context, WorkoutService::class.java)) }
                }.launchIn(this)
            }
        }
    }
}

/** Schedules the next workout reminder and posts it when it fires. */
object Reminders {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val s = App.store.data.value.settings
        if (!s.reminder || s.reminderDays.isEmpty()) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.reminderHour, s.reminderMinute)
        while (!next.isAfter(now) || next.dayOfWeek.value !in s.reminderDays) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A 10-minute window needs no exact-alarm permission and is plenty for a reminder.
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
    }

    fun follow(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            App.store.data.map { it.settings.run { listOf(reminder, reminderDays, reminderHour, reminderMinute) } }
                .distinctUntilChanged().onEach { schedule(context) }.launchIn(this)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminders.schedule(context); return }
        val d = App.store.data.value
        val trainedToday = d.history.any { Stats.day(it.startedAt) == LocalDate.now() }
        if (!trainedToday && d.active == null) {
            val next = Stats.nextRoutine(d.routines, d.history, App.repo)
            val n = Notification.Builder(context, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat_fit)
                .setContentTitle(L("Zeit fürs Training", "Time to train"))
                .setContentText(next?.let { L("Heute dran: ${it.name} · ca. ${dev.mauve.fit.ui.estMinutes(it)} min", "Today: ${it.name} · about ${dev.mauve.fit.ui.estMinutes(it)} min") } ?: L("Dein Plan wartet.", "Your plan is waiting."))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setColor(0xFFDB9062.toInt())
                .build()
            context.getSystemService(NotificationManager::class.java).notify(ID_REMIND, n)
        }
        Reminders.schedule(context)
    }
}
