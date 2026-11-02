package dev.mauve.fit.wear

import android.content.Context
import androidx.core.net.toUri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
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
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirror of the phone's WatchState (dev.mauve.fit.watch); field names must match. */
@Serializable
data class WatchState(
    val active: Boolean = false,
    val workout: String = "",
    val startedAt: Long = 0,
    val exercise: String = "",
    val exerciseIndex: Int = 0,
    val exerciseCount: Int = 0,
    val setNumber: Int = 0,
    val setCount: Int = 0,
    val warmup: Boolean = false,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val bodyweight: Boolean = false,
    val restEndsAt: Long = 0,
    val restTotalSec: Int = 0,
    val doneSets: Int = 0,
    val suggestion: String = "",
    /** The set just done, "Set 2 done · 80 kg × 8"; empty before the first. */
    val last: String = "",
    val week: Int = 0,
    val goal: Int = 0,
)

private const val PATH_STATE = "/mauvefit/state"
private const val PATH_CMD = "/mauvefit/cmd"

/** The state item from any node; only the paired phone publishes it. */
private val STATE_URI = "wear://*$PATH_STATE".toUri()

/** How far the watch got reaching the phone app. */
enum class Link { Checking, NoPhone, NoApp, Ready }

/** Follows the phone's published state and sends commands back to it. */
class PhoneLink(context: Context) : DataClient.OnDataChangedListener {
    private val app = context.applicationContext
    private val data = Wearable.getDataClient(context)
    private val messages = Wearable.getMessageClient(context)
    private val nodes = Wearable.getNodeClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poll: Job? = null

    /** Node that published the state: the phone running Mauve Fit, so commands go there and nowhere else. */
    @Volatile private var phoneNode: String? = TileCache.node(app)

    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state
    private val _link = MutableStateFlow(Link.Checking)
    val link: StateFlow<Link> = _link

    fun connect() {
        data.addListener(this, STATE_URI, DataClient.FILTER_LITERAL)
        poll?.cancel()
        poll = scope.launch {
            // Pick up whatever the phone published while the watch app was closed.
            runCatching {
                data.getDataItems(STATE_URI).await().use { buf -> buf.firstOrNull()?.let(::apply) }
            }
            // The node API has no change callback without a declared capability; a slow poll is enough here.
            while (isActive) {
                refresh()
                delay(15_000)
            }
        }
    }

    fun disconnect() {
        data.removeListener(this)
        poll?.cancel()
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == PATH_STATE }.forEach { apply(it.dataItem) }
    }

    private fun apply(item: DataItem) {
        val raw = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: return
        runCatching { json.decodeFromString(WatchState.serializer(), raw) }.onSuccess {
            phoneNode = item.uri.host
            _state.value = it
            // Keeps the tile in step while the app is open.
            TileCache.save(app, raw, item.uri.host)
            _link.value = Link.Ready
        }
    }

    private suspend fun refresh() {
        val connected = runCatching { nodes.connectedNodes.await() }.getOrNull() ?: return
        val phone = phoneNode
        _link.value = when {
            connected.isEmpty() -> Link.NoPhone
            phone == null -> Link.NoApp
            connected.any { it.id == phone } -> Link.Ready
            // The state came from a phone that is no longer reachable.
            else -> Link.NoPhone
        }
    }

    /** Sends [cmd] to the phone; false when it could not be delivered. */
    suspend fun send(cmd: String): Boolean {
        val ok = runCatching {
            val target = phoneNode ?: nodes.connectedNodes.await().let { n -> n.firstOrNull { it.isNearby } ?: n.firstOrNull() }?.id
            if (target == null) false else { messages.sendMessage(target, PATH_CMD, cmd.toByteArray()).await(); true }
        }.getOrDefault(false)
        if (!ok) refresh()
        return ok
    }
}
