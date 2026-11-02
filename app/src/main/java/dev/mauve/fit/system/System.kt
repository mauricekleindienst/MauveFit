package dev.mauve.fit.system

import android.os.PowerManager
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
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
import android.os.Build
import android.os.IBinder
import dev.mauve.fit.App
import dev.mauve.fit.MainActivity
import dev.mauve.fit.R
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Live
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

/** v2: shown in the normal section, not folded away under "Silent" like the first channel was. */
private const val CH_WORKOUT = "workout_live"
private const val CH_WORKOUT_OLD = "workout"
private const val CH_REST = "rest"
private const val CH_REST_VIBRATE = "rest_vibrate"
private const val CH_REST_QUIET = "rest_quiet"
private const val CH_REMIND = "reminder"
private const val CH_WORKOUT_ALERT = "workout_alert"
private const val ID_WORKOUT = 1
private const val ID_REST = 2
private const val ID_REMIND = 3
private const val ID_REPORT = 4
private const val ID_CLOSED = 5

/** After a restart or a change of time zone or clock, alarms set for a local time are set again. */
private val RESCHEDULE = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)

/** True while an activity of the app is on screen; alerts are skipped then (the screen buzzes itself). */
object Foreground {
    @Volatile var visible = false
}

fun createChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.deleteNotificationChannel(CH_WORKOUT_OLD)
    // Default importance so it sits with the other notifications and shows its buttons; it stays
    // silent, the rest alert below is the one that makes noise.
    nm.createNotificationChannel(NotificationChannel(CH_WORKOUT, L("Laufendes Training", "Running workout"), NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = L("Zeigt Satz und Pausentimer, solange ein Training läuft", "Shows the set and rest timer while a workout runs")
        setSound(null, null)
        enableVibration(false)
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
    nm.createNotificationChannel(NotificationChannel(CH_WORKOUT_ALERT, L("Training gestartet", "Workout started"), NotificationManager.IMPORTANCE_HIGH).apply {
        description = L("Meldet ein von der Uhr gestartetes Training, solange die App im Hintergrund ist", "Shows a workout started from the watch while the app is in the background")
    })
}

private fun openApp(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Opens the app straight on the running workout (see MainActivity.EXTRA_OPEN). */
private fun openWorkout(context: Context) = PendingIntent.getActivity(
    context, 1,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "workout"),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/**
 * Keeps an ongoing notification while a workout runs: current set with buttons, and the rest
 * countdown. When a rest ends with the app in the background it posts an alert.
 */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var restJob: Job? = null

    /**
     * Held for the length of a rest. With the screen off the phone dozes and a plain delay can end
     * minutes late; a partial wake lock keeps the countdown on time. It never outlives the rest.
     */
    private val wake by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MauveFit:rest").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("cmd")?.let(WatchSync::handle)
        // A sticky restart in the background may not go foreground (Android 12+): show the plain
        // notice instead and stop, rather than crash. Opening the app starts the service again.
        try {
            startForeground(ID_WORKOUT, build(App.store.data.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            if (e !is IllegalStateException && e !is SecurityException) throw e
            postStarted(this, App.store.data.value)
            stopSelf()
            return START_NOT_STICKY
        }
        if (restJob == null) {
            App.store.data.onEach { d ->
                if (d.active == null) { stopSelf(); return@onEach }
                getSystemService(NotificationManager::class.java).notify(ID_WORKOUT, build(d))
            }.launchIn(scope)
            // Forgotten workouts end on their own, so phone and watch stop showing them.
            scope.launch {
                while (true) {
                    if (App.store.closeIdleWorkout()) closedNotice(this@WorkoutService)
                    delay(5 * 60_000L)
                }
            }
            restJob = App.store.data.map { it.active?.restEndsAt }.distinctUntilChanged().onEach { end ->
                if (end == null) { if (wake.isHeld) wake.release(); return@onEach }
                val left = (end - System.currentTimeMillis()).coerceAtLeast(0)
                wake.acquire(left + 10_000)
                if (Build.VERSION.SDK_INT >= 36) scope.launch {
                    while (App.store.data.value.active?.restEndsAt == end && System.currentTimeMillis() < end) {
                        delay(2_000)
                        val d = App.store.data.value
                        if (d.active?.restEndsAt == end) getSystemService(NotificationManager::class.java).notify(ID_WORKOUT, build(d))
                    }
                }
                scope.launch {
                    // A little past the end: woken a few ms early, everything would still read as resting.
                    delay(left + 300)
                    val d = App.store.data.value
                    if (d.active?.restEndsAt != end) return@launch
                    App.store.endRest(end)
                    val after = App.store.data.value
                    // Swap the finished countdown for the next set, so the chronometer never runs below zero.
                    getSystemService(NotificationManager::class.java).notify(ID_WORKOUT, build(after))
                    // No alarm for a rest that ended long ago (service restarted later) or after the last set.
                    val complete = Live.status(after, { null }, { false })?.complete == true
                    if (!Foreground.visible && !complete && System.currentTimeMillis() - end < 30_000) restOver(after)
                    // The widgets count the rest down on their own; at zero they switch to the next set.
                    dev.mauve.fit.widget.Widgets.restOver(this@WorkoutService)
                    if (wake.isHeld) wake.release()
                }
            }.launchIn(scope)
        }
        return START_STICKY
    }

    private fun action(cmd: String, label: String, req: Int) = Notification.Action.Builder(
        null, label,
        PendingIntent.getService(this, req, Intent(this, WorkoutService::class.java).putExtra("cmd", cmd), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
    ).build()

    private fun build(d: AppData): Notification {
        val w = d.active
        val st = Live.status(d, { App.repo[it]?.title }, { App.repo[it]?.bodyweight == true })
        val b = Notification.Builder(this, CH_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_fit)
            .setContentIntent(openWorkout(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setColor(0xFFDB9062.toInt())
        // Android 16: a live update, shown like a timer on the lock screen and in the status bar.
        if (Build.VERSION.SDK_INT >= 36) b.setRequestPromotedOngoing(true)
        if (w == null || st == null) return b.setContentTitle("Mauve Fit").build()
        val resting = st.resting
        if (Build.VERSION.SDK_INT >= 36) b.setStyle(progress(w, resting))
        val sets = L("${st.done} von ${st.total} Sätzen", "${st.done} of ${st.total} sets")
        return when {
            // Rest: what was just done, the countdown in the header and what comes next.
            resting -> {
                val next = L("Als Nächstes: ${st.exercise} · ${st.next}", "Up next: ${st.exercise} · ${st.next}")
                b.setContentTitle(st.last ?: L("Pause · ${w.name}", "Rest · ${w.name}"))
                    .setContentText(next)
                    .setSubText(L("Pause", "Rest"))
                    .bigText(next)
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(st.restEndsAt!!).setShowWhen(true)
                    .addAction(action("plus15", "+15 s", 1)).addAction(action("skip", L("Überspringen", "Skip"), 2))
                    .build()
            }
            !st.canTick -> b.setContentTitle(if (st.complete) L("Alle Sätze erledigt", "All sets done") else w.name)
                .setContentText(st.last ?: L("Tippen, um das Training abzuschließen", "Tap to finish the workout"))
                .setSubText(sets)
                .setUsesChronometer(true).setWhen(w.startedAt).setShowWhen(true)
                .build()
            else -> b.setContentTitle(st.exercise)
                .setContentText(st.next)
                .setSubText(sets)
                .bigText(listOfNotNull(st.next, st.last?.let { L("Zuletzt: $it", "Last: $it") }).joinToString("\n"))
                .setUsesChronometer(true).setWhen(w.startedAt).setShowWhen(true)
                // With the ticked count, so a double tap before the shade redraws ticks only one set.
                // Its own PendingIntent per count: an old button keeps its old count and is rejected.
                .addAction(action("done:${st.ticked}", L("Satz erledigt", "Set done"), 1000 + st.ticked))
                .build()
        }
    }

    /** Two lines when expanded; Android 16 keeps its progress bar instead. */
    private fun Notification.Builder.bigText(text: String) =
        if (Build.VERSION.SDK_INT >= 36) this else setStyle(Notification.BigTextStyle().bigText(text))

    /**
     * Android 16 progress bar of the live update: during a rest the time running out, otherwise
     * the workout itself, one segment per exercise and filled by the sets done.
     */
    @android.annotation.TargetApi(36)
    private fun progress(w: dev.mauve.fit.data.Workout, resting: Boolean): Notification.ProgressStyle {
        val copper = 0xFFDB9062.toInt()
        val mauve = 0xFFC5A9E6.toInt()
        val style = Notification.ProgressStyle().setStyledByProgress(true)
        if (resting) {
            val total = (w.restTotalSec * 1000).coerceAtLeast(1)
            val left = ((w.restEndsAt ?: 0) - System.currentTimeMillis()).coerceIn(0, total.toLong()).toInt()
            return style.setProgressSegments(listOf(Notification.ProgressStyle.Segment(total).setColor(mauve)))
                .setProgress(total - left)
        }
        val segments = w.entries.map { e -> e.sets.count { !it.warmup }.coerceAtLeast(1) }
        val done = w.entries.sumOf { e -> e.sets.count { it.done && !it.warmup } }
        return style.setProgressSegments(segments.map { Notification.ProgressStyle.Segment(it).setColor(copper) })
            .setProgress(done)
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
            .setContentText(Live.status(d, { App.repo[it]?.title }, { App.repo[it]?.bodyweight == true })?.let { "${it.exercise} · ${it.next}" } ?: s.exercise)
            .setContentIntent(openWorkout(this))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            // A timer: Android treats it like one (do-not-disturb rules, lock screen, the watch).
            .setCategory(Notification.CATEGORY_ALARM)
            .setColor(0xFFDB9062.toInt())
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_REST, n)
    }

    override fun onDestroy() {
        if (wake.isHeld) wake.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Starts or stops the service as workouts begin and end. */
        fun follow(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                App.store.data.map { it.active != null }.distinctUntilChanged().onEach { active ->
                    if (active) start(context)
                    // Drops the stand-in notice if the workout ended before the app was opened.
                    else context.getSystemService(NotificationManager::class.java).cancel(ID_WORKOUT)
                }.launchIn(this)
            }
        }

        /**
         * Starts the service for the active workout. Android 12+ refuses that while the app is in the
         * background (a workout started from the watch), so a plain notice stands in until the app
         * comes to the front, where MainActivity calls this again. The service's notification uses
         * the same id and replaces the notice.
         */
        fun start(context: Context) {
            val d = App.store.data.value
            if (d.active == null) return
            try {
                context.startForegroundService(Intent(context, WorkoutService::class.java))
            } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) postStarted(context, d)
            } catch (_: SecurityException) {
                postStarted(context, d)
            }
        }

        /** Tells the user a forgotten workout was closed, and where the sets went. */
        fun closedNotice(context: Context) {
            val n = Notification.Builder(context, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat_fit)
                .setContentTitle(L("Training automatisch beendet", "Workout ended automatically"))
                .setContentText(L("Seit 3 Stunden nichts mehr eingetragen. Erledigte Sätze sind im Verlauf.", "Nothing logged for 3 hours. Done sets are in your history."))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setColor(0xFFDB9062.toInt())
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_CLOSED, n) }
        }

        private fun postStarted(context: Context, d: AppData) {
            val w = d.active ?: return
            val n = Notification.Builder(context, CH_WORKOUT_ALERT)
                .setSmallIcon(R.drawable.ic_stat_fit)
                .setContentTitle(L("Training läuft", "Workout running"))
                .setContentText(L("${w.name} · Tippen, um das Training zu öffnen", "${w.name} · Tap to open the workout"))
                .setContentIntent(openWorkout(context))
                .setCategory(Notification.CATEGORY_WORKOUT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true).setWhen(w.startedAt).setShowWhen(true)
                .setColor(0xFFDB9062.toInt())
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_WORKOUT, n) }
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
        if (intent.action in RESCHEDULE) { Reminders.schedule(context); dev.mauve.fit.widget.Widgets.scheduleMidnight(context); return }
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

