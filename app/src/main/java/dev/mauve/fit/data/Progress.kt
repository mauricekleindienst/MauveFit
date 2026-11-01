package dev.mauve.fit.data

import java.time.Instant
import java.time.ZoneId

/** Muscle recovery and achievements, both derived from the history. */
object Progress {

    /** The six groups shown on the recovery card, each with the target muscles it covers. */
    val groups get() = linkedMapOf(
        L("Brust", "Chest") to setOf("pectorals", "serratus anterior"),
        L("Rücken", "Back") to setOf("lats", "upper back", "traps", "spine"),
        L("Schultern", "Shoulders") to setOf("delts", "levator scapulae"),
        L("Arme", "Arms") to setOf("biceps", "triceps", "forearms"),
        L("Beine", "Legs") to setOf("quads", "hamstrings", "glutes", "calves", "adductors", "abductors"),
        L("Bauch", "Core") to setOf("abs"),
    )

    /**
     * Recovery per group from 0 to 1. A group needs about 48 h after a normal session; more sets
     * stretch that up to 72 h. Untrained groups count as fully recovered.
     */
    fun recovery(history: List<Workout>, repo: ExerciseRepo, now: Long = System.currentTimeMillis()): Map<String, Float> =
        groups.mapValues { (_, muscles) ->
            val recent = history.takeWhile { now - it.startedAt < 4 * Stats.DAY_MS }
            val hit = recent.firstNotNullOfOrNull { w ->
                val sets = w.entries.filter { repo[it.exerciseId]?.target in muscles }.sumOf { e -> e.sets.count { !it.warmup } }
                if (sets > 0) w.startedAt to sets else null
            } ?: return@mapValues 1f
            val (at, sets) = hit
            val needHours = (36 + sets * 3).coerceIn(36, 72)
            (((now - at) / 3_600_000f) / needHours).coerceIn(0f, 1f)
        }

    data class Achievement(val title: String, val detail: String, val progress: Float) {
        val unlocked get() = progress >= 1f
    }

    fun achievements(d: AppData): List<Achievement> {
        val h = d.history
        val sets = h.sumOf { it.doneSets.size }
        val volume = h.sumOf { it.volume }
        val streak = Stats.weekStreak(h, d.settings.weeklyGoal)
        val bestStreak = bestWeekStreak(h, d.settings.weeklyGoal).coerceAtLeast(streak)
        val zone = ZoneId.systemDefault()
        val hours = h.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        val distinct = h.flatMap { w -> w.entries.map { it.exerciseId } }.distinct().size
        val records = Stats.records(h).size
        fun p(v: Number, goal: Number) = (v.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
        return listOf(
            Achievement(L("Erster Schritt", "First step"), L("Erstes Training abgeschlossen", "First workout completed"), p(h.size, 1)),
            Achievement(L("Dranbleiben", "Keep going"), L("10 Trainings", "10 workouts"), p(h.size, 10)),
            Achievement(L("Gewohnheit", "Habit"), L("50 Trainings", "50 workouts"), p(h.size, 50)),
            Achievement(L("Hundert", "Hundred"), L("100 Trainings", "100 workouts"), p(h.size, 100)),
            Achievement(L("Satz um Satz", "Set by set"), L("500 Sätze", "500 sets"), p(sets, 500)),
            Achievement(L("Tonnen bewegt", "Tons moved"), L("10 Tonnen Volumen", "10 tons of volume"), p(volume, 10_000)),
            Achievement(L("Schwerlast", "Heavy duty"), L("100 Tonnen Volumen", "100 tons of volume"), p(volume, 100_000)),
            Achievement(L("Serie", "Streak"), L("4 Wochen in Folge das Wochenziel", "Weekly goal 4 weeks in a row"), p(bestStreak, 4)),
            Achievement(L("Unaufhaltsam", "Unstoppable"), L("12 Wochen in Folge das Wochenziel", "Weekly goal 12 weeks in a row"), p(bestStreak, 12)),
            Achievement(L("Frühaufsteher", "Early bird"), L("Training vor 8 Uhr", "Workout before 8 am"), p(hours.count { it < 8 }, 1)),
            Achievement(L("Nachteule", "Night owl"), L("Training nach 21 Uhr", "Workout after 9 pm"), p(hours.count { it >= 21 }, 1)),
            Achievement(L("Vielseitig", "Versatile"), L("30 verschiedene Übungen", "30 different exercises"), p(distinct, 30)),
            Achievement(L("Rekordjäger", "Record hunter"), L("Bestwerte in 15 Übungen", "Records in 15 exercises"), p(records, 15)),
            Achievement(L("Ausdauer", "Endurance"), L("Ein Training über 90 Minuten", "A workout over 90 minutes"), p(h.maxOfOrNull { it.durationMs } ?: 0L, 90 * 60_000L)),
        )
    }

    private fun bestWeekStreak(h: List<Workout>, goal: Int): Int {
        val weeks = h.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount().filterValues { it >= goal }.keys.sorted()
        var best = 0
        var run = 0
        var prev: java.time.LocalDate? = null
        for (w in weeks) {
            run = if (prev != null && prev.plusWeeks(1) == w) run + 1 else 1
            best = maxOf(best, run)
            prev = w
        }
        return best
    }
}
