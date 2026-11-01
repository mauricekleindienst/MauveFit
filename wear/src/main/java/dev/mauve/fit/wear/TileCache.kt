package dev.mauve.fit.wear

import android.content.Context
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

/**
 * Last state the phone published, kept on the watch so the tile can render without the app open.
 * Written by [PhoneLink] while the app runs and by [StateListenerService] otherwise.
 */
object TileCache {
    private const val PREFS = "tile_cache"
    private const val KEY_JSON = "json"
    private const val KEY_NODE = "node"
    private const val KEY_AT = "at"
    private val json = Json { ignoreUnknownKeys = true }

    /** A cached state plus when it arrived; null before the phone ever sent one. */
    class Entry(val state: WatchState, val receivedAt: Long)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stores the raw JSON and asks the tile to redraw when it changed. */
    fun save(context: Context, raw: String, node: String?) {
        val p = prefs(context)
        val changed = p.getString(KEY_JSON, null) != raw || p.getString(KEY_NODE, null) != node
        p.edit().putString(KEY_JSON, raw).putString(KEY_NODE, node).putLong(KEY_AT, System.currentTimeMillis()).apply()
        if (changed) requestTileUpdate(context)
    }

    fun load(context: Context): Entry? {
        val p = prefs(context)
        val raw = p.getString(KEY_JSON, null) ?: return null
        val state = runCatching { json.decodeFromString(WatchState.serializer(), raw) }.getOrNull() ?: return null
        return Entry(state, p.getLong(KEY_AT, 0))
    }

    /** Node id of the phone that published the state, so commands go straight there. */
    fun node(context: Context): String? = prefs(context).getString(KEY_NODE, null)

    fun requestTileUpdate(context: Context) {
        runCatching { TileService.getUpdater(context.applicationContext).requestUpdate(MauveTileService::class.java) }
    }
}

/** Receives the phone's state while the watch app is closed and refreshes the tile. */
class StateListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == "/mauvefit/state" }.forEach { e ->
            val raw = DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json") ?: return@forEach
            TileCache.save(this, raw, e.dataItem.uri.host)
        }
    }
}
