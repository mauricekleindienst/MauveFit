package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A lighter start (light week, block phase, poor recovery) must not carry into the next session. */
class LoadCarryTest {
    private fun session(weight: Double, load: Double) = Workout(
        name = "W", finishedAt = 1L,
        entries = listOf(ExerciseEntry("bench", sets = List(3) { SetEntry(weight, 8, target = 8, done = true) }, load = load)),
    )

    @Test fun lightWeekIsScaledBackUp() {
        // 100 kg plus a step, at 70 %: 72.5 kg lifted in the light week.
        val d = AppData(history = listOf(session(72.5, 0.7)))
        val full = Coach.lastFullSets(d, "bench")
        assertEquals(102.5, full.first().weight, 0.01)
        // All reps hit, so the next normal session goes one step past the full weight.
        assertEquals(105.0, Stats.suggestWeight(full, 8, 2.5), 0.01)
    }

    @Test fun normalSessionIsUnchanged() {
        val d = AppData(history = listOf(session(100.0, 1.0)))
        assertEquals(100.0, Coach.lastFullSets(d, "bench").first().weight, 0.01)
    }

    @Test fun poorRecoveryDayDoesNotStick() {
        val d = AppData(history = listOf(session(90.0, 0.9)))
        assertEquals(100.0, Coach.lastFullSets(d, "bench").first().weight, 0.01)
    }

    @Test fun oldEntriesWithoutLoadReadAsFull() {
        val d = AppData(history = listOf(session(80.0, 1.0).let { w -> w.copy(entries = w.entries.map { it.copy(load = 1.0) }) }))
        assertEquals(80.0, Coach.lastFullSets(d, "bench").first().weight, 0.01)
    }
}
