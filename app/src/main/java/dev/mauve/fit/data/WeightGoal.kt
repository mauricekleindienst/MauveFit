package dev.mauve.fit.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Progress towards a target body weight: the pace from the last four weeks (least squares, so one
 * salty dinner does not swing it) and when the goal is reached at that pace.
 */
object WeightGoal {
    data class Outlook(
        val latest: Double,
        val remaining: Double,
        /** kg per week, negative when losing; null until there is enough data. */
        val perWeek: Double?,
        /** Date the goal is reached at the current pace; null when the pace points the other way or is flat. */
        val eta: LocalDate?,
        val reached: Boolean,
        /** True when the pace is faster than about 1 % of body weight per week (losing) or 0.5 % (gaining). */
        val tooFast: Boolean,
        /** 0..1 from the first logged weight to the goal. */
        val progress: Float,
    )

    /** [entries] are (time, kg) newest first, as stored. */
    fun outlook(entries: List<Pair<Long, Double>>, goal: Double, now: Long = System.currentTimeMillis()): Outlook? {
        if (goal <= 0 || entries.isEmpty()) return null
        val latest = entries.first().second
        val start = entries.last().second
        val remaining = goal - latest
        val reached = abs(remaining) < 0.25 || (start > goal && latest <= goal) || (start < goal && latest >= goal)
        val pace = pace(entries, now)
        val towards = pace != null && abs(pace) >= 0.05 && (pace < 0) == (remaining < 0)
        val eta = if (!reached && towards) LocalDate.now().plusDays(ceil(abs(remaining / pace!!) * 7).toLong()) else null
        val limit = if (remaining < 0) 0.01 * latest else 0.005 * latest
        val total = goal - start
        val progress = if (abs(total) < 1e-6) 1f else ((latest - start) / total).toFloat().coerceIn(0f, 1f)
        return Outlook(latest, remaining, pace, eta, reached, pace != null && towards && abs(pace) > limit, if (reached) 1f else progress)
    }

    /** Slope of a least-squares line over the last 28 days, in kg per week; needs 3 entries over 7+ days. */
    fun pace(entries: List<Pair<Long, Double>>, now: Long = System.currentTimeMillis()): Double? {
        val recent = entries.filter { now - it.first <= 28 * Stats.DAY_MS }
        if (recent.size < 3) return null
        val days = recent.map { (it.first - recent.last().first) / Stats.DAY_MS.toDouble() }
        if (days.max() - days.min() < 7) return null
        val ys = recent.map { it.second }
        val mx = days.average()
        val my = ys.average()
        val sxx = days.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        val sxy = days.indices.sumOf { (days[it] - mx) * (ys[it] - my) }
        return sxy / sxx * 7
    }
}
