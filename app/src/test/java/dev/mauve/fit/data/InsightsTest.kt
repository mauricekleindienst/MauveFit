package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The numbers behind the Insights screen; pure functions with a fixed "today". */
class InsightsTest {
    // A Wednesday, so the running week is half over.
    private val today = LocalDate.of(2026, 10, 28)

    private fun at(date: LocalDate, hour: Int = 18) = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun set(weight: Double = 50.0, reps: Int = 10, warmup: Boolean = false, type: SetType = SetType.Normal, rpe: Int = 0) =
        SetEntry(weight, reps, done = true, warmup = warmup, type = type, rpe = rpe)

    private fun workout(date: LocalDate, vararg entries: ExerciseEntry) =
        Workout(name = "W", startedAt = at(date), finishedAt = at(date) + 3_600_000, entries = entries.toList())

    private fun entry(id: String, vararg sets: SetEntry) = ExerciseEntry(id, sets = sets.toList())

    private val exercises = mapOf(
        "bench" to Exercise("bench", "bench press", target = "pectorals", secondary = listOf("triceps", "shoulders")),
        "row" to Exercise("row", "row", target = "upper back", secondary = listOf("biceps", "lats")),
        "curl" to Exercise("curl", "curl", target = "biceps"),
    )
    private val lookup = Insights.Lookup { exercises[it] }

    // --- Heatmap ------------------------------------------------------------------------------

    @Test fun levelSplitsVolumeIntoQuarters() {
        assertEquals(0, Insights.level(0.0, 1000.0, trained = false))
        assertEquals(1, Insights.level(0.0, 1000.0, trained = true))
        assertEquals(1, Insights.level(250.0, 1000.0, trained = true))
        assertEquals(2, Insights.level(251.0, 1000.0, trained = true))
        assertEquals(3, Insights.level(700.0, 1000.0, trained = true))
        assertEquals(4, Insights.level(1000.0, 1000.0, trained = true))
    }

    @Test fun calendarCoversWholeWeeksAndMarksTheFuture() {
        val h = listOf(
            workout(today, entry("bench", set(100.0, 10))),
            workout(today.minusDays(1), entry("bench", set(50.0, 10))),
            workout(today.minusWeeks(20), entry("bench", set(500.0, 10))),
        )
        val days = Insights.calendar(h, today)
        assertEquals(16 * 7, days.size)
        assertEquals(java.time.DayOfWeek.MONDAY, days.first().date.dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, days.last().date.dayOfWeek)
        // Older workouts outside the window do not set the scale.
        assertEquals(4, days.first { it.date == today }.level)
        assertEquals(2, days.first { it.date == today.minusDays(1) }.level)
        assertEquals(0, days.first { it.date == today.minusDays(2) }.level)
        assertEquals(-1, days.first { it.date == today.plusDays(1) }.level)
    }

    // --- Sets per muscle group ----------------------------------------------------------------

    @Test fun groupSetsCountSecondaryAsHalfAndSkipWarmups() {
        val w = workout(
            today,
            entry("bench", set(warmup = true), set(), set(), set()),
            entry("row", set(), set()),
        )
        val sets = Insights.groupSets(listOf(w), lookup)
        assertEquals(3.0, sets[Insights.MuscleGroup.Chest]!!, 1e-9)
        assertEquals(1.5, sets[Insights.MuscleGroup.Triceps]!!, 1e-9)
        assertEquals(1.5, sets[Insights.MuscleGroup.Shoulders]!!, 1e-9)
        // Row: upper back is the target; lats are back too, so they add nothing on top.
        assertEquals(2.0, sets[Insights.MuscleGroup.Back]!!, 1e-9)
        assertEquals(1.0, sets[Insights.MuscleGroup.Biceps]!!, 1e-9)
    }

    @Test fun unfinishedSetsAndUnknownExercisesDoNotCount() {
        val w = workout(today, entry("bench", SetEntry(50.0, 10, done = false), set()), entry("mystery", set(), set()))
        val sets = Insights.groupSets(listOf(w), lookup)
        assertEquals(1.0, sets[Insights.MuscleGroup.Chest]!!, 1e-9)
        assertEquals(setOf(Insights.MuscleGroup.Chest, Insights.MuscleGroup.Triceps, Insights.MuscleGroup.Shoulders), sets.keys)
    }

    @Test fun weeklyGroupSetsLineUpByCalendarWeek() {
        val h = listOf(
            workout(today, entry("curl", set(), set())),
            workout(today.minusWeeks(2), entry("curl", set())),
        )
        val biceps = Insights.weeklyGroupSets(h, lookup, today)[Insights.MuscleGroup.Biceps]!!
        assertEquals(12, biceps.size)
        assertEquals(2.0, biceps.last(), 1e-9)
        assertEquals(1.0, biceps[9], 1e-9)
        assertEquals(0.0, biceps[10], 1e-9)
    }

    @Test fun bandsFollowTheTenToTwentyRange() {
        assertEquals(Insights.Band.Under, Insights.band(9.5))
        assertEquals(Insights.Band.Good, Insights.band(10.0))
        assertEquals(Insights.Band.Good, Insights.band(20.0))
        assertEquals(Insights.Band.High, Insights.band(20.5))
    }

    // --- Strength -----------------------------------------------------------------------------

