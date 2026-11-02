package dev.mauve.fit.data

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
    ) {
        val resting: Boolean get() = restEndsAt != null
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
        return Status(
            workout = w.name,
            startedAt = w.startedAt,
            exercise = entry?.let { title(it.exerciseId) } ?: L("Training", "Workout"),
            next = next,
            last = last,
            restEndsAt = w.restEndsAt?.takeIf { it > now },
            restTotalSec = w.restTotalSec,
            done = w.entries.sumOf { e -> e.sets.count { it.done && !it.warmup } },
            total = w.entries.sumOf(working),
            complete = w.entries.isNotEmpty() && w.entries.all { e -> e.sets.all { it.done } },
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
