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

    /** Bronze to diamond; [tier] 0 for one-off achievements. */
    data class Achievement(val title: String, val detail: String, val progress: Float, val tier: Int = 0) {
        val unlocked get() = progress >= 1f
    }

    /** Tier names, Bronze (1) to Diamond (5). */
    fun tierName(tier: Int) = when (tier) {
        1 -> L("Bronze", "Bronze"); 2 -> L("Silber", "Silver"); 3 -> L("Gold", "Gold"); 4 -> L("Platin", "Platinum"); 5 -> L("Diamant", "Diamond")
        else -> ""
    }

    /** Coins an unlocked achievement pays: 50 for a one-off, more for each tier. */
    fun coins(a: Achievement) = if (a.tier == 0) 50 else 25 * a.tier * a.tier

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
        val prs = Insights.prTimeline(h, Int.MAX_VALUE)
        val records = prs.map { it.exerciseId }.distinct().size
        val level = Quests.level(Quests.xp(d)).level
        val cardioMin = d.cardio.sumOf { it.minutes }
        val maxCollection = Shop.collections(d).count { it.tier >= 5 }
        val weekend = h.count { Stats.day(it.startedAt).dayOfWeek.value >= 6 }
        fun p(v: Number, goal: Number) = (v.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

        /** Five tiers of one goal; each tier is its own achievement with its own target. */
        fun ladder(de: String, en: String, unitDe: (Long) -> String, unitEn: (Long) -> String, value: Number, steps: List<Long>) =
            steps.mapIndexed { k, t ->
                Achievement("${L(de, en)} · ${tierName(k + 1)}", L(unitDe(t), unitEn(t)), p(value, t), k + 1)
            }

        return listOf(
            Achievement(L("Erster Schritt", "First step"), L("Erstes Training abgeschlossen", "First workout completed"), p(h.size, 1)),
            Achievement(L("Frühaufsteher", "Early bird"), L("Training vor 8 Uhr", "Workout before 8 am"), p(hours.count { it < 8 }, 1)),
            Achievement(L("Nachteule", "Night owl"), L("Training nach 21 Uhr", "Workout after 9 pm"), p(hours.count { it >= 21 }, 1)),
            Achievement(L("Ausdauer", "Endurance"), L("Ein Training über 90 Minuten", "A workout over 90 minutes"), p(h.maxOfOrNull { it.durationMs } ?: 0L, 90 * 60_000L)),
            Achievement(L("Erster Rekord", "First record"), L("Einen eigenen Bestwert verbessert", "Beat one of your own bests"), p(records, 1)),
        ) +
            ladder("Trainings", "Workouts", { "$it Trainings" }, { "$it workouts" }, h.size, listOf(10, 50, 150, 500, 1000)) +
            ladder("Sätze", "Sets", { "$it Sätze" }, { "$it sets" }, sets, listOf(250, 1000, 5000, 15000, 40000)) +
            ladder("Tonnen", "Tons", { "$it Tonnen Volumen" }, { "$it tons of volume" }, volume / 1000, listOf(10, 100, 500, 2000, 10000)) +
            ladder("Serie", "Streak", { "$it Wochen in Folge das Wochenziel" }, { "Weekly goal $it weeks in a row" }, bestStreak, listOf(4, 12, 26, 52, 104)) +
            ladder("Rekordjäger", "Record hunter", { "In $it Übungen den Bestwert verbessert" }, { "Beat your best in $it exercises" }, records, listOf(5, 15, 40, 80, 150)) +
            ladder("Rekordflut", "Record streak", { "$it Rekorde gebrochen" }, { "$it records broken" }, prs.size, listOf(10, 50, 150, 400, 1000)) +
            ladder("Vielseitig", "Versatile", { "$it verschiedene Übungen" }, { "$it different exercises" }, distinct, listOf(15, 30, 60, 120, 250)) +
            ladder("Questjäger", "Quest hunter", { "$it Quests erledigt" }, { "$it quests done" }, d.questsDone, listOf(25, 100, 300, 1000, 2500)) +
            ladder("Herausforderer", "Challenger", { "$it Wochen-Challenges" }, { "$it weekly challenges" }, d.challengesDone.size, listOf(5, 15, 40, 100, 200)) +
            ladder("Aufsteiger", "Climber", { "Level $it erreicht" }, { "Reach level $it" }, level, listOf(5, 15, 30, 50, 75)) +
            ladder("Cardio", "Cardio", { "$it Minuten Cardio" }, { "$it minutes of cardio" }, cardioMin, listOf(300, 1500, 5000, 15000, 40000)) +
            ladder("Wochenendkrieger", "Weekend warrior", { "$it Trainings am Wochenende" }, { "$it weekend workouts" }, weekend, listOf(10, 30, 75, 150, 300)) +
            ladder("Sammler", "Collector", { "$it Sammlungen auf Stufe V" }, { "$it collections at tier V" }, maxCollection, listOf(1, 5, 15, 30, 60)) +
            ladder("Quest-Serie", "Quest streak", { "$it Tage in Folge alle Tagesquests" }, { "All daily quests $it days in a row" }, d.questStreakBest, listOf(3, 7, 30, 100, 365))
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
