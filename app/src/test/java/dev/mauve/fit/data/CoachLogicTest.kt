package dev.mauve.fit.data

import dev.mauve.fit.system.Recovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The training logic that decides weights, order and load; pure functions, run on the JVM. */
class CoachLogicTest {
    private val day = Stats.DAY_MS

    private fun workout(daysAgo: Long, vararg entries: ExerciseEntry) =
        Workout(name = "W", startedAt = System.currentTimeMillis() - daysAgo * day, finishedAt = System.currentTimeMillis() - daysAgo * day + 3_600_000, entries = entries.toList())

    private fun done(weight: Double, reps: Int, target: Int = reps) = SetEntry(weight, reps, done = true, target = target)

    // --- Stats -------------------------------------------------------------------------------

    @Test fun e1rmFollowsEpley() {
        assertEquals(100 * (1 + 5 / 30.0), Stats.e1rm(SetEntry(100.0, 5)), 1e-9)
        assertEquals(0.0, Stats.e1rm(SetEntry(100.0, 0)), 1e-9)
    }

    @Test fun weightGoesUpOnlyWhenEveryRepWasHit() {
        assertEquals(62.5, Stats.suggestWeight(listOf(done(60.0, 8), done(60.0, 8)), 8, 2.5), 1e-9)
        assertEquals(60.0, Stats.suggestWeight(listOf(done(60.0, 8), done(60.0, 6)), 8, 2.5), 1e-9)
        assertEquals(0.0, Stats.suggestWeight(emptyList(), 8, 2.5), 1e-9)
    }

    @Test fun roundToSnapsToTheStep() {
        assertEquals(42.5, Stats.roundTo(43.4, 2.5), 1e-9)
        assertEquals(45.0, Stats.roundTo(43.8, 2.5), 1e-9)
    }

    @Test fun streakIsZeroWithoutHistory() {
        assertEquals(0, Stats.weekStreak(emptyList(), 3))
    }

    @Test fun setLabelShowsTypeAndRpe() {
        assertEquals("60×8", Stats.setLabel(SetEntry(60.0, 8), false))
        assertTrue(Stats.setLabel(SetEntry(60.0, 8, type = SetType.Drop, rpe = 9), false).endsWith(" D @9"))
    }

    // --- Supersets ---------------------------------------------------------------------------

    private fun entry(id: String, sets: Int, done: Int = 0, group: String? = null) =
        ExerciseEntry(id, sets = List(sets) { SetEntry(reps = 10, done = it < done) }, superset = group)

    @Test fun supersetPartnerFollowsWithoutRest() {
        val entries = listOf(entry("a", 3, done = 1, group = "g"), entry("b", 3, group = "g"), entry("c", 3))
        val step = Supersets.afterSet(entries, 0)!!
        assertEquals(1, step.page)
        assertEquals(false, step.rest)
    }

    @Test fun restComesAfterTheRoundAndGoesBackToTheFirst() {
        val entries = listOf(entry("a", 3, done = 1, group = "g"), entry("b", 3, done = 1, group = "g"))
        val step = Supersets.afterSet(entries, 1)!!
        assertEquals(0, step.page)
        assertTrue(step.rest)
    }

    @Test fun plainExerciseHasNoSupersetStep() {
        assertNull(Supersets.afterSet(listOf(entry("a", 3, done = 1)), 0))
    }

    @Test fun watchAlternatesInsideASuperset() {
        // a has one more set done than b, so b is next.
        val entries = listOf(entry("a", 3, done = 2, group = "g"), entry("b", 3, done = 1, group = "g"))
        assertEquals(1, Supersets.current(entries))
        val even = listOf(entry("a", 3, done = 1, group = "g"), entry("b", 3, done = 1, group = "g"))
        assertEquals(0, Supersets.current(even))
    }

    // --- Training blocks ------------------------------------------------------------------------

    private fun withBlock(startedDaysAgo: Long, weeks: Int = 4) =
        AppData(settings = Settings(blockStart = System.currentTimeMillis() - startedDaysAgo * day, blockWeeks = weeks))

    @Test fun blockWalksThroughItsPhases() {
        assertEquals(Coach.Phase.Base, Coach.blockWeek(withBlock(0))!!.phase)
        assertEquals(Coach.Phase.Build, Coach.blockWeek(withBlock(8))!!.phase)
        assertEquals(Coach.Phase.Peak, Coach.blockWeek(withBlock(15))!!.phase)
        assertEquals(Coach.Phase.Deload, Coach.blockWeek(withBlock(22))!!.phase)
        assertNull(Coach.blockWeek(withBlock(29)))
        assertTrue(Coach.blockFinished(withBlock(29)))
    }

    @Test fun sixWeekBlockRepeatsBaseAndBuild() {
        assertEquals(6, Coach.phases(6).size)
        assertEquals(Coach.Phase.Base, Coach.blockWeek(withBlock(8, weeks = 6))!!.phase)
    }

    @Test fun phasesShapeSetsAndReps() {
        val p = PlanExercise("x", sets = 3, reps = 8)
        assertEquals(10, Coach.applyBlock(withBlock(0), p).reps)
        assertEquals(4, Coach.applyBlock(withBlock(8), p).sets)
        assertEquals(6, Coach.applyBlock(withBlock(15), p).reps)
        assertEquals(2, Coach.applyBlock(withBlock(22), p).sets)
        assertEquals(p, Coach.applyBlock(AppData(), p))
    }

    // --- Readiness ----------------------------------------------------------------------------

    @Test fun readinessIsFullWhenEverythingIsNormal() {
        assertEquals(100, Recovery.score(480, 55.0, 55.0, 60.0, 60.0))
    }

    @Test fun shortSleepAndHighPulseLowerReadiness() {
        val score = Recovery.score(300, 62.0, 55.0, 45.0, 60.0)
        assertTrue("score $score", score in 1..60)
    }

    @Test fun eachPenaltyIsCapped() {
        assertEquals(60, Recovery.score(0, null, null, null, null))
        assertTrue(Recovery.score(0, 90.0, 50.0, 10.0, 60.0) >= 0)
    }

    @Test fun readinessLowersLoadOnlyOnPoorDays() {
        assertEquals(1.0, Coach.readinessLoad(null), 1e-9)
        assertEquals(1.0, Coach.readinessLoad(80), 1e-9)
        assertEquals(0.95, Coach.readinessLoad(55), 1e-9)
        assertEquals(0.9, Coach.readinessLoad(30), 1e-9)
    }

    // --- CSV ----------------------------------------------------------------------------------

    @Test fun csvHasOneRowPerDoneSetAndQuotesCommas() {
        val w = workout(1, ExerciseEntry("e1", sets = listOf(done(60.0, 8), done(62.5, 6), SetEntry(65.0, 5))))
            .copy(name = "Push, heavy")
        val csv = CsvExport.sets(AppData(history = listOf(w))) { Exercise(it, "bench press", target = "pectorals") }
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("date,time,workout,exercise"))
        assertTrue(lines[1].contains("\"Push, heavy\",bench press,e1,pectorals,1,normal,60,8"))
        assertTrue(lines[2].contains(",2,normal,62.5,6,"))
    }
}
