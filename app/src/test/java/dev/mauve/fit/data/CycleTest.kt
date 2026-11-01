package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Cycle phase arithmetic: pure, no Android. */
class CycleTest {
    private val start = LocalDate.of(2026, 10, 1)

    @Test fun phasesOfATypicalCycle() {
        // 28 days: ovulation around day 14, window 13 to 15.
        assertEquals(Cycle.Phase.Menstrual, Cycle.phase(1, 28))
        assertEquals(Cycle.Phase.Menstrual, Cycle.phase(5, 28))
        assertEquals(Cycle.Phase.Follicular, Cycle.phase(6, 28))
        assertEquals(Cycle.Phase.Follicular, Cycle.phase(12, 28))
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(13, 28))
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(14, 28))
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(15, 28))
        assertEquals(Cycle.Phase.Luteal, Cycle.phase(16, 28))
        assertEquals(Cycle.Phase.Luteal, Cycle.phase(28, 28))
    }

    @Test fun shortAndLongCyclesShiftOvulation() {
        // 21 days: ovulation day 7, no follicular days left after the period.
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(6, 21))
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(8, 21))
        assertEquals(Cycle.Phase.Luteal, Cycle.phase(9, 21))
        // 35 days: ovulation day 21.
        assertEquals(Cycle.Phase.Follicular, Cycle.phase(19, 35))
        assertEquals(Cycle.Phase.Ovulation, Cycle.phase(21, 35))
        assertEquals(Cycle.Phase.Luteal, Cycle.phase(23, 35))
    }

    @Test fun statusCountsDaysFromTheStart() {
        val s = Cycle.status(start, 28, start)!!
        assertEquals(1, s.day)
        assertEquals(Cycle.Phase.Menstrual, s.phase)
        assertFalse(s.stale)
        assertFalse(s.projected)
        assertEquals(14, Cycle.status(start, 28, start.plusDays(13))!!.day)
    }

    @Test fun anOverdueStartIsProjectedThenStale() {
        val projected = Cycle.status(start, 28, start.plusDays(30))!!
        assertEquals(3, projected.day)
        assertTrue(projected.projected)
        assertFalse(projected.stale)
        assertFalse(Cycle.status(start, 28, start.plusDays(55))!!.stale)
        assertTrue(Cycle.status(start, 28, start.plusDays(56))!!.stale)
    }

    @Test fun missingOrFutureStartGivesNothing() {
        assertNull(Cycle.status(null, 28, start))
        assertNull(Cycle.status(start.plusDays(1), 28, start))
        assertNull(Cycle.status(CycleSettings(on = true, lastStart = ""), start))
        assertNull(Cycle.status(CycleSettings(on = true, lastStart = "not a date"), start))
    }

    @Test fun switchedOffShowsNothingEvenWithAStoredDate() {
        assertNull(Cycle.status(CycleSettings(on = false, lastStart = start.toString()), start))
        assertEquals(5, Cycle.status(CycleSettings(on = true, lastStart = start.toString()), start.plusDays(4))!!.day)
    }

    @Test fun lengthIsClampedTo21To40() {
        assertEquals(21, Cycle.status(start, 10, start)!!.length)
        assertEquals(40, Cycle.status(start, 90, start)!!.length)
    }

    @Test fun usualLengthIsTheMedianOfPlausibleGaps() {
        val starts = listOf(start, start.plusDays(29), start.plusDays(57), start.plusDays(87))
        assertEquals(29, Cycle.lengthFrom(starts.shuffled()))
        // A missed entry (a 58-day gap) is left out.
        assertEquals(28, Cycle.lengthFrom(listOf(start, start.plusDays(28), start.plusDays(86))))
        assertNull(Cycle.lengthFrom(listOf(start)))
        assertNull(Cycle.lengthFrom(emptyList()))
    }
}
