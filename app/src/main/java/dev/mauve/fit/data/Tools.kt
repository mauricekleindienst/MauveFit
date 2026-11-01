package dev.mauve.fit.data

import kotlin.math.roundToInt

/**
 * Interval timer maths. The screen keeps only a start time and the time banked before a pause;
 * everything shown is derived from the elapsed milliseconds, so the countdown never drifts.
 */
object Intervals {
    enum class Phase { Work, Rest, Done }

    /** [rounds] rounds of [workSec] work, each but the last followed by [restSec] rest. */
    data class Plan(val workSec: Int, val restSec: Int, val rounds: Int) {
        val totalMs: Long get() = rounds * workSec * 1000L + (rounds - 1).coerceAtLeast(0) * restSec * 1000L
    }

    /** Where the timer stands: the phase, its 1-based round, what is left of it and its length. */
    data class Tick(val phase: Phase, val round: Int, val remainingMs: Long, val phaseMs: Long)

    /** Every minute on the minute: the work and the rest after it share one minute. */
    fun emom(rounds: Int) = Plan(60, 0, rounds)

    /** 20 s on, 10 s off, eight times. */
    val tabata = Plan(20, 10, 8)

    fun at(plan: Plan, elapsedMs: Long): Tick {
        val work = plan.workSec * 1000L
        val rest = plan.restSec.coerceAtLeast(0) * 1000L
        if (plan.rounds <= 0 || work <= 0 || elapsedMs >= plan.totalMs) return Tick(Phase.Done, plan.rounds.coerceAtLeast(0), 0, 0)
        val e = elapsedMs.coerceAtLeast(0)
        val cycle = work + rest
        val index = (e / cycle).toInt()
        val inCycle = e - index * cycle
        return if (inCycle < work) Tick(Phase.Work, index + 1, work - inCycle, work)
        else Tick(Phase.Rest, index + 1, cycle - inCycle, rest)
    }

    /** Whole seconds left, rounded up, so "0:01" shows until the phase really ends. */
    fun secondsLeft(t: Tick): Int = ((t.remainingMs + 999) / 1000).toInt()

    fun fmt(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}

/** One-rep max estimate and the loads that follow from it. */
object Strength {
    data class Row(val percent: Int, val weight: Double, val reps: Int)

    /** Same Epley estimate as the progress charts. */
    fun oneRm(weight: Double, reps: Int): Double = Stats.e1rm(SetEntry(weight = weight, reps = reps))

    /** Reps that fit a share of the 1RM: Epley turned round, at least one. */
    fun repsAt(percent: Int): Int = (30.0 * (100.0 / percent - 1)).roundToInt().coerceAtLeast(1)

    /** 95 % down to 50 % in fives, each rounded to the weight step. */
    fun table(oneRm: Double, step: Double): List<Row> =
        (95 downTo 50 step 5).map { p -> Row(p, Stats.roundTo(oneRm * p / 100, step), repsAt(p)) }
}
