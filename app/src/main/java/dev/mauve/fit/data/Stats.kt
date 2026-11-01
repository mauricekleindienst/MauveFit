package dev.mauve.fit.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

object Stats {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    const val DAY_MS = 24L * 3600 * 1000

    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Epley estimate of the one-rep max. */
    fun e1rm(s: SetEntry): Double = if (s.reps <= 0) 0.0 else s.weight * (1 + s.reps / 30.0)

    /** Working sets of the most recent workout that contained this exercise. */
    fun lastSets(history: List<Workout>, exerciseId: String): List<SetEntry> =
        history.firstNotNullOfOrNull { w -> w.entries.firstOrNull { it.exerciseId == exerciseId }?.sets?.filter { !it.warmup } }.orEmpty()

    /**
     * Progressive overload: if every set last time hit the target reps, add one weight step;
     * otherwise repeat the weight.
     */
    fun suggestWeight(last: List<SetEntry>, targetReps: Int, step: Double): Double {
        if (last.isEmpty()) return 0.0
        val top = last.maxOf { it.weight }
        val allHit = last.all { it.reps >= targetReps }
        return if (allHit && top > 0) top + step else top
    }

    fun roundTo(v: Double, step: Double): Double = if (step <= 0) v else (v / step).roundToInt() * step

    fun workoutsThisWeek(history: List<Workout>): Int {
        val start = weekStart(LocalDate.now(zone))
        return history.count { !day(it.startedAt).isBefore(start) }
    }

    /** Consecutive weeks (ending with this one, or last if this one is still open) that met the goal. */
    fun weekStreak(history: List<Workout>, goal: Int): Int {
        val perWeek = history.groupingBy { weekStart(day(it.startedAt)) }.eachCount()
        var week = weekStart(LocalDate.now(zone))
        if ((perWeek[week] ?: 0) < goal) week = week.minusWeeks(1)
        var streak = 0
        while ((perWeek[week] ?: 0) >= goal) { streak++; week = week.minusWeeks(1) }
        return streak
    }

    /** Volume (kg × reps) for each of the last [weeks] weeks, oldest first. */
    fun weeklyVolume(history: List<Workout>, weeks: Int = 8): List<Pair<LocalDate, Double>> {
        val now = weekStart(LocalDate.now(zone))
        val byWeek = history.groupBy { weekStart(day(it.startedAt)) }.mapValues { (_, ws) -> ws.sumOf { it.volume } }
        return (weeks - 1 downTo 0).map { i -> now.minusWeeks(i.toLong()).let { it to (byWeek[it] ?: 0.0) } }
    }

    /** Days of the current week on which a workout was logged. */
    fun weekDays(history: List<Workout>): Set<LocalDate> {
        val start = weekStart(LocalDate.now(zone))
        return history.map { day(it.startedAt) }.filter { !it.isBefore(start) }.toSet()
    }

    /** When each target muscle was last trained. */
    fun lastTrained(history: List<Workout>, repo: ExerciseRepo): Map<String, Long> {
        val out = mutableMapOf<String, Long>()
        history.forEach { w -> w.entries.forEach { e -> repo[e.exerciseId]?.target?.let { out.putIfAbsent(it, w.startedAt) } } }
        return out
    }

    /**
     * The plan whose muscles have rested longest, so the suggestion follows recovery rather than
     * a fixed order. Ties fall back to plan order after the last one trained.
     */
    fun nextRoutine(routines: List<Routine>, history: List<Workout>, repo: ExerciseRepo): Routine? {
        val usable = routines.filter { it.exercises.isNotEmpty() }
        if (usable.isEmpty()) return null
        val last = lastTrained(history, repo)
        val lastId = history.firstOrNull { it.routineId != null }?.routineId
        val start = usable.indexOfFirst { it.id == lastId } + 1
        val ordered = usable.indices.map { usable[(start + it) % usable.size] }
        val now = System.currentTimeMillis()
        return ordered.maxByOrNull { r ->
            val targets = r.exercises.mapNotNull { repo[it.exerciseId]?.target }.distinct()
            // Average hours of rest across the plan's muscles; untrained muscles count as fully rested.
            targets.map { t -> last[t]?.let { ((now - it) / 3_600_000.0).coerceAtMost(96.0) } ?: 96.0 }.average() -
                ordered.indexOf(r) * 0.01
        }
    }

