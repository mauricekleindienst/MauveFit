package dev.mauve.fit.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.mauve.fit.App
import dev.mauve.fit.MainActivity
import dev.mauve.fit.R
import dev.mauve.fit.data.Intervals
import dev.mauve.fit.data.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The interval timer's run, kept outside the screen so it goes on with the app in the background
 * or the screen off. Only clock anchors are stored: elapsed = banked + (now - startedAt) while
 * running, so the countdown is computed from real time and never drifts. startedAt 0 = stopped.
 */
object IntervalRun {
    data class State(val plan: Intervals.Plan = Intervals.emom(10), val startedAt: Long = 0L, val banked: Long = 0L) {
        val running get() = startedAt != 0L
        val idle get() = !running && banked == 0L
        fun elapsed(now: Long = SystemClock.elapsedRealtime()) = banked + if (running) (now - startedAt).coerceAtLeast(0) else 0
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** A new plan only while nothing runs, so changing the steppers never jumps a running timer. */
    fun setPlan(plan: Intervals.Plan) {
        if (_state.value.idle) _state.value = State(plan)
    }

    fun start(context: Context) {
        val s = _state.value
        val done = Intervals.at(s.plan, s.elapsed()).phase == Intervals.Phase.Done
        _state.value = s.copy(startedAt = SystemClock.elapsedRealtime(), banked = if (done) 0L else s.banked)
        context.startForegroundService(Intent(context, IntervalService::class.java))
    }

    fun pause() {
        val s = _state.value
        if (s.running) _state.value = s.copy(banked = s.elapsed(), startedAt = 0L)
    }

    fun reset() {
        _state.value = State(_state.value.plan)
    }

    /** The last phase ran out: stop on the finished state. */
    internal fun finish() {
        val s = _state.value
        _state.value = s.copy(banked = s.plan.totalMs, startedAt = 0L)
    }
}

/**
 * Runs the interval timer's cues while it runs: a beep and a buzz on every phase change and a
 * light tick on the last three seconds, with the app in the background or the screen off too. Its
 * notification counts the current phase down and has a pause button.
 */
class IntervalService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    /** The phone dozes with the screen off and a plain delay can end late; held only while running. */
    private val wake by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MauveFit:interval").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) IntervalRun.pause()
        val s = IntervalRun.state.value
        try {
            startForeground(ID_INTERVAL, build(s), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            if (e !is IllegalStateException && e !is SecurityException) throw e
            stopSelf()
            return START_NOT_STICKY
        }
        if (!s.running) { stop(); return START_NOT_STICKY }
        wake.acquire((s.plan.totalMs - s.elapsed()).coerceAtLeast(0) + 10_000)
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_NOT_STICKY
    }

    private suspend fun run() {
        val nm = getSystemService(NotificationManager::class.java)
        var s = IntervalRun.state.value
        var last = Intervals.at(s.plan, s.elapsed()).let { it.phase to it.round }
        var lastSec = -1
        while (scope.isActive) {
            s = IntervalRun.state.value
            if (!s.running) { stop(); return }
            val tick = Intervals.at(s.plan, s.elapsed())
            val cue = tick.phase to tick.round
            if (cue != last) {
                when (tick.phase) {
                    Intervals.Phase.Work -> { Haptics.restOver(); beep(ToneGenerator.TONE_PROP_BEEP2, 300) }
                    Intervals.Phase.Rest -> { Haptics.heavy(); beep(ToneGenerator.TONE_PROP_BEEP, 250) }
                    Intervals.Phase.Done -> { Haptics.success(); beep(ToneGenerator.TONE_PROP_ACK, 500) }
                }
                last = cue
                if (tick.phase == Intervals.Phase.Done) {
                    IntervalRun.finish()
                    // Let the last beep play out before the tone generator goes.
                    delay(600)
                    stop()
                    return
                }
                nm.notify(ID_INTERVAL, build(s))
            }
            val sec = Intervals.secondsLeft(tick)
            if (sec != lastSec && sec in 1..3 && tick.phaseMs > 5_000) Haptics.tick()
            lastSec = sec
            delay(100)
        }
    }

    private fun beep(kind: Int, ms: Int) {
        if (App.store.data.value.settings.sound) runCatching { tone?.startTone(kind, ms) }
    }

    private fun stop() {
        if (wake.isHeld) wake.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun build(s: IntervalRun.State): Notification {
        val tick = Intervals.at(s.plan, s.elapsed())
        val phase = when (tick.phase) {
            Intervals.Phase.Work -> L("Arbeit", "Work")
            Intervals.Phase.Rest -> L("Pause", "Rest")
            Intervals.Phase.Done -> L("Fertig", "Done")
        }
        val open = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "tools"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 4, Intent(this, IntervalService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CH_INTERVAL)
            .setSmallIcon(R.drawable.ic_stat_fit)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setColor(0xFFDB9062.toInt())
            .setContentTitle(L("Intervall-Timer · $phase", "Interval timer · $phase"))
            .setContentText(L("Runde ${tick.round.coerceAtLeast(1)} von ${s.plan.rounds}", "Round ${tick.round.coerceAtLeast(1)} of ${s.plan.rounds}"))
            // Counts the current phase down by itself, so the notification needs no update per second.
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + tick.remainingMs).setShowWhen(true)
            .addAction(Notification.Action.Builder(null, L("Pause", "Pause"), pause).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        tone?.release()
        if (wake.isHeld) wake.release()
        super.onDestroy()
    }

    companion object {
        private const val CH_INTERVAL = "interval"
        private const val ID_INTERVAL = 6
        private const val ACTION_PAUSE = "dev.mauve.fit.interval.PAUSE"

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH_INTERVAL, L("Intervall-Timer", "Interval timer"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = L("Zeigt den laufenden Intervall-Timer; die Töne kommen von der App selbst", "Shows the running interval timer; the beeps come from the app itself")
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
    }
}
