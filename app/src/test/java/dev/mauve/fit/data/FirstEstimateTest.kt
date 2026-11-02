package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstEstimateTest {
    private fun ex(name: String, equipment: String) = Exercise("x", name, equipment = equipment)

    @Test fun benchForAnIntermediateIsACarefulStart() {
        // 82 kg, intermediate: max about 61 kg, 8 reps about 49 kg, start at 80 % of that.
        val w = Coach.firstEstimate(ex("barbell bench press", "barbell"), 82.0, Level.Intermediate, 8)!!
        assertEquals(38.8, w, 0.5)
    }

    @Test fun dumbbellsArePerHandAndLevelsScale() {
        val beg = Coach.firstEstimate(ex("dumbbell bench press", "dumbbell"), 80.0, Level.Beginner, 10)!!
        val adv = Coach.firstEstimate(ex("dumbbell bench press", "dumbbell"), 80.0, Level.Advanced, 10)!!
        assertTrue(beg in 5.0..15.0)
        assertTrue(adv > beg * 1.8)
    }

    @Test fun barbellNeverGoesBelowTheBar() {
        assertEquals(20.0, Coach.firstEstimate(ex("barbell curl", "barbell"), 55.0, Level.Beginner, 12)!!, 0.01)
    }

    @Test fun noEstimateForMachinesUnknownLiftsOrMissingWeight() {
        assertNull(Coach.firstEstimate(ex("lever leg press", "leverage machine"), 80.0, Level.Intermediate, 10))
        assertNull(Coach.firstEstimate(ex("barbell lying triceps extension", "barbell"), 80.0, Level.Intermediate, 10))
        assertNull(Coach.firstEstimate(ex("barbell bench press", "barbell"), null, Level.Intermediate, 10))
    }
}
