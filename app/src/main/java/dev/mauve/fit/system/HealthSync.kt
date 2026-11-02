package dev.mauve.fit.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import dev.mauve.fit.App
import dev.mauve.fit.data.HealthExport
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Writes finished workouts and body weights to Health Connect, so Samsung Health, Fit and other
 * apps see them. Off until switched on in the settings; what was written is remembered on the
 * phone, so each change is written once and deletions in the app are removed there too.
 */
object HealthSync {
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    private const val PREFS = "health_sync"
    private const val KEY = "synced"
    private val lock = Mutex()

    /** Result of the last write: when it worked (0 = never) and why the last attempt failed, if it did. */
    data class Status(val at: Long = 0, val error: String? = null)

    private val state = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = state

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Loads the stored status, for the settings screen. */
    fun load(context: Context) {
        if (state.value == null) state.value = prefs(context).let { Status(it.getLong("at", 0), it.getString("error", null)) }
    }

    private fun save(context: Context, s: Status) {
        state.value = s
        prefs(context).edit().putLong("at", s.at).putString("error", s.error).apply()
    }

    suspend fun granted(context: Context): Boolean =
        Recovery.available(context) && runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)

    @OptIn(FlowPreview::class)
    fun follow(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            App.store.data.map { Triple(it.settings.healthWrite, it.history, it.bodyWeights) }
                .distinctUntilChanged()
                // Ticking off sets changes nothing here, but saving a workout and its rating come close together.
                .debounce(3_000)
                .onEach { (on, _, _) -> if (on) sync(app) }
                .launchIn(this)
        }
    }

    /** Writes what changed since last time; quietly does nothing without access. */
    suspend fun sync(context: Context) = lock.withLock {
        if (!granted(context)) return@withLock
        val prefs = prefs(context)
        load(context)
        val synced = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { e ->
            e.substringAfterLast(':').toIntOrNull()?.let { e.substringBeforeLast(':') to it }
        }.toMap()
        val plan = HealthExport.plan(App.store.data.value, synced)
        if (plan.empty) return@withLock
        val client = HealthConnectClient.getOrCreate(context)
        runCatching {
            // The version only has to grow, so the write time serves: a newer write replaces the older one.
            val version = System.currentTimeMillis()
            val records: List<Record> = plan.workouts.map { session(it, version) } + plan.weights.map { b ->
                val at = Instant.ofEpochMilli(b.at)
                WeightRecord(at, offset(at), Mass.kilograms(b.kg), Metadata.manualEntry(HealthExport.weightId(b), version))
            }
            if (records.isNotEmpty()) client.insertRecords(records)
            val gone = plan.delete.groupBy { if (it.startsWith("weight-")) WeightRecord::class else ExerciseSessionRecord::class }
            gone.forEach { (type: KClass<out Record>, ids) -> client.deleteRecords(type, emptyList(), ids) }
            prefs.edit().putStringSet(KEY, plan.after.map { (id, h) -> "$id:$h" }.toSet()).apply()
            save(context, Status(System.currentTimeMillis()))
        }.onFailure { e -> save(context, Status(state.value?.at ?: 0, e.message ?: e.javaClass.simpleName)) }
    }

    private fun offset(at: Instant) = ZoneId.systemDefault().rules.getOffset(at)

    private fun session(w: Workout, version: Long): ExerciseSessionRecord {
        val start = Instant.ofEpochMilli(w.startedAt)
        val end = Instant.ofEpochMilli(w.finishedAt ?: w.startedAt)
        val sets = w.doneSets.size
        val summary = L("$sets Sätze · ${w.volume.toInt()} kg Volumen", "$sets sets · ${w.volume.toInt()} kg volume")
        return ExerciseSessionRecord(
            startTime = start, startZoneOffset = offset(start),
            endTime = end, endZoneOffset = offset(end),
            metadata = Metadata.manualEntry(HealthExport.workoutId(w), version),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = w.name,
            notes = listOf(summary, w.note).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    /** Forgets what was written, e.g. when the switch goes off, so turning it on again writes all. */
    fun forget(context: Context) = prefs(context).edit().remove(KEY).apply()
}
