package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Regressions from the review in 1.19.3. */
class FixRunTest {
    @Test fun supersetWarmupsDoNotHandOverToThePartner() {
        val a = ExerciseEntry("a", superset = "g", sets = listOf(
            SetEntry(20.0, 10, warmup = true, done = true), SetEntry(30.0, 8, warmup = true), SetEntry(40.0, 8), SetEntry(40.0, 8),
        ))
        val b = ExerciseEntry("b", superset = "g", sets = listOf(SetEntry(10.0, 12), SetEntry(10.0, 12)))
        assertEquals(0, Supersets.current(listOf(a, b)))
        // Warm-ups through, one working set of A done: B's turn.
        val a2 = a.copy(sets = a.sets.mapIndexed { i, s -> if (i <= 2) s.copy(done = true) else s })
        assertEquals(1, Supersets.current(listOf(a2, b)))
    }

    @Test fun aFrozenWeekKeepsTheBestStreakAfterItEnds() {
        // 13 weeks: goal met in all but week 7, which was frozen; then two empty weeks.
        val monday = Stats.weekStart(LocalDate.now()).minusWeeks(15)
        val history = (0 until 13).filter { it != 6 }.flatMap { w ->
            List(3) { i -> Workout(name = "W", startedAt = monday.plusWeeks(w.toLong()).plusDays(i.toLong()).atTime(10, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), finishedAt = 1L) }
        }
        val d = AppData(history = history, frozenWeeks = setOf(Stats.weekKey(monday.plusWeeks(6))))
        val unstoppable = Progress.achievements(d).first { it.detail.contains("12") }
        assertTrue(unstoppable.unlocked)
    }

    @Test fun aWeightedSetBeatsManyRepsWithoutWeight() {
        fun w(at: Long, kg: Double, reps: Int) = Workout(name = "W", startedAt = at, finishedAt = at + 1,
            entries = listOf(ExerciseEntry("bench", sets = listOf(SetEntry(kg, reps, done = true)))))
        val r = Stats.records(listOf(w(2_000_000_000, 0.0, 30), w(1_000_000_000, 20.0, 8))).single()
        assertEquals(20.0, r.best.weight, 0.0)
    }

    @Test fun aSingleRepIsTheMaxItself() {
        assertEquals(100.0, Stats.e1rm(SetEntry(100.0, 1)), 0.0)
        assertEquals(116.67, Stats.e1rm(SetEntry(100.0, 5)), 0.01)
    }

    @Test fun macrosNeverExceedTheCalorieTarget() {
        for (kg in listOf(40.0, 55.0, 82.0)) for (goal in Goal.values()) {
            val t = Nutrition.targets(kg, goal)
            assertTrue("$kg $goal", t.protein * 4 + t.fat * 9 + t.carbs * 4 <= t.kcal + 6)
        }
    }

    @Test fun aWeightChangedByHandEndsTheReducedLoad() {
        val e = ExerciseEntry("bench", load = 0.7, sets = listOf(SetEntry(70.0, 8), SetEntry(70.0, 8)))
        val old = Workout(name = "W", entries = listOf(e))
        val raised = old.copy(entries = listOf(e.copy(sets = listOf(SetEntry(100.0, 8), SetEntry(70.0, 8)))))
        assertEquals(1.0, Live.manualLoad(old, raised).single().load, 0.0)
        val ticked = old.copy(entries = listOf(e.copy(sets = listOf(SetEntry(70.0, 8, done = true), SetEntry(70.0, 8)))))
        assertEquals(0.7, Live.manualLoad(old, ticked).single().load, 0.0)
    }

    @Test fun aSmallStepScalesTheLoadInsteadOfResettingIt() {
        val e = ExerciseEntry("bench", load = 0.7, sets = listOf(SetEntry(70.0, 8)))
        val old = Workout(name = "W", entries = listOf(e))
        val step = old.copy(entries = listOf(e.copy(sets = listOf(SetEntry(72.5, 8)))))
        assertEquals(0.725, Live.manualLoad(old, step).single().load, 0.001)
    }

    @Test fun repsSaidWithMalAloneAreReps() {
        assertEquals(8, VoiceInput.parse("acht mal").reps)
        assertEquals(null, VoiceInput.parse("acht mal").weight)
        val both = VoiceInput.parse("60 mal 8")
        assertEquals(60.0, both.weight!!, 0.0)
        assertEquals(8, both.reps)
    }

    @Test fun loadIsAnchoredSoEditsDoNotPileUp() {
        val e = ExerciseEntry("bench", load = 0.7, sets = List(3) { SetEntry(70.0, 8) })
        val w0 = Workout(name = "W", entries = listOf(e))
        // Back-off set lowered: the top set still says 70 %.
        val backOff = w0.copy(entries = listOf(e.copy(sets = listOf(SetEntry(70.0, 8), SetEntry(70.0, 8), SetEntry(60.0, 8)))))
        assertEquals(0.7, Live.manualLoad(w0, backOff).single().load, 0.001)
        // A typo and its fix end where they started.
        val typo = w0.copy(entries = Live.manualLoad(w0, w0.copy(entries = listOf(e.copy(sets = listOf(SetEntry(700.0, 8)) + e.sets.drop(1))))))
        val fixed = typo.copy(entries = listOf(typo.entries.single().copy(sets = e.sets)))
        assertEquals(0.7, Live.manualLoad(typo, fixed).single().load, 0.001)
    }

    @Test fun warmupsStayBelowTheWork() {
        assertTrue(Coach.warmups(7.5, 2.5, 2.5).all { it.weight < 7.5 })
    }

    @Test fun eightTimesSixtyKiloIsEightReps() {
        val p = VoiceInput.parse("8 x 60 kg")
        assertEquals(60.0, p.weight!!, 0.0)
        assertEquals(8, p.reps)
        val t = VoiceInput.parse("60 times 8")
        assertEquals(60.0, t.weight!!, 0.0)
        assertEquals(8, t.reps)
    }
}
