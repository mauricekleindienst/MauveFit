package dev.mauve.fit.data

import kotlin.math.abs

/**
 * What the running workout looks like at a glance, for the notification and the widgets: the set
 * to do now, the one just done and the rest between them.
 */
object Live {
    data class Status(
        val workout: String,
        val startedAt: Long,
        val exercise: String,
        /** "Set 3 of 4 · 80 kg × 8", or the warm-up. */
        val next: String,
        /** "Set 2 done · 80 kg × 8"; null before the first set. */
        val last: String?,
        /** Wall-clock end of the running rest, null when none runs (or it is over). */
        val restEndsAt: Long?,
        val restTotalSec: Int,
        val done: Int,
        val total: Int,
        /** Every set is ticked off: only finishing is left. */
        val complete: Boolean,
        val warmups: Int = 0,
        /** Every ticked set, warm-ups included: "done:<ticked>" only counts once. */
        val ticked: Int = 0,
    ) {
        val resting: Boolean get() = restEndsAt != null
        /** There is an open set to tick off (not when all are done or the workout is still empty). */
        val canTick: Boolean get() = !complete && total + warmups > 0
    }

    /** The set that went from open to done between [old] and [new], if any. */
    fun ticked(old: Workout, new: Workout, now: Long = System.currentTimeMillis()): LastSet? {
        new.entries.forEachIndexed { i, e ->
            val before = old.entries.getOrNull(i)?.takeIf { it.exerciseId == e.exerciseId } ?: return@forEachIndexed
            e.sets.forEachIndexed { j, s ->
                if (s.done && before.sets.getOrNull(j)?.done == false) {
                    val number = if (s.warmup) 0 else e.sets.take(j + 1).count { !it.warmup }
                    return LastSet(e.exerciseId, s.weight, s.reps, number, now)
                }
            }
        }
        return null
    }

    /**
     * An entry whose working weights were changed by hand (or by the watch) is no longer running
     * at the reduced load it was suggested with: 100 kg lifted in a 70 % week is 100 kg, not 143.
     */
    fun manualLoad(old: Workout, new: Workout): List<ExerciseEntry> = new.entries.mapIndexed { i, e ->
        val before = old.entries.getOrNull(i)?.takeIf { it.exerciseId == e.exerciseId } ?: return@mapIndexed e
        if (abs(e.load - 1.0) < 1e-9 && e.fullTop <= 0) return@mapIndexed e
        fun working(x: ExerciseEntry) = x.sets.filter { !it.warmup }.map { it.weight }
        // Only a change of the working weights counts; warm-ups added in front shift nothing.
        if (working(before).sorted() == working(e).sorted()) return@mapIndexed e
        // The load is the heaviest working set against the full-load top, measured once: a back-off
        // set, a 0 typed by mistake or a typo fixed again cannot pile up into a wrong load.
        val ref = e.fullTop.takeIf { it > 0 } ?: working(before).maxOrNull()?.takeIf { it > 0 }?.let { it / before.load } ?: return@mapIndexed e
        val top = working(e).maxOrNull()?.takeIf { it > 0 } ?: return@mapIndexed e
        e.copy(load = (top / ref).coerceIn(0.1, 1.0), fullTop = ref)
    }

    /** False once [last] was unticked again (or its exercise removed), so it is no longer shown as done. */
    fun stillDone(w: Workout, last: LastSet): Boolean = w.entries.any { e ->
        e.exerciseId == last.exerciseId && e.sets.withIndex().any { (j, s) ->
            s.done && if (last.number == 0) s.warmup else !s.warmup && e.sets.take(j + 1).count { !it.warmup } == last.number
        }
    }

    fun status(d: AppData, title: (String) -> String?, bodyweight: (String) -> Boolean, now: Long = System.currentTimeMillis()): Status? {
        val w = d.active ?: return null
        val i = Supersets.current(w.entries)
        val entry = w.entries.getOrNull(i)
        val j = entry?.sets?.indexOfFirst { !it.done } ?: -1
        val set = if (j >= 0) entry?.sets?.get(j) else null
        val working = { e: ExerciseEntry -> e.sets.count { !it.warmup } }
        val next = when {
            entry == null -> L("Noch keine Übung", "No exercise yet")
            set == null -> L("Alle Sätze erledigt", "All sets done")
            else -> {
                val label = if (set.warmup) L("Aufwärmen", "Warm-up")
                else L("Satz ${entry.sets.take(j + 1).count { !it.warmup }} von ${working(entry)}", "Set ${entry.sets.take(j + 1).count { !it.warmup }} of ${working(entry)}")
                "$label · " + amount(set.weight, set.reps, bodyweight(entry.exerciseId))
            }
        }
        val last = w.lastSet?.let { l ->
            val what = if (l.number == 0) L("Aufwärmen erledigt", "Warm-up done") else L("Satz ${l.number} erledigt", "Set ${l.number} done")
            val name = title(l.exerciseId)?.takeIf { l.exerciseId != entry?.exerciseId }
            listOfNotNull(name, what, amount(l.weight, l.reps, bodyweight(l.exerciseId))).joinToString(" · ")
        }
        val complete = w.entries.isNotEmpty() && w.entries.all { e -> e.sets.all { it.done } }
        return Status(
            workout = w.name,
            startedAt = w.startedAt,
            exercise = entry?.let { title(it.exerciseId) } ?: L("Training", "Workout"),
            next = next,
            last = last,
            // Nothing comes after the last set: no countdown to it.
            restEndsAt = w.restEndsAt?.takeIf { it > now && !complete },
            restTotalSec = w.restTotalSec,
            done = w.entries.sumOf { e -> e.sets.count { it.done && !it.warmup } },
            total = w.entries.sumOf(working),
            complete = complete,
            warmups = w.entries.sumOf { e -> e.sets.count { it.warmup } },
            ticked = w.entries.sumOf { e -> e.sets.count { it.done } },
        )
    }

    fun amount(weight: Double, reps: Int, bodyweight: Boolean): String =
        if (bodyweight && weight == 0.0) L("$reps Wdh.", "$reps reps")
        else if (bodyweight) "+${Stats.fmtKg(weight)} kg × $reps"
        else "${Stats.fmtKg(weight)} kg × $reps"

    /** "1:30" for the rest left, rounded up so it never shows 0:00 while still running. */
    fun clock(ms: Long): String {
        val s = ((ms + 999) / 1000).coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}
