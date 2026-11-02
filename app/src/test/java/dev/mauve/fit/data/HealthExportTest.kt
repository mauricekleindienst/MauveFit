package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthExportTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun workout(id: String, daysAgo: Long, note: String = "") = Workout(
        id = id, name = "Push", startedAt = now - daysAgo * day, finishedAt = now - daysAgo * day + 3_600_000, note = note,
        entries = listOf(ExerciseEntry("bench", sets = listOf(SetEntry(60.0, 8, done = true)))),
    )

    @Test fun firstRunWritesEverythingInsideTheWindow() {
        val d = AppData(history = listOf(workout("a", 1), workout("old", 120)), bodyWeights = listOf(BodyWeight(now - day, 80.0)))
        val p = HealthExport.plan(d, emptyMap(), now)
        assertEquals(listOf("a"), p.workouts.map { it.id })
        assertEquals(1, p.weights.size)
        assertTrue(p.delete.isEmpty())
    }

    @Test fun secondRunWritesOnlyWhatChanged() {
        val d = AppData(history = listOf(workout("a", 1), workout("b", 2)))
        val first = HealthExport.plan(d, emptyMap(), now)
        assertTrue(HealthExport.plan(d, first.after, now).empty)
        val edited = d.copy(history = listOf(workout("a", 1, note = "heavy"), workout("b", 2)))
        assertEquals(listOf("a"), HealthExport.plan(edited, first.after, now).workouts.map { it.id })
    }

    @Test fun deletedInTheAppIsDeletedThereButOldEntriesJustFallOut() {
        val d = AppData(history = listOf(workout("a", 1), workout("b", 89)))
        val synced = HealthExport.plan(d, emptyMap(), now).after
        // "a" deleted in the app; "b" is now 91 days old and only leaves the window.
        val later = now + 2 * day
        val p = HealthExport.plan(AppData(history = listOf(workout("b", 89))), synced, later)
        assertEquals(setOf(HealthExport.workoutId(workout("a", 1))), p.delete)
    }

    @Test fun unfinishedWorkoutsAreNotWritten() {
        val open = workout("a", 0).copy(finishedAt = null)
        assertTrue(HealthExport.plan(AppData(history = listOf(open)), emptyMap(), now).empty)
    }
}