    private fun p(daysAgo: Long, e1rm: Double) = Insights.Point(today.minusDays(daysAgo), e1rm)

    @Test fun plateauNeedsFourWeeksAndThreeSessionsWithoutANewHigh() {
        val stuck = listOf(p(60, 90.0), p(40, 100.0), p(30, 98.0), p(20, 99.0), p(10, 100.0))
        assertTrue(Insights.plateau(stuck, today))
        // Only two sessions since the high: too few to call it.
        assertFalse(Insights.plateau(listOf(p(60, 90.0), p(40, 100.0), p(30, 98.0), p(10, 99.0)), today))
        // A new high three weeks ago: still moving.
        assertFalse(Insights.plateau(listOf(p(60, 90.0), p(50, 95.0), p(40, 94.0), p(30, 96.0), p(21, 101.0), p(7, 100.0)), today))
        assertFalse(Insights.plateau(emptyList(), today))
    }

    @Test fun percentChangeIsSignedAndNeedsABase() {
        assertEquals(18.0, Insights.pctChange(100.0, 118.0)!!, 1e-9)
        assertEquals(-25.0, Insights.pctChange(80.0, 60.0)!!, 1e-9)
        assertNull(Insights.pctChange(0.0, 50.0))
    }

    @Test fun liftChangeComparesWithTheLastSessionBeforeTheWindow() {
        val points = listOf(p(70, 80.0), p(60, 100.0), p(20, 105.0), p(5, 110.0), p(2, 108.0))
        assertEquals(10.0, Insights.liftChange(points, today)!!, 1e-9)
        // Nothing trained inside the window: no change to report.
        assertNull(Insights.liftChange(listOf(p(90, 80.0), p(70, 90.0)), today))
    }

    @Test fun volumeChangeComparesFourWeekBlocks() {
        val h = listOf(
            workout(today.minusDays(3), entry("bench", set(100.0, 10))),
            workout(today.minusDays(40), entry("bench", set(100.0, 8))),
        )
        assertEquals(25.0, Insights.volumeChange(h, today)!!, 1e-9)
        assertNull(Insights.volumeChange(h.take(1), today))
    }

    @Test fun recordsTimelineSkipsTheFirstSessionAndListsNewestFirst() {
        val h = listOf(
            workout(today.minusDays(1), entry("bench", set(110.0, 5))),
            workout(today.minusDays(5), entry("bench", set(100.0, 5))),
            workout(today.minusDays(9), entry("bench", set(105.0, 5))),
            workout(today.minusDays(12), entry("bench", set(100.0, 5), set(150.0, 5, warmup = true))),
        )
        val prs = Insights.prTimeline(h)
        assertEquals(listOf(today.minusDays(1), today.minusDays(9)), prs.map { it.date })
        assertEquals(5.0, prs.last().gain!!, 1e-9)
    }

    // --- Consistency --------------------------------------------------------------------------

    @Test fun goalRateCountsTheRunningWeekOnlyOnceMet() {
        val mon = Stats.weekStart(today)
        val h = listOf(
            workout(mon), // this week: 1 of 2 so far, still open
            workout(mon.minusWeeks(1)), workout(mon.minusWeeks(1).plusDays(2)),
            workout(mon.minusWeeks(2)),
            workout(mon.minusWeeks(3)), workout(mon.minusWeeks(3).plusDays(1)),
        )
        assertEquals(Insights.Rate(2, 3), Insights.goalRate(h, 2, today))
        assertEquals(67, Insights.goalRate(h, 2, today).pct)
        val met = h + workout(mon.plusDays(1))
        assertEquals(Insights.Rate(3, 4), Insights.goalRate(met, 2, today))
        assertEquals(Insights.Rate(0, 0), Insights.goalRate(emptyList(), 2, today))
    }

    @Test fun streaksKeepTheBestRunAndSurviveAnOpenWeek() {
        val mon = Stats.weekStart(today)
        val h = listOf(1L, 2L, 5L, 6L, 7L).map { workout(mon.minusWeeks(it)) }
        assertEquals(Insights.Streaks(current = 2, best = 3), Insights.streaks(h, 1, today))
        // A frozen week bridges the gap.
        val frozen = setOf(Stats.weekKey(mon.minusWeeks(3)), Stats.weekKey(mon.minusWeeks(4)))
        assertEquals(Insights.Streaks(current = 5, best = 5), Insights.streaks(h, 1, today, frozen))
    }

    // --- Body and effort ----------------------------------------------------------------------

    @Test fun movingAverageUsesTheLastSevenDays() {
        val d = Stats.DAY_MS
        val avg = Insights.movingAverage(listOf(0L to 80.0, d to 82.0, 10 * d to 90.0))
        assertEquals(listOf(80.0, 81.0, 90.0), avg.map { it.second })
    }

    @Test fun effortSharesComeFromWorkingSets() {
        val w = workout(
            today,
            entry("bench", set(warmup = true, rpe = 5), set(rpe = 8), set(rpe = 9, type = SetType.Failure), set(type = SetType.Drop), set()),
        )
        val e = Insights.effort(listOf(w), today)
        assertEquals(4, e.sets)
        assertEquals(2, e.rated)
        assertEquals(8.5, e.avgRpe!!, 1e-9)
        assertEquals(1, e.failure)
        assertEquals(1, e.drop)
    }
}
