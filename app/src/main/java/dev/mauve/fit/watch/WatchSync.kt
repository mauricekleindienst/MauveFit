package dev.mauve.fit.watch

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the watch shows. Mirrored in the wear module (same field names); published as JSON in the
 * data item [PATH_STATE]. The watch sends commands as messages on [PATH_CMD].
 */
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

const val PATH_STATE = "/mauvefit/state"
const val PATH_CMD = "/mauvefit/cmd"

object WatchSync {
    private val json = Json { encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The watch follows the first exercise that still has unticked sets. */
    fun state(d: AppData): WatchState {
        val w = d.active ?: return WatchState(suggestion = Stats.nextRoutine(d.routines, d.history, App.repo)?.name.orEmpty())
        val idx = w.entries.indexOfFirst { e -> e.sets.any { !it.done } }.takeIf { it >= 0 } ?: (w.entries.size - 1)
        val entry = w.entries.getOrNull(idx)
        val setIdx = entry?.sets?.indexOfFirst { !it.done }?.takeIf { it >= 0 } ?: 0
        val set = entry?.sets?.getOrNull(setIdx)
        val ex = entry?.let { App.repo[it.exerciseId] }
        return WatchState(
            active = true,
            workout = w.name,
            startedAt = w.startedAt,
            exercise = ex?.title ?: L("Training", "Workout"),
            exerciseIndex = idx.coerceAtLeast(0),
            exerciseCount = w.entries.size,
            setNumber = entry?.sets?.take(setIdx + 1)?.count { !it.warmup } ?: 0,
            setCount = entry?.sets?.count { !it.warmup } ?: 0,
            warmup = set?.warmup == true,
            weight = set?.weight ?: 0.0,
            reps = set?.reps ?: 0,
            bodyweight = ex?.bodyweight == true,
            restEndsAt = w.restEndsAt ?: 0,
            restTotalSec = w.restTotalSec,
            doneSets = w.doneSets.size,
        )
    }

    /** Publishes the state whenever it changes; harmless when no watch is paired. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        val client = Wearable.getDataClient(context)
        App.store.data.map(::state).distinctUntilChanged().debounce(150).onEach { s ->
            runCatching {
                val req = PutDataMapRequest.create(PATH_STATE).apply {
                    dataMap.putString("json", json.encodeToString(WatchState.serializer(), s))
                    dataMap.putLong("t", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                client.putDataItem(req).await()
            }
        }.launchIn(scope)
    }

    /** Applies a command from the watch to the active workout. */
    fun handle(cmd: String) {
        val store = App.store
        when (cmd) {
            "start" -> {
                val d = store.data.value
                store.startWorkout(Stats.nextRoutine(d.routines, d.history, App.repo))
            }
            "skip" -> store.updateActive { it.copy(restEndsAt = null) }
            "plus15" -> store.updateActive { it.copy(restEndsAt = (it.restEndsAt ?: System.currentTimeMillis()) + 15_000, restTotalSec = it.restTotalSec + 15) }
            "done", "w+", "w-", "r+", "r-" -> store.updateActive { w ->
                val i = w.entries.indexOfFirst { e -> e.sets.any { !it.done } }
                if (i < 0) return@updateActive w
                val entry = w.entries[i]
                val j = entry.sets.indexOfFirst { !it.done }
                val step = store.data.value.settings.weightStep
                val sets = entry.sets.toMutableList()
                val s = sets[j]
                sets[j] = when (cmd) {
                    "w+" -> s.copy(weight = s.weight + step)
                    "w-" -> s.copy(weight = (s.weight - step).coerceAtLeast(0.0))
                    "r+" -> s.copy(reps = s.reps + 1)
                    "r-" -> s.copy(reps = (s.reps - 1).coerceAtLeast(0))
                    else -> s.copy(done = true)
                }
                val updated = w.copy(entries = w.entries.toMutableList().also { it[i] = entry.copy(sets = sets) })
                if (cmd == "done") {
                    val rest = if (s.warmup) 45 else entry.restSec
                    updated.copy(restEndsAt = System.currentTimeMillis() + rest * 1000L, restTotalSec = rest)
                } else updated
            }
        }
    }
}

/** Receives commands from the watch, even while the phone app is in the background. */
class WatchCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PATH_CMD) WatchSync.handle(String(event.data))
    }
}
