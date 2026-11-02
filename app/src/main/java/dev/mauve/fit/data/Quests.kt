package dev.mauve.fit.data

import java.time.LocalDate
import kotlin.random.Random

/**
 * Daily quests, weekly side quests, XP and levels. Quests are picked from the date alone, so a day
 * keeps its three; their progress is read from the data. XP from training comes straight from the
 * history (an edited workout changes it), XP from quests is banked once claimed.
 */
object Quests {

    data class Quest(
        /** Stable key for claiming: "d:2026-11-02:Water", "w:2026-W45:1". */
        val key: String,
        val title: String,
        val progress: Int,
        val target: Int,
        val xp: Int,
    ) {
        val done get() = progress >= target
        val label get() = "${progress.coerceAtMost(target)}/$target"
    }

    enum class Daily(val xp: Int) { Train(50), Sets(30), Record(60), Water(20), Protein(30), Rate(15), Rpe(20), NewExercise(40), Cardio(30), Weigh(15), Warmup(20), Superset(30) }

    private fun workoutsOn(d: AppData, day: LocalDate) = d.history.filter { Stats.day(it.startedAt) == day }

    private fun targets(d: AppData) = d.bodyWeights.firstOrNull()?.kg?.let { Nutrition.targets(it, d.profile.goal) }

    /** The three quests of [day]: a training one, a nutrition or body one, and one of either. */
    fun daily(d: AppData, day: LocalDate = LocalDate.now()): List<Quest> {
        // A reroll bought in the shop draws the day's quests anew.
        val rerolls = if (d.rerollDay == day.toString()) d.rerollCount else 0
        val rnd = Random(day.toEpochDay() * 7919 + rerolls)
        val training = listOf(Daily.Train, Daily.Sets, Daily.Record, Daily.Rpe, Daily.NewExercise, Daily.Warmup, Daily.Superset, Daily.Rate)
        val body = listOfNotNull(Daily.Water, if (targets(d) != null) Daily.Protein else null, Daily.Cardio, Daily.Weigh)
        val first = training.random(rnd)
        val second = body.random(rnd)
        val third = (training + body).filter { it != first && it != second }.random(rnd)
        return listOf(first, second, third).map { quest(d, day, it) }
    }

    private fun quest(d: AppData, day: LocalDate, q: Daily): Quest {
        val done = workoutsOn(d, day) + listOfNotNull(d.active?.takeIf { Stats.day(it.startedAt) == day })
        val sets = done.flatMap { w -> w.entries.flatMap { it.sets } }.filter { it.done }
        val key = "d:$day:${q.name}"
        val sets10 = if (d.profile.level == Level.Beginner) 10 else 15
        fun of(title: String, p: Int, t: Int) = Quest(key, title, p, t, q.xp)
        return when (q) {
            Daily.Train -> of(L("Ein Training abschließen", "Finish a workout"), workoutsOn(d, day).size, 1)
            Daily.Sets -> of(L("$sets10 Sätze schaffen", "Do $sets10 sets"), sets.count { !it.warmup }, sets10)
            Daily.Record -> of(L("Einen Rekord brechen", "Break a record"),
                Insights.prTimeline(d.history, Int.MAX_VALUE).count { it.date == day }, 1)
            Daily.Water -> {
                val goal = targets(d)?.waterMl ?: 2000
                of(L("${goal / 1000.0} l trinken".replace(".0 l", " l").replace('.', ','), "Drink ${goal / 1000.0} l".replace(".0 l", " l")),
                    (d.water[day.toString()] ?: 0) / 250, goal / 250)
            }
            Daily.Protein -> {
                val goal = targets(d)?.protein ?: 100
                of(L("Proteinziel erreichen ($goal g)", "Hit your protein goal ($goal g)"),
                    d.food.filter { it.date == day.toString() }.sumOf { it.protein }, goal)
            }
            Daily.Rate -> of(L("Ein Training bewerten", "Rate a workout"), workoutsOn(d, day).count { it.feel > 0 }, 1)
            Daily.Rpe -> of(L("3 Sätze mit RPE bewerten", "Rate 3 sets with RPE"), sets.count { it.rpe > 0 }, 3)
            Daily.NewExercise -> {
                val before = d.history.filter { Stats.day(it.startedAt).isBefore(day) }.flatMap { w -> w.entries.map { it.exerciseId } }.toSet()
                val new = done.flatMap { w -> w.entries.filter { e -> e.sets.any { it.done && !it.warmup } }.map { it.exerciseId } }.count { it !in before }
                of(L("Eine neue Übung ausprobieren", "Try a new exercise"), new.coerceAtMost(1), 1)
            }
            Daily.Cardio -> of(L("20 Minuten Cardio", "20 minutes of cardio"),
                d.cardio.filter { Stats.day(it.at) == day }.sumOf { it.minutes }, 20)
            Daily.Weigh -> of(L("Körpergewicht eintragen", "Log your body weight"),
                d.bodyWeights.count { Stats.day(it.at) == day }.coerceAtMost(1), 1)
            Daily.Warmup -> of(L("Mit Aufwärmsätzen starten", "Start with warm-up sets"), sets.count { it.warmup }.coerceAtMost(2), 2)
            Daily.Superset -> of(L("Einen Supersatz trainieren", "Train a superset"),
                done.count { w -> w.entries.any { e -> e.superset != null && e.sets.any { it.done } } }.coerceAtMost(1), 1)
        }
    }

