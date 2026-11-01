package dev.mauve.fit.data

import android.content.Context
import dev.mauve.fit.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** All app state in one JSON file, written shortly after every change. */
@OptIn(FlowPreview::class)
class Store(context: Context) {
    private val file = File(context.filesDir, "data.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    init {
        _data.drop(1).debounce(300).onEach(::save).launchIn(scope)
    }

    fun update(block: (AppData) -> AppData) = _data.update(block)

    private fun load(): AppData = runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrElse { AppData() }

    /** Writes immediately; called when the app goes to the background so a quick kill loses nothing. */
    fun flush() = scope.launch { save(_data.value) }

    @Synchronized
    private fun save(d: AppData) {
        // Write beside and rename so a crash mid-write never leaves a truncated file.
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), d))
        tmp.renameTo(file)
    }

    // --- Workout flow ------------------------------------------------------------

    /**
     * Starts a workout from a plan (or an empty one); does nothing while another workout runs.
     * [short] trims it to the coach's short version for days with little time.
     */
    fun startWorkout(routine: Routine?, short: Boolean = false) = update { d ->
        if (d.active != null) return@update d
        val plan = routine?.let { if (short) Coach.shortVersion(it) else it }
        val entries = plan?.exercises.orEmpty().map { p ->
            val weight = Coach.startWeight(d, App.repo, p.exerciseId, p.reps)
            ExerciseEntry(p.exerciseId, p.restSec, List(p.sets) { SetEntry(weight = weight, reps = p.reps, target = p.reps) })
        }
        d.copy(active = Workout(name = plan?.name ?: L("Freies Training", "Free workout"), routineId = routine?.id, entries = entries))
    }

    /** Applies a weight change to the unticked sets of one exercise (coach suggestion). */
    fun adjustRemaining(index: Int, delta: Double) = updateActive { w ->
        w.copy(entries = w.entries.mapIndexed { i, e ->
            if (i != index) e
            else e.copy(sets = e.sets.map { s -> if (!s.done && !s.warmup) s.copy(weight = (s.weight + delta).coerceAtLeast(0.0)) else s })
        })
    }

    // --- Meal prep ----------------------------------------------------------------------

    fun setPortions(recipeId: String, portions: Int) = update { d ->
        d.copy(mealPlan = if (portions <= 0) d.mealPlan - recipeId else d.mealPlan + (recipeId to portions.coerceAtMost(14)))
    }

    fun toggleShopping(item: String) = update { d ->
        d.copy(shoppingDone = if (item in d.shoppingDone) d.shoppingDone - item else d.shoppingDone + item)
    }

    fun clearMealPlan() = update { it.copy(mealPlan = emptyMap(), shoppingDone = emptySet()) }

    fun startDeload() = updateSettings { it.copy(deloadUntil = System.currentTimeMillis() + 7 * Stats.DAY_MS) }
    fun endDeload() = updateSettings { it.copy(deloadUntil = 0) }

    fun updateActive(block: (Workout) -> Workout) = update { d -> d.active?.let { d.copy(active = block(it)) } ?: d }

    /** Saves the active workout; returns its id, or null if nothing was ticked off (then it is just closed). */
    fun finishWorkout(): String? {
        var saved: String? = null
        update { d ->
            val w = d.active ?: return@update d
            // Unticked sets are dropped; an exercise with nothing done is not logged.
            val entries = w.entries.map { e -> e.copy(sets = e.sets.filter { it.done }) }.filter { e -> e.sets.any { !it.warmup } }
            if (entries.isEmpty()) d.copy(active = null)
            else {
                saved = w.id
                d.copy(active = null, history = listOf(w.copy(entries = entries, finishedAt = System.currentTimeMillis(), restEndsAt = null)) + d.history)
            }
        }
        return saved
    }

    /** Replaces the exercise at [index], keeping the planned set count and reps. */
    fun swapExercise(index: Int, exerciseId: String) = updateActive { w ->
        val d = data.value
        w.copy(entries = w.entries.mapIndexed { i, e ->
            if (i != index) e
            else {
                val reps = e.sets.firstOrNull { !it.warmup }?.let { if (it.target > 0) it.target else it.reps } ?: 10
                val weight = Coach.startWeight(d, App.repo, exerciseId, reps)
                e.copy(exerciseId = exerciseId, sets = e.sets.filter { !it.warmup }.map { SetEntry(weight, reps, target = reps) })
            }
        })
    }

    /** Puts ramp-up sets in front of the first working set (once). */
    fun addWarmups(index: Int) = updateActive { w ->
        val s = data.value.settings
        w.copy(entries = w.entries.mapIndexed { i, e ->
            if (i != index || e.sets.any { it.warmup }) e
            else {
                val work = e.sets.firstOrNull()?.weight ?: 0.0
                e.copy(sets = Coach.warmups(work, s.weightStep, s.barWeight) + e.sets)
            }
        })
    }

    // --- Profile and body ------------------------------------------------------------

    /** Finishes the intro: stores the profile and replaces the plans with the generated program. */
    fun completeOnboarding(profile: Profile, program: List<Routine>) = update { d ->
        d.copy(
            profile = profile.copy(onboarded = true),
            routines = program,
            settings = d.settings.copy(
                weeklyGoal = profile.days,
                defaultRest = program.firstOrNull()?.exercises?.lastOrNull()?.restSec ?: d.settings.defaultRest,
            ),
        )
    }

    // --- Backup ------------------------------------------------------------------------

    fun export(): String = json.encodeToString(AppData.serializer(), _data.value)

    /** Replaces everything with a backup; false if the file is not a Mauve Fit backup. */
    fun import(text: String): Boolean = runCatching { json.decodeFromString<AppData>(text) }
        .onSuccess { restored -> update { restored }; flush() }.isSuccess

    /** Deletes all data and returns to the intro. */
    fun reset() { update { AppData() }; flush() }

    fun setFeel(workoutId: String, feel: Int, note: String) = update { d ->
        d.copy(history = d.history.map { if (it.id == workoutId) it.copy(feel = feel, note = note) else it })
    }

    fun addBodyWeight(kg: Double) = update { d -> d.copy(bodyWeights = listOf(BodyWeight(System.currentTimeMillis(), kg)) + d.bodyWeights) }
    fun deleteBodyWeight(at: Long) = update { d -> d.copy(bodyWeights = d.bodyWeights.filterNot { it.at == at }) }

    fun discardWorkout() = update { it.copy(active = null) }

    fun addToActive(exerciseId: String) = updateActive { w ->
        val d = data.value
        val reps = Stats.lastSets(d.history, exerciseId).firstOrNull()?.reps ?: 10
        val weight = Coach.startWeight(d, App.repo, exerciseId, reps)
        w.copy(entries = w.entries + ExerciseEntry(exerciseId, d.settings.defaultRest, List(3) { SetEntry(weight, reps, target = reps) }))
    }

    // --- Plans -------------------------------------------------------------------

    fun saveRoutine(r: Routine) = update { d ->
        val exists = d.routines.any { it.id == r.id }
        d.copy(routines = if (exists) d.routines.map { if (it.id == r.id) r else it } else d.routines + r)
    }

    fun deleteRoutine(id: String) = update { d -> d.copy(routines = d.routines.filterNot { it.id == id }) }

    fun addToRoutine(routineId: String, exerciseId: String) = update { d ->
        d.copy(routines = d.routines.map {
            if (it.id == routineId) it.copy(exercises = it.exercises + PlanExercise(exerciseId, restSec = d.settings.defaultRest)) else it
        })
    }

    fun toggleFavorite(id: String) = update { d ->
        d.copy(favorites = if (id in d.favorites) d.favorites - id else d.favorites + id)
    }

    fun deleteWorkout(id: String) = update { d -> d.copy(history = d.history.filterNot { it.id == id }) }

    fun updateSettings(block: (Settings) -> Settings) = update { it.copy(settings = block(it.settings)) }
}
