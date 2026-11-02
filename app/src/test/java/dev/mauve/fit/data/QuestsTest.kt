package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class QuestsTest {
    private val day = LocalDate.of(2026, 11, 2)

    @Test fun aDayHasThreeDifferentQuestsThatStayPut() {
        val q = Quests.daily(AppData(), day)
        assertEquals(3, q.size)
        assertEquals(3, q.map { it.key }.distinct().size)
        assertEquals(q.map { it.key }, Quests.daily(AppData(), day).map { it.key })
    }

    @Test fun levelsGrowSteadily() {
        assertEquals(1, Quests.level(0).level)
        assertEquals(1, Quests.level(149).level)
        assertEquals(2, Quests.level(150).level)
        // 150 + 200 to reach level 3.
        assertEquals(3, Quests.level(350).level)
        assertEquals(0, Quests.level(350).into)
    }

    @Test fun aWorkoutTodayFinishesTheTrainQuestAndIsPaidOnce() {
        val at = day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val w = Workout(name = "W", startedAt = at, finishedAt = at + 3_600_000,
            entries = listOf(ExerciseEntry("x", sets = List(20) { SetEntry(50.0, 8, done = true) })))
        val d = AppData(history = listOf(w))
        val done = Quests.daily(d, day).filter { it.done }
        val claimed = Quests.claim(d, stubRepo(), day)
        assertEquals(done.sumOf { it.xp }, claimed.questXp - Quests.weekly(d, stubRepo(), day).filter { it.done }.sumOf { it.xp })
        // A second claim adds nothing.
        assertEquals(claimed.questXp, Quests.claim(claimed, stubRepo(), day).questXp)
        assertTrue(Quests.xp(claimed) >= Quests.workoutXp(w))
    }

    @Test fun sideQuestsDifferFromTheMainChallenge() {
        val d = AppData()
        val main = Motivation.challenge(d, stubRepo(), day).kind
        val side = (1..2).map { Motivation.challenge(d, stubRepo(), day, it).kind }
        side.forEach { assertNotEquals(main, it) }
        assertNotEquals(side[0], side[1])
    }

    private fun stubRepo(): (String) -> String? = { "pectorals" }

    private fun at(day: LocalDate) = day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun noTrainingQuestOnceTheWeeklyGoalIsMet() {
        // Monday to Wednesday trained, goal 3: Thursday is a rest day.
        val thu = LocalDate.of(2026, 11, 5)
        val h = (1..3).map { k -> Workout(name = "W", startedAt = at(thu.minusDays(k.toLong())), finishedAt = at(thu.minusDays(k.toLong())) + 1) }
        val keys = Quests.daily(AppData(history = h), thu).map { it.key.substringAfterLast(':') }
        assertTrue(keys.none { it in setOf("Train", "Sets", "Record", "Rpe", "Warmup", "Superset", "NewExercise") })
    }

    @Test fun waterTargetFollowsTheUsualDay() {
        val day = LocalDate.of(2026, 11, 5)
        val water = (1..7L).associate { day.minusDays(it).toString() to 2000 }
        // Rerolls until the water quest shows up, then check its size: a glass over the usual 2 l.
        val q = (0..20).asSequence().map { r -> Quests.daily(AppData(water = water, rerollDay = day.toString(), rerollCount = r), day) }
            .flatten().first { it.key.endsWith(":Water") }
        assertEquals(9, q.target)
    }
}