    /** Completed working sets per target muscle in the last 7 days. */
    fun muscleSets(history: List<Workout>, repo: ExerciseRepo): Map<String, Int> {
        val since = System.currentTimeMillis() - 7 * DAY_MS
        val counts = mutableMapOf<String, Int>()
        history.filter { it.startedAt >= since }.forEach { w ->
            w.entries.forEach { e ->
                repo[e.exerciseId]?.target?.let { m -> counts[m] = (counts[m] ?: 0) + e.sets.count { !it.warmup } }
            }
        }
        return counts
    }

    data class Session(val date: LocalDate, val best: SetEntry, val e1rm: Double, val volume: Double, val reps: Int)

    /** Every finished session of one exercise, oldest first (warm-ups ignored). */
    fun sessions(history: List<Workout>, exerciseId: String): List<Session> =
        history.reversed().mapNotNull { w ->
            val sets = w.entries.filter { it.exerciseId == exerciseId }.flatMap { it.sets }.filter { !it.warmup }
            if (sets.isEmpty()) null
            else {
                val best = sets.maxBy { e1rm(it) * 1000 + it.reps }
                Session(day(w.startedAt), best, e1rm(best), sets.sumOf { it.weight * it.reps }, sets.sumOf { it.reps })
            }
        }

    data class Record(val exerciseId: String, val best: SetEntry, val e1rm: Double, val date: LocalDate)

    /** Best set ever for every exercise, most recently set records first. */
    fun records(history: List<Workout>): List<Record> {
        val best = mutableMapOf<String, Record>()
        history.reversed().forEach { w ->
            w.entries.forEach { e ->
                e.sets.filter { !it.warmup }.forEach { s ->
                    val score = if (s.weight > 0) e1rm(s) else s.reps.toDouble()
                    val cur = best[e.exerciseId]
                    val curScore = cur?.let { if (it.best.weight > 0) it.e1rm else it.best.reps.toDouble() } ?: -1.0
                    if (score > curScore) best[e.exerciseId] = Record(e.exerciseId, s, e1rm(s), day(w.startedAt))
                }
            }
        }
        return best.values.sortedByDescending { it.date }
    }

    fun sets(n: Int) = if (n == 1) L("1 Satz", "1 set") else L("$n Sätze", "$n sets")

    /** Volume for weighted workouts, total reps for body-weight ones. */
    fun headline(w: Workout): String = if (w.volume > 0) fmtVolume(w.volume) else L("${w.doneSets.sumOf { it.second.reps }} Wdh.", "${w.doneSets.sumOf { it.second.reps }} reps")

    fun fmtKg(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v).let { if (I18n.german) it.replace('.', ',') else it }
    fun fmtVolume(v: Double): String = when {
        v >= 10_000 -> "%.1f t".format(java.util.Locale.ROOT, v / 1000).let { if (I18n.german) it.replace('.', ',') else it }
        else -> "${v.roundToInt()} kg"
    }
    fun fmtDuration(ms: Long): String {
        val m = ms / 60_000
        if (m < 1) return "< 1 min"
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** One set as text for lists: "60×8", "8 reps", plus "D" / "V" and "@8" when set. */
    fun setLabel(s: SetEntry, bodyweight: Boolean): String {
        val base = if (bodyweight) L("${s.reps} Wdh.", "${s.reps} reps") else "${fmtKg(s.weight)}×${s.reps}"
        val type = when (s.type) { SetType.Drop -> " D"; SetType.Failure -> L(" V", " F"); else -> "" }
        val rpe = if (s.rpe > 0) " @${s.rpe}" else ""
        return base + type + rpe
    }
}
