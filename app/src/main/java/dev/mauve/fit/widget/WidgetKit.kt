package dev.mauve.fit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import dev.mauve.fit.R
import dev.mauve.fit.watch.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mauve.fit.App
import dev.mauve.fit.MainActivity
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.Recovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

// Every widget wears the dark copper look, whatever theme the app uses.
internal val Bg = Color(0xFF111315)
internal val Surface = Color(0xFF1A1D21)
internal val Raised = Color(0xFF23272C)
internal val TextColor = Color(0xFFEDEFF2)
internal val Muted = Color(0xFFA6ACB4)
/**
 * The accent picked in the settings, in its dark-theme tone (the widgets are always dark). The
 * wallpaper accent takes the wallpaper's primary tone on Android 12+, as in the app.
 */
internal val AccentInk: Color
    get() {
        val accent = dev.mauve.fit.ui.Accent.of(App.store.data.value.settings.accent)
        val ctx = Widgets.context
        if (accent == dev.mauve.fit.ui.Accent.Wallpaper && ctx != null && android.os.Build.VERSION.SDK_INT >= 31) {
            return androidx.compose.material3.dynamicDarkColorScheme(ctx).primary
        }
        return accent.onInk
    }
internal val Mauve = Color(0xFFC5A9E6)

internal fun style(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

/** Small copper caption above a widget's main line. */
@Composable
internal fun Eyebrow(text: String, modifier: GlanceModifier = GlanceModifier, color: Color = AccentInk) =
    Text(text, modifier, style = style(color, 12.sp, FontWeight.Medium), maxLines = 1)

/** A rounded button in the accent colour. */
@Composable
internal fun Pill(label: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.background(AccentInk).cornerRadius(50.dp).padding(horizontal = 14.dp, vertical = 7.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style(Bg, 13.sp, FontWeight.Bold), maxLines = 1)
    }
}

/**
 * A clock that runs by itself on the home screen (a RemoteViews chronometer), so the rest counts
 * down without the widget being redrawn every second. [at] is wall-clock time: the end of the rest
 * when counting [down], the start when counting up.
 */
@Composable
internal fun Clock(at: Long, down: Boolean, color: Color, size: Float, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
        setChronometer(R.id.clock, SystemClock.elapsedRealtime() + (at - System.currentTimeMillis()), null, true)
        setChronometerCountDown(R.id.clock, down)
        setTextColor(R.id.clock, color.toArgb())
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, size)
    }
    AndroidRemoteViews(views, modifier)
}

/** Opens the app, optionally asking for a screen through [MainActivity.EXTRA_OPEN]. */
internal fun openApp(context: Context, open: String? = null): Action =
    actionStartActivity(appIntent(context, open))

internal fun appIntent(context: Context, open: String?): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { if (open != null) putExtra(MainActivity.EXTRA_OPEN, open) }

/** ISO date of today, the key of the food diary and the water map. */
internal fun todayKey(): String = LocalDate.now().toString()

/** Keeps every placed widget in step with the store; cheap when none are placed. */
object Widgets {
    /** Re-renders all widgets now (also used by the action callbacks after they changed data). */
    suspend fun updateAll(context: Context) {
        runCatching { NextWidget().updateAll(context) }
        runCatching { NutritionWidget().updateAll(context) }
        runCatching { StreakWidget().updateAll(context) }
    }

    @OptIn(FlowPreview::class)
    /** For the wallpaper accent, which needs a context to read the wallpaper's colours. */
    @Volatile internal var context: Context? = null
        private set

