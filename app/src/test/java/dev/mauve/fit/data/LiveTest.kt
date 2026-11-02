package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTest {
    private fun workout(vararg done: Boolean) = Workout(
        name = "Push", startedAt = 0,
        entries = listOf(
            ExerciseEntry("bench", sets = listOf(SetEntry(40.0, 10, warmup = true, done = done[0])) +
                List(3) { i -> SetEntry(80.0 + i * 2.5, 8, done = done[i + 1]) }),
            ExerciseEntry("dips", sets = List(2) { SetEntry(0.0, 12) }),
        ),
    )
    private val titles = mapOf("bench" to "Bench Press", "dips" to "Dips")
    private fun status(w: Workout, now: Long = 1000) =
        Live.status(AppData(active = w), { titles[it] }, { it == "dips" }, now)!!

    @Test fun tickedFindsTheNewlyDoneSetAndNumbersWorkingSets() {
        val l = Live.ticked(workout(true, true, false, false), workout(true, true, true, false), now = 5)!!
        assertEquals(LastSet("bench", 82.5, 8, number = 2, at = 5), l)
        assertEquals(0, Live.ticked(workout(false, false, false, false), workout(true, false, false, false))!!.number)
        assertNull(Live.ticked(workout(true, true, false, false), workout(true, true, false, false)))
    }

    @Test fun restShowsWhatWasDoneAndWhatComesNext() {
        val w = workout(true, true, true, false).copy(
            restEndsAt = 61_000, restTotalSec = 90, lastSet = LastSet("bench", 82.5, 8, number = 2),
        )
        I18n.apply("en")
        val s = status(w)
        assertTrue(s.resting)
        assertEquals("Set 2 done · 82.5 kg × 8", s.last)
        assertEquals("Set 3 of 3 · 85 kg × 8", s.next)
        assertEquals(2, s.done)
        assertEquals(5, s.total)
    }

    @Test fun anOverRestIsNoRestAndOtherExercisesAreNamed() {
        val w = workout(true, true, true, true).copy(restEndsAt = 500, lastSet = LastSet("bench", 85.0, 8, number = 3))
        I18n.apply("en")
        val s = status(w, now = 1000)
        assertFalse(s.resting)
        assertEquals("Dips", s.exercise)
        assertEquals("Set 1 of 2 · 12 reps", s.next)
        assertEquals("Bench Press · Set 3 done · 85 kg × 8", s.last)
    }

    @Test fun clockRoundsUp() {
        assertEquals("1:30", Live.clock(89_001))
        assertEquals("0:00", Live.clock(-5))
    }
}
