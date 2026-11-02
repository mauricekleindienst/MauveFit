package dev.mauve.fit.data

/**
 * What to write to Health Connect: finished workouts and body weights of the last [WINDOW_DAYS]
 * days. Each record is keyed by a stable id and a hash of its content, so only new or changed
 * entries are written and entries deleted in the app are removed there too.
 */
object HealthExport {
    const val WINDOW_DAYS = 90L

    data class Plan(
        val workouts: List<Workout>,
        val weights: List<BodyWeight>,
        /** Ids that were written before and no longer exist in the app. */
        val delete: Set<String>,
        /** The full state after the plan has run, to remember for the next time. */
        val after: Map<String, Int>,
    ) {
        val empty get() = workouts.isEmpty() && weights.isEmpty() && delete.isEmpty()
    }

    /** The start time is part of the id, so an id alone tells whether it is still inside the window. */
    fun workoutId(w: Workout) = "workout-${w.startedAt}-${w.id}"
    fun weightId(b: BodyWeight) = "weight-${b.at}"

    /** Name, times, sets and note: anything that shows up in another health app. */
    private fun hash(w: Workout) = listOf(w.name, w.startedAt, w.finishedAt, w.doneSets.size, w.volume, w.note).hashCode()
    private fun hash(b: BodyWeight) = b.kg.hashCode()

    fun plan(d: AppData, synced: Map<String, Int>, now: Long = System.currentTimeMillis()): Plan {
        val since = now - WINDOW_DAYS * 86_400_000L
        val workouts = d.history.filter { it.finishedAt != null && it.startedAt >= since && it.finishedAt > it.startedAt }
        val weights = d.bodyWeights.filter { it.at >= since && it.kg > 0 }
        val current = workouts.associate { workoutId(it) to hash(it) } + weights.associate { weightId(it) to hash(it) }
        // Only ids inside the window are deleted: older ones simply fell out of it.
        val gone = synced.keys.filter { it !in current }.filter { id ->
            val at = id.split('-').getOrNull(1)?.toLongOrNull()
            at == null || at >= since
        }.toSet()
        return Plan(
            workouts = workouts.filter { synced[workoutId(it)] != current[workoutId(it)] },
            weights = weights.filter { synced[weightId(it)] != current[weightId(it)] },
            delete = gone,
            after = current,
        )
    }
}
