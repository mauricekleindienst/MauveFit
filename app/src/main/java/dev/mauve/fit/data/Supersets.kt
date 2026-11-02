package dev.mauve.fit.data

/**
 * Supersets: consecutive exercises with the same group id are trained alternately, one set of
 * each in turn, and the rest only comes after the last one of the round.
 */
object Supersets {

    /**
     * Keeps groups well-formed after edits: a group needs two or more neighbours, and an id that
     * shows up again further down (after a move) starts a group of its own.
     */
    fun <T> clean(items: List<T>, id: (T) -> String?, withId: (T, String?) -> T): List<T> {
        val out = items.toMutableList()
        val seen = mutableSetOf<String>()
        var i = 0
        while (i < items.size) {
            val g = id(items[i])
            var end = i
            while (g != null && end + 1 < items.size && id(items[end + 1]) == g) end++
            if (g != null) {
                val fixed = when {
                    end == i -> null
                    g in seen -> newId()
                    else -> g
                }
                seen += g
                for (j in i..end) out[j] = withId(items[j], fixed)
            }
            i = end + 1
        }
        return out
    }

    /**
     * The neighbours trained together with [index] (given the group id of every slot), or null if
     * it is not part of a superset.
     */
    fun members(groups: List<String?>, index: Int): IntRange? {
        val g = groups.getOrNull(index) ?: return null
        var start = index
        var end = index
        while (start > 0 && groups[start - 1] == g) start--
        while (end < groups.lastIndex && groups[end + 1] == g) end++
        return if (end > start) start..end else null
    }

    /** "A1", "A2", "B1"...: the group's letter in this plan or workout and the position within it. */
    fun label(groups: List<String?>, index: Int): String? {
        val run = members(groups, index) ?: return null
        val letter = 'A' + (0 until run.first).count { j -> members(groups, j)?.first == j }
        return "$letter${index - run.first + 1}"
    }

    /** Where to go after a set: the page to show, and whether the round is over and rest is due. */
    data class Step(val page: Int, val rest: Boolean)

    /**
     * After a set at [index]: the next exercise of the group that still has sets, without rest;
     * once the round is through, rest and start over at the first one with sets left, or move on.
     */
    /**
     * The exercise to train now when there is no screen to look at (the watch): the first one with
     * open sets, and inside a superset the member that is behind, so A and B alternate.
     */
    fun current(entries: List<ExerciseEntry>): Int {
        val first = entries.indexOfFirst { e -> e.sets.any { !it.done } }
        if (first < 0) return entries.lastIndex
        val run = members(entries.map { it.superset }, first) ?: return first
        val open = run.filter { j -> entries[j].sets.any { !it.done } }
        // Warm-ups come first and do not count as a round, or A's warm-up would hand over to B.
        open.firstOrNull { j -> entries[j].sets.any { !it.done && it.warmup } }?.let { return it }
        return open.minBy { j -> entries[j].sets.count { it.done && !it.warmup } }
    }

    fun afterSet(entries: List<ExerciseEntry>, index: Int): Step? {
        val run = members(entries.map { it.superset }, index) ?: return null
        fun open(j: Int) = entries[j].sets.any { !it.done }
        (index + 1..run.last).firstOrNull(::open)?.let { return Step(it, rest = false) }
        return Step(run.firstOrNull(::open) ?: (run.last + 1), rest = true)
    }
}

fun List<PlanExercise>.withSupersetsCleaned(): List<PlanExercise> =
    Supersets.clean(this, { it.superset }) { p, g -> p.copy(superset = g) }

@JvmName("entriesWithSupersetsCleaned")
fun List<ExerciseEntry>.withSupersetsCleaned(): List<ExerciseEntry> =
    Supersets.clean(this, { it.superset }) { e, g -> e.copy(superset = g) }

/** Whether the exercise at [i] is trained as a superset with the one after it. */
fun List<PlanExercise>.linkedWithNext(i: Int): Boolean =
    i + 1 < size && this[i].superset != null && this[i].superset == this[i + 1].superset

/** Links the exercise at [i] with the next one, or splits them apart if they already are. */
fun List<PlanExercise>.toggleSupersetWithNext(i: Int): List<PlanExercise> {
    if (i + 1 >= size) return this
    val g = this[i].superset
    val out = if (linkedWithNext(i)) {
        // Everything after i in the group continues as a group of its own.
        val split = newId()
        val runEnd = (i + 1 until size).takeWhile { this[it].superset == g }.last()
        mapIndexed { j, p -> if (j in i + 1..runEnd) p.copy(superset = split) else p }
    } else {
        val group = g ?: newId()
        val next = this[i + 1].superset
        mapIndexed { j, p -> if (j == i || j == i + 1 || (next != null && p.superset == next)) p.copy(superset = group) else p }
    }
    return out.withSupersetsCleaned()
}
