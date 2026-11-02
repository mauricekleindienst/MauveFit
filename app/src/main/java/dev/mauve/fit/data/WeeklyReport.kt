package dev.mauve.fit.data

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Last week in numbers, for the Monday notification and the card on Home: what was done, how it
 * compares with the week before, and one line that sums it up.
 */
object WeeklyReport {
    data class Report(
        val weekStart: LocalDate,
        val workouts: Int,
        val goal: Int,
        val sets: Int,
        val volume: Double,
        /** Working reps done, for weeks of body-weight training without volume. */
        val reps: Int = 0,
        /** Change against the week before in percent; null when that week had no volume. */
        val volumeChange: Int?,
        val records: Int,
        val cardioMinutes: Int,
        val topMuscle: String?,
    ) {
        val goalHit get() = workouts >= goal

        /** One plain sentence: the most notable thing about the week. */
        val headline: String get() = when {
            workouts == 0 -> L("Letzte Woche ohne Training. Diese Woche zählt jede Einheit.", "No workouts last week. Every session counts this week.")
            records > 0 -> L("$records ${if (records == 1) "neuer Rekord" else "neue Rekorde"} und $workouts von $goal Trainings.", "$records new ${if (records == 1) "record" else "records"} and $workouts of $goal workouts.")
            goalHit && (volumeChange ?: 0) > 0 -> L("Wochenziel geschafft, $volumeChange % mehr Volumen als davor.", "Weekly goal hit, $volumeChange % more volume than before.")
            goalHit -> L("Wochenziel geschafft: $workouts von $goal Trainings.", "Weekly goal hit: $workouts of $goal workouts.")
            else -> L("$workouts von $goal Trainings. Diese Woche holst du den Rest.", "$workouts of $goal workouts. This week you get the rest.")
        }
    }

    /** Report for the week starting [weekStart] (a Monday). */
    fun of(d: AppData, weekStart: LocalDate, repo: (String) -> Exercise?): Report {
        fun inWeek(start: LocalDate, at: Long) = Stats.day(at).let { !it.isBefore(start) && it.isBefore(start.plusWeeks(1)) }
        val week = d.history.filter { inWeek(weekStart, it.startedAt) }
        val before = d.history.filter { inWeek(weekStart.minusWeeks(1), it.startedAt) }
        val volume = week.sumOf { it.volume }
        val previous = before.sumOf { it.volume }
        // A record: a set beating the best e1RM of that exercise from everything before this week.
        val earlier = d.history.filter { Stats.day(it.startedAt).isBefore(weekStart) }
        val records = week.flatMap { it.entries }.groupBy { it.exerciseId }.count { (id, entries) ->
            // Scored like every other record list: weighted beats unweighted, reps count without weight.
            val best = entries.flatMap { it.sets }.filter { it.done && !it.warmup }.maxOfOrNull { Stats.recordScore(it) } ?: return@count false
            val old = earlier.flatMap { w -> w.entries.filter { it.exerciseId == id }.flatMap { it.sets } }
                .filter { it.done && !it.warmup }.maxOfOrNull { Stats.recordScore(it) } ?: return@count false
            best > old + 0.01
        }
        val muscles = week.flatMap { it.entries }.groupingBy { repo(it.exerciseId)?.target ?: "" }
            .fold(0) { n, e -> n + e.sets.count { it.done && !it.warmup } }.filterKeys { it.isNotBlank() }
        return Report(
            weekStart = weekStart,
            workouts = week.size,
            goal = d.settings.weeklyGoal,
            sets = week.sumOf { it.doneSets.size },
            volume = volume,
            reps = week.sumOf { w -> w.doneSets.sumOf { it.second.reps } },
            // Against reps (a body-weight week) a volume change in percent means nothing.
            volumeChange = if (previous > 0 && volume > 0) ((volume / previous - 1) * 100).roundToInt() else null,
            records = records,
            cardioMinutes = d.cardio.filter { inWeek(weekStart, it.at) }.sumOf { it.minutes },
            topMuscle = muscles.maxByOrNull { it.value }?.key,
        )
    }

    /** The week before the current one. */
    fun lastWeekStart(today: LocalDate = LocalDate.now()) = Stats.weekStart(today).minusWeeks(1)
}
