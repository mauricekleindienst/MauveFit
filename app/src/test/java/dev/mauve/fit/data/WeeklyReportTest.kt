package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyReportTest {
    private val monday: LocalDate = LocalDate.of(2026, 10, 19)

    private fun at(day: LocalDate) = day.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun workout(day: LocalDate, weight: Double, reps: Int = 8, sets: Int = 3) = Workout(
        name = "W", startedAt = at(day), finishedAt = at(day) + 3_600_000,
        entries = listOf(ExerciseEntry("bench", sets = List(sets) { SetEntry(weight, reps, done = true) })),
    )

    private val repo: (String) -> Exercise? = { Exercise(it, it, target = "pectorals") }

    @Test fun countsTheWeekAndComparesVolume() {
        val d = AppData(
            history = listOf(workout(monday.plusDays(2), 60.0), workout(monday, 50.0), workout(monday.minusDays(5), 50.0)),
            settings = Settings(weeklyGoal = 2),
        )
        val r = WeeklyReport.of(d, monday, repo)
        assertEquals(2, r.workouts)
        assertTrue(r.goalHit)
        assertEquals(6, r.sets)
        // (50+60)*24 against 50*24 is +120 %.
        assertEquals(120, r.volumeChange)
        assertEquals("pectorals", r.topMuscle)
    }

    @Test fun aHeavierSetThanEverBeforeIsARecord() {
        val d = AppData(history = listOf(workout(monday.plusDays(1), 70.0), workout(monday.minusDays(3), 60.0)))
        assertEquals(1, WeeklyReport.of(d, monday, repo).records)
    }

    @Test fun firstEverSessionIsNotARecordAndEmptyWeekHasNoChange() {
        val d = AppData(history = listOf(workout(monday.plusDays(1), 70.0)))
        val r = WeeklyReport.of(d, monday, repo)
        assertEquals(0, r.records)
        assertNull(r.volumeChange)
        assertEquals(0, WeeklyReport.of(d, monday.plusWeeks(1), repo).workouts)
    }
}