    /** The week's side quests: two more challenges next to the main one, each a different kind. */
    fun weekly(d: AppData, repo: ExerciseRepo, today: LocalDate = LocalDate.now()): List<Quest> = weekly(d, { repo[it]?.target }, today)

    fun weekly(d: AppData, targetOf: (String) -> String?, today: LocalDate = LocalDate.now()): List<Quest> =
        (1..2).map { slot ->
            val c = Motivation.challenge(d, targetOf, today, slot)
            Quest("w:${c.week}:$slot", c.title, c.progress, c.target, WEEKLY_XP)
        }

    const val WEEKLY_XP = 60
    /** Coins per day of the quest streak, growing to seven times that. */
    const val STREAK_COINS = 15
    const val CHALLENGE_XP = 100

    /** Banks the XP of quests finished today and this week; returns the data unchanged when nothing new. */
    fun claim(d: AppData, repo: ExerciseRepo, today: LocalDate = LocalDate.now()): AppData = claim(d, { repo[it]?.target }, today)

    fun claim(d: AppData, targetOf: (String) -> String?, today: LocalDate = LocalDate.now()): AppData {
        val fresh = (daily(d, today) + weekly(d, targetOf, today)).filter { it.done && it.key !in d.questsClaimed }
        if (fresh.isEmpty()) return d
        // Only recent keys matter (today and this week); older ones can go.
        val kept = (d.questsClaimed + fresh.map { it.key }).takeLast(120)
        // A running XP booster doubles quest XP; coins are half the base XP.
        val boost = if (System.currentTimeMillis() < d.boostUntil) 2 else 1
        val xp = fresh.sumOf { it.xp }
        var out = d.copy(questsClaimed = kept, questXp = d.questXp + xp * boost, questsDone = d.questsDone + fresh.size, questCoins = d.questCoins + xp / 2)
        // All three of today done: the quest streak grows, and pays more each day up to a week.
        if (daily(d, today).all { it.done } && d.questStreakDay != today.toString()) {
            val run = if (d.questStreakDay == today.minusDays(1).toString()) d.questStreak + 1 else 1
            out = out.copy(questStreak = run, questStreakDay = today.toString(), questStreakBest = maxOf(d.questStreakBest, run),
                questCoins = out.questCoins + STREAK_COINS * run.coerceAtMost(7))
        }
        return out
    }

    // --- XP and levels -------------------------------------------------------------------

    /** XP a finished workout is worth: a base plus its working sets, capped so marathons don't run away. */
    fun workoutXp(w: Workout): Int = 40 + (w.doneSets.size * 3).coerceAtMost(60)

    const val RECORD_XP = 25

    fun xp(d: AppData): Int =
        d.history.sumOf(::workoutXp) +
            Insights.prTimeline(d.history, Int.MAX_VALUE).size * RECORD_XP +
            d.challengesDone.size * CHALLENGE_XP +
            d.questXp

    data class Rank(val level: Int, val title: String, val into: Int, val need: Int, val total: Int) {
        val fraction get() = (into.toFloat() / need).coerceIn(0f, 1f)
    }

    /** XP from one level to the next: 150 at first, 50 more for each level after. */
    private fun need(level: Int) = 100 + 50 * level

    fun level(xp: Int): Rank {
        var level = 1
        var left = xp
        while (left >= need(level)) { left -= need(level); level++ }
        return Rank(level, title(level), left, need(level), xp)
    }

    fun title(level: Int) = when {
        level < 5 -> L("Neuling", "Rookie")
        level < 10 -> L("Sportler", "Athlete")
        level < 15 -> L("Athlet", "Competitor")
        level < 20 -> L("Kraftpaket", "Powerhouse")
        level < 30 -> L("Profi", "Pro")
        else -> L("Legende", "Legend")
    }
}
