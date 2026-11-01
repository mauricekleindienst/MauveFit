package dev.mauve.fit.widget

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate

// Every widget wears the dark copper look, whatever theme the app uses.
internal val Bg = Color(0xFF111315)
internal val Surface = Color(0xFF1A1D21)
internal val Raised = Color(0xFF23272C)
internal val TextColor = Color(0xFFEDEFF2)
internal val Muted = Color(0xFFA6ACB4)
internal val Copper = Color(0xFFDB9062)
internal val Mauve = Color(0xFFC5A9E6)

internal fun style(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

/** Small copper caption above a widget's main line. */
@Composable
internal fun Eyebrow(text: String, color: Color = Copper) =
    Text(text, style = style(color, 12.sp, FontWeight.Medium), maxLines = 1)

/** A rounded button in the accent colour. */
@Composable
internal fun Pill(label: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.background(Copper).cornerRadius(50.dp).padding(horizontal = 14.dp, vertical = 7.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style(Bg, 13.sp, FontWeight.Bold), maxLines = 1)
    }
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
    fun follow(context: Context) {
        val app = context.applicationContext
        combine(App.store.data, Recovery.today) { d, r ->
            val today = todayKey()
            val food = d.food.filter { it.date == today }
            listOf(
                // Next workout: the running one down to its sets, the plans, the week.
                d.active?.name, d.active?.entries, d.routines, d.history.size, d.history.firstOrNull()?.id,
                d.settings.weeklyGoal, d.settings.blockStart, d.settings.blockWeeks, d.settings.language,
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
            .debounce(1000)
            .onEach { updateAll(app) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
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

/** Logs one 250 ml glass of water for today. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        App.store.addWater(todayKey(), GLASS_ML)
        App.store.flush()
        Widgets.updateAll(context)
    }
}

internal const val GLASS_ML = 250
