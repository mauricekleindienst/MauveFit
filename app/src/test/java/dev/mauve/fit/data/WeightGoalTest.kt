package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightGoalTest {
    private val now = 1_800_000_000_000L
    private val day = Stats.DAY_MS

    /** One entry every 7 days, losing [perWeek] kg, newest first like the store keeps them. */
    private fun series(start: Double, perWeek: Double, weeks: Int) =
        (0..weeks).map { w -> (now - (weeks - w) * 7 * day) to start + perWeek * w }.reversed()

    @Test fun paceIsTheWeeklySlope() {
        assertEquals(-0.5, WeightGoal.pace(series(80.0, -0.5, 3), now)!!, 1e-6)
    }

    @Test fun paceNeedsEnoughData() {
        assertNull(WeightGoal.pace(listOf(now to 80.0, now - day to 80.5), now))
    }

    @Test fun losingTowardsGoalGivesAnEta() {
        val o = WeightGoal.outlook(series(84.0, -0.5, 3), 78.0, now)!!
        assertEquals(-4.5, o.remaining, 1e-6)
        assertNotNull(o.eta)
        assertFalse(o.reached)
        assertFalse(o.tooFast)
    }

    @Test fun wrongDirectionHasNoEta() {
        val o = WeightGoal.outlook(series(80.0, 0.5, 3), 75.0, now)!!
        assertNull(o.eta)
    }

    @Test fun crashDietIsFlagged() {
        val o = WeightGoal.outlook(series(90.0, -1.5, 3), 75.0, now)!!
        assertTrue(o.tooFast)
    }

    @Test fun reachedWhenPastTheGoal() {
        val o = WeightGoal.outlook(series(80.0, -1.0, 3), 78.0, now)!!
        assertTrue(o.reached)
        assertEquals(1f, o.progress, 1e-6f)
    }

    @Test fun noGoalNoOutlook() {
        assertNull(WeightGoal.outlook(series(80.0, -0.5, 3), 0.0, now))
    }
}
