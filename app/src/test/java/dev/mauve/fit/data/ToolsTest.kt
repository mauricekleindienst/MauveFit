package dev.mauve.fit.data

import dev.mauve.fit.data.Intervals.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

/** Interval timer phases, the 1RM table and the plate maths behind the tools screen. */
class ToolsTest {
    // --- Intervals -----------------------------------------------------------------------------

    @Test fun tabataRunsEightRoundsWithoutTrailingRest() {
        val plan = Intervals.tabata
        assertEquals(8 * 20_000L + 7 * 10_000L, plan.totalMs)
        assertEquals(Intervals.Tick(Phase.Work, 1, 20_000, 20_000), Intervals.at(plan, 0))
        assertEquals(Intervals.Tick(Phase.Rest, 1, 10_000, 10_000), Intervals.at(plan, 20_000))
        assertEquals(Intervals.Tick(Phase.Work, 2, 15_000, 20_000), Intervals.at(plan, 35_000))
        assertEquals(Phase.Work, Intervals.at(plan, plan.totalMs - 1).phase)
        assertEquals(8, Intervals.at(plan, plan.totalMs - 1).round)
        assertEquals(Phase.Done, Intervals.at(plan, plan.totalMs).phase)
    }

    @Test fun emomIsWorkOnlyEveryMinute() {
        val plan = Intervals.emom(10)
        assertEquals(600_000L, plan.totalMs)
        val t = Intervals.at(plan, 61_500)
        assertEquals(Phase.Work, t.phase)
        assertEquals(2, t.round)
        assertEquals(59, Intervals.secondsLeft(t))
    }

    @Test fun customPlanAndEdgeCases() {
        val plan = Intervals.Plan(40, 20, 3)
        assertEquals(Intervals.Tick(Phase.Rest, 2, 5_000, 20_000), Intervals.at(plan, 60_000 + 55_000))
        assertEquals(Phase.Work, Intervals.at(plan, -100).phase)
        assertEquals(Phase.Done, Intervals.at(Intervals.Plan(0, 10, 3), 0).phase)
        assertEquals(Phase.Done, Intervals.at(Intervals.Plan(30, 10, 0), 0).phase)
    }

    @Test fun secondsRoundUpAndFormat() {
        assertEquals(1, Intervals.secondsLeft(Intervals.Tick(Phase.Work, 1, 1, 20_000)))
        assertEquals(20, Intervals.secondsLeft(Intervals.Tick(Phase.Work, 1, 20_000, 20_000)))
        assertEquals("1:00", Intervals.fmt(60))
        assertEquals("0:05", Intervals.fmt(5))
    }

    // --- 1RM -----------------------------------------------------------------------------------

    @Test fun oneRmMatchesStats() {
        assertEquals(Stats.e1rm(SetEntry(100.0, 5)), Strength.oneRm(100.0, 5), 1e-9)
    }

    @Test fun percentTableRoundsToStep() {
        val rows = Strength.table(100.0, 2.5)
        assertEquals((95 downTo 50 step 5).toList(), rows.map { it.percent })
        assertEquals(95.0, rows.first().weight, 1e-9)
        assertEquals(50.0, rows.last().weight, 1e-9)
        assertEquals(Strength.Row(85, 85.0, 5), rows[2])
        // Every weight sits on the step.
        Strength.table(123.0, 2.5).forEach { assertEquals(0.0, it.weight % 2.5, 1e-9) }
    }

    @Test fun repsFallAsLoadRises() {
        val reps = (95 downTo 50 step 5).map(Strength::repsAt)
        assertEquals(2, reps.first())
        assertEquals(10, Strength.repsAt(75))
        assertEquals(30, reps.last())
        assertEquals(reps.sorted(), reps)
    }

    // --- Plates --------------------------------------------------------------------------------

    @Test fun platesPerSide() {
        assertEquals(listOf(25.0, 10.0, 2.5), Coach.plates(95.0, 20.0))
        assertEquals(emptyList<Double>(), Coach.plates(20.0, 20.0))
        assertEquals(null, Coach.plates(15.0, 20.0))
        // Rounds to the nearest load the plates allow, not always down.
        assertEquals(listOf(25.0, 15.0, 1.25), Coach.plates(102.0, 20.0))
        assertEquals(listOf(25.0, 15.0), Coach.plates(100.6, 20.0))
    }
}
