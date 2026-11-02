package dev.mauve.fit.wear

import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.Locale

private const val RESOURCES_VERSION = "1"

private object TileInk {
    const val COPPER = 0xFFDB9062.toInt()
    const val TEXT = 0xFFF3F3F6.toInt()
    const val MUTED = 0xFF8C8C9C.toInt()
    const val ON_COPPER = 0xFF0B0B0F.toInt()
}

/** German or English, following the watch language (same rule as the app). */
internal fun tileText(de: String, en: String) = if (Locale.getDefault().language == "de") de else en

/**
 * Tile with the running workout (exercise, set, load, rest end time, "Set done") or, without one,
 * the next routine and a "Start" button. Reads [TileCache]; buttons go through [TileActionActivity].
 */
class MauveTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                val tile = runCatching { buildTile(requestParams.deviceConfiguration) }
                    .getOrElse { hintTile(requestParams.deviceConfiguration, phoneConnected = true) }
                completer.set(tile)
            }
            "MauveTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "MauveTileResources"
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun buildTile(device: DeviceParameters): TileBuilders.Tile {
        val cached = TileCache.load(this)
        val connected = withTimeoutOrNull(2_000) {
            runCatching { Wearable.getNodeClient(this@MauveTileService).connectedNodes.await() }.getOrNull()
        }
        // Unknown (timeout or error) counts as connected: better show the last state than a false alarm.
        val phoneConnected = connected == null || connected.isNotEmpty()
        if (cached == null || !phoneConnected) return hintTile(device, phoneConnected)
        val s = cached.state
        val now = System.currentTimeMillis()
        return when {
            s.active && s.restEndsAt > now -> tile(activeLayout(device, s, resting = true), s.restEndsAt - now + 1_000)
            s.active -> tile(activeLayout(device, s, resting = false), 10 * 60_000L)
            else -> tile(idleLayout(device, s), 30 * 60_000L)
        }
    }

    private fun hintTile(device: DeviceParameters, phoneConnected: Boolean): TileBuilders.Tile {
        val title = if (phoneConnected) tileText("Mauve Fit am Handy öffnen", "Open Mauve Fit on your phone")
        else tileText("Handy nicht verbunden", "Phone not connected")
        val body = if (phoneConnected) tileText("Trainings kommen vom Handy", "Workouts come from your phone")
        else tileText("Bluetooth prüfen", "Check Bluetooth")
        val layout = PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(label("Mauve Fit", TileInk.COPPER))
            .setContent(
                column(
                    text(title, Typography.TYPOGRAPHY_TITLE3, TileInk.TEXT, maxLines = 3),
                    gap(4f),
                    text(body, Typography.TYPOGRAPHY_CAPTION1, TileInk.MUTED, maxLines = 2),
                ),
            )
            .setPrimaryChipContent(chip(tileText("Öffnen", "Open"), openApp(), device))
            .build()
        return tile(layout, 15 * 60_000L)
    }

    private fun activeLayout(device: DeviceParameters, s: WatchState, resting: Boolean): LayoutElementBuilders.LayoutElement {
        val top = if (resting) tileText("Pause bis ${time(s.restEndsAt)}", "Rest until ${time(s.restEndsAt)}")
        else if (s.exerciseCount > 1) tileText("Übung ${s.exerciseIndex + 1} von ${s.exerciseCount}", "Exercise ${s.exerciseIndex + 1} of ${s.exerciseCount}")
        else s.workout.ifBlank { "Mauve Fit" }
        val set = if (s.warmup) tileText("Aufwärmen", "Warm-up")
        else tileText("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}")
        val content = column(
            text(if (resting) tileText("Nächster: ${s.exercise}", "Up next: ${s.exercise}") else s.exercise,
                Typography.TYPOGRAPHY_TITLE3, TileInk.TEXT, maxLines = 2),
            gap(2f),
            text(if (resting && s.last.isNotBlank()) s.last else set, Typography.TYPOGRAPHY_CAPTION1, TileInk.MUTED, maxLines = 2),
            gap(2f),
            text(load(s), Typography.TYPOGRAPHY_TITLE2, TileInk.COPPER),
        )
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(label(top, if (resting) TileInk.COPPER else TileInk.MUTED))
            .setContent(content)
            .setPrimaryChipContent(chip(tileText("Satz erledigt", "Set done"), command("done"), device))
            .build()
    }

    private fun idleLayout(device: DeviceParameters, s: WatchState): LayoutElementBuilders.LayoutElement {
        val content = if (s.suggestion.isNotBlank()) column(
            text(tileText("Nächstes Training", "Next workout"), Typography.TYPOGRAPHY_CAPTION1, TileInk.MUTED),
            gap(2f),
            text(s.suggestion, Typography.TYPOGRAPHY_TITLE2, TileInk.TEXT, maxLines = 2),
            gap(4f),
            text(tileText("Diese Woche ${s.week}/${s.goal}", "This week ${s.week}/${s.goal}"), Typography.TYPOGRAPHY_CAPTION1, if (s.goal > 0 && s.week >= s.goal) TileInk.COPPER else TileInk.MUTED),
        ) else column(
            text(tileText("Kein Training läuft", "No workout running"), Typography.TYPOGRAPHY_TITLE3, TileInk.TEXT, maxLines = 2),
        )
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(label("Mauve Fit", TileInk.COPPER))
            .setContent(content)
            .setPrimaryChipContent(chip(tileText("Starten", "Start"), command("start"), device))
            .build()
    }

    // --- building blocks ---

    private fun tile(layout: LayoutElementBuilders.LayoutElement, freshnessMs: Long): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(freshnessMs.coerceAtLeast(1_000))
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()

    private fun text(value: String, typography: Int, color: Int, maxLines: Int = 1): LayoutElementBuilders.LayoutElement =
        Text.Builder(this, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .build()

    private fun label(value: String, color: Int) = text(value, Typography.TYPOGRAPHY_CAPTION1, color)

    private fun gap(height: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    /** Centered column; tapping it opens the full app. */
    private fun column(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp()).build())
            .apply { items.forEach { addContent(it) } }
            .build()

    private fun chip(value: String, click: ModifiersBuilders.Clickable, device: DeviceParameters): LayoutElementBuilders.LayoutElement =
        CompactChip.Builder(this, value, click, device)
            .setChipColors(ChipColors(TileInk.COPPER, TileInk.ON_COPPER))
            .build()

    private fun openApp(): ModifiersBuilders.Clickable = clickable("open", WatchActivity::class.java.name, null)

    private fun command(cmd: String): ModifiersBuilders.Clickable = clickable("cmd_$cmd", TileActionActivity::class.java.name, cmd)

    private fun clickable(id: String, className: String, cmd: String?): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(className)
            .apply {
                if (cmd != null) addKeyToExtraMapping(TileActionActivity.EXTRA_CMD, ActionBuilders.AndroidStringExtra.Builder().setValue(cmd).build())
            }
            .build()
        return ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()
    }

    private fun time(ms: Long): String = DateFormat.getTimeFormat(this).format(Date(ms))

    private fun load(s: WatchState): String =
        if (s.bodyweight) tileText("${s.reps} Wdh.", "${s.reps} reps") else "${weight(s.weight)} kg x ${s.reps}"

    /** Up to two decimals without trailing zeros; comma in German, dot otherwise. */
    private fun weight(v: Double): String {
        val t = "%.2f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')
        return tileText(t.replace('.', ','), t)
    }
}

