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
        /** Formats an amount for the progress label, e.g. millilitres as litres. */
        val unit: ((Int) -> String)? = null,
    ) {
        val done get() = progress >= target
        val label get() = unit?.let { "${it(progress.coerceAtMost(target)).removeSuffix(" l")}/${it(target)}" } ?: "${progress.coerceAtMost(target)}/$target"
    }

    enum class Daily(val xp: Int) { Train(50), Sets(30), Record(60), Water(20), Protein(30), Rate(15), Rpe(20), NewExercise(40), Cardio(30), Weigh(15), Warmup(20), Superset(30) }

    private fun workoutsOn(d: AppData, day: LocalDate) = d.history.filter { Stats.day(it.startedAt) == day }

    private fun targets(d: AppData) = d.bodyWeights.firstOrNull()?.kg?.let { Nutrition.targets(it, d.profile.goal) }

    /**
     * What the days before [day] say about this person: whether today is a training day, which
     * parts of the app they use, and their usual amounts. Only the past counts, so the day's
     * quests stay the same from morning to night.
     */
    private class Context(d: AppData, day: LocalDate) {
        val past = d.history.filter { Stats.day(it.startedAt).isBefore(day) }
        private val weekStart = Stats.weekStart(day)
        private val doneThisWeek = past.count { !Stats.day(it.startedAt).isBefore(weekStart) }
        private val goal = d.settings.weeklyGoal.coerceIn(1, 7)
        private val daysLeft = 8 - day.dayOfWeek.value
        private val trainedYesterday = past.any { Stats.day(it.startedAt) == day.minusDays(1) }
        /** The goal still needs sessions, and either every day left counts or yesterday was a rest day. */
        val trainingDay = doneThisWeek < goal && (goal - doneThisWeek >= daysLeft || !trainedYesterday)
        val workouts = past.size

        private val from14 = day.minusDays(14).toString()
        private val today = day.toString()
        private val foodDays = d.food.filter { it.date >= from14 && it.date < today }.groupBy { it.date }.mapValues { (_, f) -> f.sumOf { it.protein } }
        private val waterDays = d.water.filterKeys { it >= from14 && it < today }.filterValues { it > 0 }
        private val recentCardio = d.cardio.filter { Stats.day(it.at).let { c -> c.isBefore(day) && !c.isBefore(day.minusDays(30)) } }

        val usesFood = foodDays.isNotEmpty()
        val usesWater = waterDays.isNotEmpty()
        val usesCardio = recentCardio.isNotEmpty()
        val weighs = d.settings.goalWeight > 0 || d.bodyWeights.any { !Stats.day(it.at).isBefore(day.minusDays(30)) }
        val usesRpe = past.take(10).any { w -> w.entries.any { e -> e.sets.any { it.rpe > 0 } } }
        val hasSupersets = d.routines.any { r -> r.exercises.any { it.superset != null } }

        /** Usual amounts, so a quest asks for a little more than a normal day, not a fixed number. */
        val typicalSets = past.take(5).map { it.doneSets.size }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val typicalWater = waterDays.values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val typicalProtein = foodDays.values.takeIf { it.isNotEmpty() }?.average()
        val typicalCardio = recentCardio.map { it.minutes }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    }

    /**
     * The three quests of [day], fitted to the person: on a training day two about the workout
     * and one about the body, on a rest day recovery and nutrition only; quests only for what is
     * used (water, food, cardio, RPE, supersets), and targets from their own usual amounts.
     */
    fun daily(d: AppData, day: LocalDate = LocalDate.now()): List<Quest> {
        // A reroll bought in the shop draws the day's quests anew.
        val rerolls = if (d.rerollDay == day.toString()) d.rerollCount else 0
        val rnd = Random(day.toEpochDay() * 7919 + rerolls)
        val c = Context(d, day)
        val hasTargets = targets(d) != null

        val body = buildMap {
            put(Daily.Water, if (c.usesWater) 3 else 1)
            if (c.usesFood && hasTargets) put(Daily.Protein, 3)
            // Less likely when not part of their habits, but there to try.
            put(Daily.Weigh, if (c.weighs) 2 else 1)
            put(Daily.Cardio, if (c.usesCardio) 2 else 1)
        }
        val picks = mutableListOf<Daily>()
        if (c.trainingDay) {
            val training = buildMap {
                put(Daily.Sets, 3)
                if (c.workouts >= 3) put(Daily.Record, 2)
                put(Daily.Rate, 1)
                if (c.usesRpe) put(Daily.Rpe, 2) else if (c.workouts >= 5) put(Daily.Rpe, 1)
                if (c.workouts >= 5) put(Daily.NewExercise, 1)
                if (c.workouts >= 2) put(Daily.Warmup, 1)
                if (c.hasSupersets) put(Daily.Superset, 1)
            }
            // The workout itself always, one more about it, one about the body.
            picks += Daily.Train
            picks += pick(training, rnd)
            picks += pick(body, rnd)
        } else {
            picks += pick(body, rnd, 3)
        }
        // Fill up should a pool run short (a new user on a rest day).
        for (spare in listOf(Daily.Water, Daily.Weigh, Daily.Cardio, Daily.Rate)) if (picks.size < 3 && spare !in picks) picks += spare
        return picks.take(3).map { quest(d, day, it, c) }
    }

    /** 1500 -> "1,5 l" (German) or "1.5 l". */
    fun litres(ml: Int): String =
        String.format(java.util.Locale.ROOT, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.').let { if (I18n.german) it.replace('.', ',') else it } + " l"

    /** [n] distinct picks from [weights], each as likely as its weight. */
    private fun pick(weights: Map<Daily, Int>, rnd: Random, n: Int = 1): List<Daily> {
        val left = weights.filterValues { it > 0 }.toMutableMap()
        return List(minOf(n, left.size)) {
            var r = rnd.nextInt(left.values.sum())
            val chosen = left.entries.first { (_, w) -> (r < w).also { r -= w } }.key
            left.remove(chosen)
            chosen
        }
    }

    private fun quest(d: AppData, day: LocalDate, q: Daily, c: Context): Quest {
        val done = workoutsOn(d, day) + listOfNotNull(d.active?.takeIf { Stats.day(it.startedAt) == day })
        val sets = done.flatMap { w -> w.entries.flatMap { it.sets } }.filter { it.done }
        val key = "d:$day:${q.name}"
        fun of(title: String, p: Int, t: Int) = Quest(key, title, p, t, q.xp)
        return when (q) {
            Daily.Train -> of(L("Ein Training abschließen", "Finish a workout"), workoutsOn(d, day).size, 1)
            Daily.Sets -> {
                // About a normal workout's sets, rounded to 5.
                val t = c.typicalSets?.let { (it / 5).toInt().coerceAtLeast(1) * 5 }?.coerceIn(8, 30)
                    ?: if (d.profile.level == Level.Beginner) 10 else 15
                of(L("$t Sätze schaffen", "Do $t sets"), sets.count { !it.warmup }, t)
            }
            Daily.Record -> of(L("Einen Rekord brechen", "Break a record"),
                Insights.prTimeline(d.history, Int.MAX_VALUE).count { it.date == day }, 1)
            Daily.Water -> {
                // A glass more than a usual day, between 1.5 and 2.5 l; 1.5 l to start with.
                val goal = c.typicalWater?.let { ((it + 250) / 250 * 250).coerceIn(1500, 2500) } ?: 1500
                Quest(key, L("${litres(goal)} trinken", "Drink ${litres(goal)}"), d.water[day.toString()] ?: 0, goal, q.xp, ::litres)
            }
            Daily.Protein -> {
                // A tenth over a usual day, never more than the target itself.
                val target = targets(d)?.protein ?: 100
                val goal = (c.typicalProtein?.let { minOf(it * 1.1, target.toDouble()) } ?: (target * 0.75)).let { (it / 10).toInt() * 10 }.coerceAtLeast(40)
                of(L("$goal g Protein essen", "Eat $goal g of protein"),
                    d.food.filter { it.date == day.toString() }.sumOf { it.protein }, goal)
            }
            Daily.Rate -> of(L("Ein Training bewerten", "Rate a workout"), workoutsOn(d, day).count { it.feel > 0 }, 1)
            Daily.Rpe -> of(L("3 Sätze mit RPE bewerten", "Rate 3 sets with RPE"), sets.count { it.rpe > 0 }, 3)
            Daily.NewExercise -> {
                val before = c.past.flatMap { w -> w.entries.map { it.exerciseId } }.toSet()
                val new = done.flatMap { w -> w.entries.filter { e -> e.sets.any { it.done && !it.warmup } }.map { it.exerciseId } }.count { it !in before }
                of(L("Eine neue Übung ausprobieren", "Try a new exercise"), new.coerceAtMost(1), 1)
            }
            Daily.Cardio -> {
                // The usual session length; on a rest day without cardio habits an easy 20 minutes.
                val t = c.typicalCardio?.let { (it / 5 * 5).coerceIn(15, 45) } ?: 20
                of(if (c.usesCardio) L("$t Minuten Cardio", "$t minutes of cardio") else L("$t Minuten locker bewegen", "$t minutes of easy movement"),
                    d.cardio.filter { Stats.day(it.at) == day }.sumOf { it.minutes }, t)
            }
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
