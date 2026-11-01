package dev.mauve.fit.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the intro's extra answers do to the generated plan, run on the real exercise list. */
class IntroPlanTest {
    private val all: List<Exercise> by lazy {
        // Unit tests run in the module directory, so the bundled asset is right there.
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Exercise>>(File("src/main/assets/exercises.json").readText()).sortedBy { it.name }
    }
    private val byId by lazy { all.associateBy { it.id } }

    private fun exercises(program: List<Routine>) = program.flatMap { r -> r.exercises.map { byId.getValue(it.exerciseId) } }

    // --- Limitations -------------------------------------------------------------------------

    @Test fun limitsMatchTheirExercises() {
        fun named(name: String) = all.first { it.name == name }
        assertTrue(Limit.Knees.hits(named("walking lunge")))
        assertTrue(Limit.LowerBack.hits(named("barbell deadlift")))
        assertTrue(Limit.Shoulders.hits(named("dumbbell seated shoulder press")))
        assertTrue(Limit.Wrists.hits(named("push-up")))
        assertFalse(Limit.Knees.hits(named("barbell bench press")))
        assertTrue(Coach.spares(named("barbell bench press"), Limit.entries.map { it.key }.toSet()))
    }

    @Test fun limitsKeepStrainingExercisesOutOfEveryPlan() {
        val limits = Limit.entries.map { it.key }.toSet()
        for (setup in Setup.entries) for (days in 2..6) for (seed in 0..3) {
            val program = Coach.buildProgram(all, Profile(days = days, setup = setup, level = Level.Intermediate, limits = limits), seed)
            val bad = exercises(program).filter { e -> Limit.entries.any { it.hits(e) } }
            assertTrue("$setup $days days seed $seed: ${bad.map { it.name }}", bad.isEmpty())
            assertTrue(program.all { it.exercises.isNotEmpty() })
        }
    }

    @Test fun withoutLimitsThePlanIsUnchanged() {
        val p = Profile(goal = Goal.Strength, level = Level.Advanced, days = 5, setup = Setup.Dumbbells)
        assertEquals(Coach.buildProgram(all, p, 3).map { it.exercises }, Coach.buildProgram(all, p.copy(limits = emptySet(), focus = emptySet()), 3).map { it.exercises })
    }

    // --- Session length ----------------------------------------------------------------------

    @Test fun sessionLengthFitsEachDay() {
        for (goal in Goal.entries) for (level in Level.entries) for (minutes in listOf(30, 45, 60, 75)) {
            val program = Coach.buildProgram(all, Profile(goal = goal, level = level, days = 4, sessionMinutes = minutes))
            program.forEach { r ->
                val m = Coach.minutes(r.exercises)
                // Long rests for heavy lifts leave no room below about half an hour; otherwise it stays close.
                assertTrue("$goal $level $minutes: ${r.name} takes $m", m <= maxOf(minutes + 3.0, 28.0))
                if (goal != Goal.FatLoss && goal != Goal.Fitness) assertTrue("$goal $level $minutes: ${r.name} takes $m", m >= minutes - 10)
            }
        }
    }

    @Test fun longerSessionsGetMoreWork() {
        val short = Coach.buildProgram(all, Profile(goal = Goal.Muscle, level = Level.Intermediate, days = 3, sessionMinutes = 30))
        val long = Coach.buildProgram(all, Profile(goal = Goal.Muscle, level = Level.Intermediate, days = 3, sessionMinutes = 75))
        short.zip(long).forEach { (s, l) ->
            assertTrue(l.exercises.size > s.exercises.size)
            assertTrue(Coach.minutes(l.exercises) > Coach.minutes(s.exercises) + 20)
            assertTrue(s.exercises.size >= 3)
        }
    }

    // --- Focus -------------------------------------------------------------------------------

    @Test fun focusAddsSetsAndAnExerciseForTheArea() {
        val base = Profile(goal = Goal.Muscle, level = Level.Intermediate, days = 3)
        fun chest(p: Profile) = Coach.buildProgram(all, p).first().exercises.filter { byId.getValue(it.exerciseId).target == "pectorals" }
        val plain = chest(base)
        val focused = chest(base.copy(focus = setOf(Focus.Chest.key)))
        assertEquals(plain.size + 1, focused.size)
        assertTrue(focused.sumOf { it.sets } > plain.sumOf { it.sets } + plain.size)
    }

    @Test fun focusOnlyTouchesDaysThatTrainTheArea() {
        val base = Profile(goal = Goal.Muscle, level = Level.Intermediate, days = 5)
        val plain = Coach.buildProgram(all, base)
        val arms = Coach.buildProgram(all, base.copy(focus = setOf(Focus.Arms.key)))
        // Push, pull and legs: the leg day has no arm work, so it stays as it was.
        assertEquals(plain[2].exercises, arms[2].exercises)
        assertTrue(arms[0].exercises.size > plain[0].exercises.size)
        assertTrue(arms[1].exercises.size > plain[1].exercises.size)
    }
}
