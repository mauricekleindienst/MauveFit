package dev.mauve.fit.data

import java.time.Instant
import java.time.YearMonth
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
        val streak = Stats.weekStreak(h, d.settings.weeklyGoal, d.frozenWeeks)
        // Frozen weeks keep a run alive here as they do for the live streak, or a badge earned
        // with a freeze would lock again once that streak ends.
        val bestStreak = Insights.streaks(h, d.settings.weeklyGoal, java.time.LocalDate.now(), d.frozenWeeks).best.coerceAtLeast(streak)
        val zone = ZoneId.systemDefault()
        val hours = h.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        val distinct = h.flatMap { w -> w.entries.map { it.exerciseId } }.distinct().size
        // Exercises with a record actually broken (an earlier best beaten), not every exercise done once.
        val records = Insights.prTimeline(h, Int.MAX_VALUE).map { it.exerciseId }.distinct().size
        val level = Quests.level(Quests.xp(d)).level
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
            Achievement(L("Rekordjäger", "Record hunter"), L("In 15 Übungen den eigenen Bestwert verbessert", "Beat your own best in 15 exercises"), p(records, 15)),
            Achievement(L("Ausdauer", "Endurance"), L("Ein Training über 90 Minuten", "A workout over 90 minutes"), p(h.maxOfOrNull { it.durationMs } ?: 0L, 90 * 60_000L)),
            Achievement(L("Marathon", "Marathon"), L("250 Trainings", "250 workouts"), p(h.size, 250)),
            Achievement(L("Satzmaschine", "Set machine"), L("2000 Sätze", "2000 sets"), p(sets, 2000)),
            Achievement(L("Halbes Jahr", "Half a year"), L("26 Wochen in Folge das Wochenziel", "Weekly goal 26 weeks in a row"), p(bestStreak, 26)),
            Achievement(L("Erster Rekord", "First record"), L("Einen eigenen Bestwert verbessert", "Beat one of your own bests"), p(records, 1)),
            Achievement(L("Herausforderer", "Challenger"), L("10 Wochen-Challenges geschafft", "10 weekly challenges done"), p(d.challengesDone.size, 10)),
            Achievement(L("Questjäger", "Quest hunter"), L("50 Quests erledigt", "50 quests done"), p(d.questsDone, 50)),
            Achievement(L("Aufsteiger", "Climber"), L("Level 10 erreicht", "Reach level 10"), p(level, 10)),
            Achievement(L("Veteran", "Veteran"), L("Level 25 erreicht", "Reach level 25"), p(level, 25)),
            Achievement(L("Wochenendkrieger", "Weekend warrior"), L("10 Trainings am Wochenende", "10 workouts on a weekend"),
                p(h.count { Stats.day(it.startedAt).dayOfWeek.value >= 6 }, 10)),
            Achievement(L("Ausdauerläufer", "Cardio fan"), L("10 Cardio-Einheiten", "10 cardio sessions"), p(d.cardio.size, 10)),
        )
    }

    // --- Body measurements, heatmap and monthly recap --------------------------------

    /** Tape measurements tracked in the Body tab, key to label. */
    val measures get() = linkedMapOf(
        "waist" to L("Taille", "Waist"),
        "chest" to L("Brust", "Chest"),
        "hips" to L("Hüfte", "Hips"),
        "arm" to L("Oberarm", "Arm"),
        "thigh" to L("Oberschenkel", "Thigh"),
    )

    /** Muscles on the heatmap, in body order from top to bottom. */
    val heatmapMuscles = listOf(
        "traps", "delts", "pectorals", "lats", "upper back", "biceps", "triceps", "forearms",
        "abs", "spine", "glutes", "quads", "hamstrings", "adductors", "calves",
    )

    /** Working sets per heatmap muscle over the last [days] days; untrained muscles count 0. */
    fun heatmap(history: List<Workout>, repo: ExerciseRepo, days: Int = 28, now: Long = System.currentTimeMillis()): Map<String, Int> {
        val counts = heatmapMuscles.associateWith { 0 }.toMutableMap()
        history.takeWhile { now - it.startedAt < days * Stats.DAY_MS }.forEach { w ->
            w.entries.forEach { e ->
                val m = repo[e.exerciseId]?.target ?: return@forEach
                if (m in counts) counts[m] = counts.getValue(m) + e.sets.count { !it.warmup }
            }
        }
        return counts
    }

    data class MonthRecap(
        val month: YearMonth,
        val workouts: Int,
        val volume: Double,
        val timeMs: Long,
        /** Working reps, for months of body-weight training. */
        val reps: Int = 0,
        val records: Int,
        /** Target muscle with the most working sets, or null without workouts. */
        val topMuscle: String?,
    )

    /** Recaps for this month and the [count] - 1 months before it, newest first. */
    fun monthRecaps(history: List<Workout>, repo: ExerciseRepo, count: Int = 3): List<MonthRecap> {
        val records = recordsPerMonth(history)
        val byMonth = history.groupBy { YearMonth.from(Stats.day(it.startedAt)) }
        val now = YearMonth.now()
        return (0 until count).map { i ->
            val month = now.minusMonths(i.toLong())
            val list = byMonth[month].orEmpty()
            val muscles = mutableMapOf<String, Int>()
            list.forEach { w ->
                w.entries.forEach { e ->
                    repo[e.exerciseId]?.target?.let { m -> muscles[m] = (muscles[m] ?: 0) + e.sets.count { !it.warmup } }
                }
            }
            MonthRecap(
                month, list.size, list.sumOf { it.volume }, list.sumOf { it.durationMs },
                records = records[month] ?: 0, topMuscle = muscles.maxByOrNull { it.value }?.key,
                reps = list.sumOf { w -> w.doneSets.sumOf { it.second.reps } },
            )
        }
    }

    /**
     * Records broken per month: each workout that beats an exercise's earlier best counts once
     * for that exercise. The first time an exercise is done sets the bar and is not a record.
     */
    private fun recordsPerMonth(history: List<Workout>): Map<YearMonth, Int> {
        val best = mutableMapOf<String, Double>()
        val out = mutableMapOf<YearMonth, Int>()
        history.reversed().forEach { w ->
            val month = YearMonth.from(Stats.day(w.startedAt))
            w.entries.groupBy { it.exerciseId }.forEach { (id, entries) ->
                val sets = entries.flatMap { it.sets }.filter { !it.warmup }
                if (sets.isEmpty()) return@forEach
                val score = sets.maxOf { Stats.recordScore(it) }
                val prev = best[id]
                if (prev != null && score > prev) out[month] = (out[month] ?: 0) + 1
                if (prev == null || score > prev) best[id] = score
            }
        }
        return out
    }
}