    fun follow(context: Context) {
        val app = context.applicationContext
        this.context = app
        combine(App.store.data, Recovery.today) { d, r ->
            val today = todayKey()
            val food = d.food.filter { it.date == today }
            listOf(
                // Next workout: the running one down to its sets, the plans, the week.
                d.active?.name, d.active?.entries, d.active?.restEndsAt, d.active?.lastSet, d.routines, d.history.size, d.history.firstOrNull()?.id,
                d.settings.weeklyGoal, d.settings.blockStart, d.settings.blockWeeks, d.settings.language, d.settings.accent,
                // Weekly challenge and streak.
                d.challengesDone, d.freezes, d.frozenWeeks, d.profile.level, d.profile.setup,
                // Nutrition today.
                today, food.sumOf { it.kcal }, food.sumOf { it.protein }, d.water[today],
                d.bodyWeights.firstOrNull()?.kg, d.profile.goal,
                // Cardio.
                d.cardio.size, d.cardio.firstOrNull()?.id,
                // Readiness.
                r?.score,
            )
        }
            .distinctUntilChanged()
            // Short while a workout runs, so a ticked set shows up right away.
            .debounce { if (App.store.data.value.active != null) 200 else 1000 }
            .onEach { updateAll(app) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        App.store.data.map { it.active?.restEndsAt }.distinctUntilChanged()
            .onEach { scheduleRestEnd(app, it) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        scheduleMidnight(app)
    }

    /** Bumped when a rest runs out: nothing in the store changes then, but the widget must. */
    val tick = MutableStateFlow(0L)

    suspend fun restOver(context: Context) {
        tick.value = System.currentTimeMillis()
        runCatching { NextWidget().updateAll(context) }
    }

    /**
     * Redraws the widgets when a rest ends. The workout service does that too, but it may not be
     * allowed to run (a workout started from the watch), and the widget clock would count below zero.
     */
    fun scheduleRestEnd(context: Context, end: Long?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 3, Intent(context, RestEndReceiver::class.java).putExtra("end", end ?: 0L),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (end != null && end > System.currentTimeMillis()) am.setWindow(AlarmManager.RTC, end + 500, 5_000L, pi)
    }

    /**
     * Wakes the widgets just after midnight: "today" (food, water) and on Mondays the week change
     * then, but nothing in the store does. Not a wake-up alarm, a sleeping screen shows nothing anyway.
     */
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 2, Intent(context, MidnightReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val at = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC, at, 10 * 60_000L, pi)
    }
}

/** Ends a rest that ran out and switches the widgets to the next set. */
class RestEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val end = intent.getLongExtra("end", 0L)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (end > 0) App.store.endRest(end)
                Widgets.restOver(context)
            } finally { pending.finish() }
        }
    }
}

/** Redraws the widgets for the new day, then sets itself up for the next midnight. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // The watch's week count turns over at Monday midnight too, with nothing changed in the store.
            // A freeze covering last week is applied first, or the widget shows a broken streak.
            try { App.store.settleStreak(); Widgets.updateAll(context); WatchSync.publish(context) } finally {
                Widgets.scheduleMidnight(context)
                pending.finish()
            }
        }
    }
}

// --- Actions ---------------------------------------------------------------------------
// Callbacks run in the app process. If it was dead, Android starts it first, so App.onCreate has
// already loaded the store and the language before onAction runs.

/** Starts the suggested next plan (unless a workout runs) and opens the workout screen. */
class StartNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = App.store
        if (store.data.value.active == null) {
            val d = store.data.value
            store.startWorkout(Stats.nextRoutine(d.routines, d.history, App.repo))
            store.flush()
        }
        Widgets.updateAll(context)
        // Opening from a broadcast may be refused on newer Android; the widget then shows "Open".
        runCatching { context.startActivity(appIntent(context, "workout")) }
    }
}

/** Ticks off the current set from the widget; the rest starts like on the phone and the watch. */
class SetDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchSync.handle(parameters[Ticked]?.let { "done:$it" } ?: "done")
        App.store.flush()
        Widgets.updateAll(context)
    }

    companion object {
        /** The ticked count the widget was drawn with. */
        val Ticked = ActionParameters.Key<Int>("ticked")
    }
}

/** Ends the rest early. */
class SkipRestAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchSync.handle("skip")
        App.store.flush()
        Widgets.updateAll(context)
    }
}

/** Logs one 250 ml glass of water for today. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        App.store.addWater(todayKey(), GLASS_ML)
        App.store.flush()
        Widgets.updateAll(context)
    }
}

internal const val GLASS_ML = 250