/** Schedules the weekly report for Monday 9:00 and posts it when it fires. */
object WeeklyReports {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 1, Intent(context, WeeklyReportReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!App.store.data.value.settings.weeklyReport) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        while (!next.isAfter(now) || next.dayOfWeek != java.time.DayOfWeek.MONDAY) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 30 * 60_000L, pi)
    }

    fun follow(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            App.store.data.map { it.settings.weeklyReport }.distinctUntilChanged().onEach { schedule(context) }.launchIn(this)
        }
    }
}

class WeeklyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESCHEDULE) { WeeklyReports.schedule(context); return }
        val d = App.store.data.value
        // Nobody wants a report about nothing: only once the app has been used for training.
        if (d.settings.weeklyReport && d.history.isNotEmpty()) {
            val r = dev.mauve.fit.data.WeeklyReport.of(d, dev.mauve.fit.data.WeeklyReport.lastWeekStart(), App.repo::get)
            val n = Notification.Builder(context, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat_fit)
                .setContentTitle(L("Dein Wochenbericht", "Your weekly report"))
                .setContentText(r.headline)
                .setStyle(Notification.BigTextStyle().bigText(r.headline + "\n" + L(
                    "${r.sets} Sätze · ${Stats.amount(r.volume, r.reps)}" + (r.volumeChange?.let { " (${if (it >= 0) "+" else ""}$it %)" } ?: "") + if (r.cardioMinutes > 0) " · ${r.cardioMinutes} min Cardio" else "",
                    "${r.sets} sets · ${Stats.amount(r.volume, r.reps)}" + (r.volumeChange?.let { " (${if (it >= 0) "+" else ""}$it %)" } ?: "") + if (r.cardioMinutes > 0) " · ${r.cardioMinutes} min cardio" else "",
                )))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setColor(0xFFDB9062.toInt())
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_REPORT, n) }
        }
        WeeklyReports.schedule(context)
    }
}
