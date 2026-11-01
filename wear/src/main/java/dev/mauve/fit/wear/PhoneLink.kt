package dev.mauve.fit.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

private const val PATH_STATE = "/mauvefit/state"
private const val PATH_CMD = "/mauvefit/cmd"

/** Follows the phone's published state and sends commands back to it. */
class PhoneLink(context: Context) : DataClient.OnDataChangedListener {
    private val data = Wearable.getDataClient(context)
    private val messages = Wearable.getMessageClient(context)
    private val nodes = Wearable.getNodeClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state
    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected

    fun connect() {
        data.addListener(this)
        scope.launch {
            // Pick up whatever the phone published while the watch app was closed.
            runCatching { data.dataItems.await().use { buf -> buf.firstOrNull { it.uri.path == PATH_STATE }?.let(::apply) } }
            _connected.value = runCatching { nodes.connectedNodes.await().isNotEmpty() }.getOrDefault(false)
        }
    }

    fun disconnect() = data.removeListener(this)

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == PATH_STATE }.forEach { apply(it.dataItem) }
    }

    private fun apply(item: DataItem) {
        val raw = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: return
        runCatching { json.decodeFromString(WatchState.serializer(), raw) }.onSuccess {
            _state.value = it
            _connected.value = true
        }
    }

    suspend fun send(cmd: String) {
        runCatching {
            val targets = nodes.connectedNodes.await()
            _connected.value = targets.isNotEmpty()
            targets.forEach { messages.sendMessage(it.id, PATH_CMD, cmd.toByteArray()).await() }
        }
    }
}
