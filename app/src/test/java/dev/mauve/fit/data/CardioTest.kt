package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Hybrid coaching: how cardio feeds into leg recovery, imports and the weekly minutes. */
class CardioTest {
    private val hour = 3_600_000L
    private val now = System.currentTimeMillis()

    /** A session that ended [hoursAgo] hours before now. */
    private fun session(hoursAgo: Long, minutes: Int, kind: CardioKind = CardioKind.Run, rpe: Int? = null, hr: Int? = null) =
        CardioSession(at = now - hoursAgo * hour - minutes * 60_000L, kind = kind, minutes = minutes, rpe = rpe, avgHr = hr)

    private val legs get() = Progress.groups.entries.first { "quads" in it.value }.key
    private val rested get() = Progress.groups.keys.associateWith { 1f }

    @Test fun longRunLoadsTheLegs() {
        assertNotNull(Coach.legLoad(listOf(session(12, 60)), now))
    }

    @Test fun hardShortRideLoadsTheLegs() {
        assertNotNull(Coach.legLoad(listOf(session(12, 25, CardioKind.Bike, rpe = 8)), now))
        // Unrated watch import with a high pulse counts as hard.
        assertNotNull(Coach.legLoad(listOf(session(12, 25, hr = 165)), now))
    }

    @Test fun easyShortRunOrSwimDoesNot() {
        assertNull(Coach.legLoad(listOf(session(12, 30, rpe = 3)), now))
        assertNull(Coach.legLoad(listOf(session(12, 90, CardioKind.Swim, rpe = 8)), now))
    }

    @Test fun olderThanTwoDaysIsForgotten() {
        assertNull(Coach.legLoad(listOf(session(49, 60)), now))
    }

    @Test fun legsAreLoweredOnlyAfterHeavyCardio() {
        val after = Coach.withCardio(rested, listOf(session(0, 60, rpe = 8)), now)
        assertEquals(0.3f, after.getValue(legs), 0.01f)
        rested.keys.filter { it != legs }.forEach { assertEquals(1f, after.getValue(it), 0f) }
        assertEquals(rested, Coach.withCardio(rested, listOf(session(0, 20, rpe = 3)), now))
    }

    @Test fun legsRecoverOverTwoDays() {
        val day1 = Coach.withCardio(rested, listOf(session(24, 50)), now).getValue(legs)
        assertEquals(0.75f, day1, 0.01f)
        // A leg day that already left them lower keeps the lower value.
        val sore = rested + (legs to 0.2f)
        assertEquals(0.2f, Coach.withCardio(sore, listOf(session(24, 50)), now).getValue(legs), 0f)
    }

    @Test fun importSkipsDuplicatesByStartTime() {
        val known = listOf(CardioSession(at = 1_000_000L))
        val incoming = listOf(CardioSession(at = 1_030_000L), CardioSession(at = 5_000_000L), CardioSession(at = 5_010_000L))
        val fresh = Cardio.newSessions(known, incoming)
        assertEquals(listOf(5_000_000L), fresh.map { it.at })
    }

    @Test fun weekMinutesLandOnTheirDay() {
        val today = LocalDate.now()
        val minutes = Cardio.weekMinutes(listOf(CardioSession(at = now, minutes = 40), CardioSession(at = now - 30 * Stats.DAY_MS, minutes = 99)), today)
        assertEquals(7, minutes.size)
        assertEquals(40, minutes[today.dayOfWeek.value - 1])
        assertEquals(40, minutes.sum())
    }

    @Test fun effortMapsBackFromRpe() {
        Cardio.Effort.entries.forEach { assertEquals(it, Cardio.Effort.of(it.rpe)) }
        assertNull(Cardio.Effort.of(null))
        assertTrue(Cardio.isHard(CardioSession(rpe = Cardio.Effort.Hard.rpe)))
    }
}
