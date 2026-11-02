package dev.mauve.fit.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The numbers behind the Insights screen: consistency, sets per muscle group, strength trends,
 * session habits, records, nutrition, body weight and effort. Pure functions over the stored data;
 * the exercise lookup and "today" are passed in, so everything runs on the JVM in tests. Each
 * section also has a one-sentence takeaway in plain language.
 */
object Insights {

    /** Hard sets per muscle group and week that studies link with good growth; below is little, above a lot. */
    const val SETS_LOW = 10.0
    const val SETS_HIGH = 20.0

    /** Looks up an exercise by id; [ExerciseRepo.get] in the app, a map in tests. */
    fun interface Lookup { operator fun invoke(id: String): Exercise? }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Signed change from [old] to [new] in percent; null when there is nothing to compare with. */
    fun pctChange(old: Double, new: Double): Double? = if (old <= 0.0) null else (new - old) / old * 100

    /** "18 %" without sign, for sentences that say up or down in words. */
    private fun pct(v: Double) = "${abs(v).roundToInt()} %"

    private fun dec(v: Double) = Stats.fmtKg(Math.round(v * 10) / 10.0)

    // --- Consistency ---------------------------------------------------------------------------

    /** One day of the calendar heatmap; [level] 0 = rest, 1 to 4 light to heavy, -1 = still to come. */
    data class Day(val date: LocalDate, val volume: Double, val level: Int)

    /** Intensity step of a day: quarters of the busiest day's volume; body-weight days count as light. */
    fun level(volume: Double, max: Double, trained: Boolean): Int = when {
        !trained -> 0
        volume <= 0.0 || max <= 0.0 -> 1
        else -> ceil(volume / max * 4).toInt().coerceIn(1, 4)
    }

    /** [weeks] whole weeks ending with the week of [today], week by week and Monday first in each. */
    fun calendar(history: List<Workout>, today: LocalDate, weeks: Int = 16): List<Day> {
        val start = Stats.weekStart(today).minusWeeks(weeks - 1L)
        val byDay = history.groupBy { Stats.day(it.startedAt) }.mapValues { (_, ws) -> ws.sumOf { it.volume } }
        val max = byDay.filterKeys { !it.isBefore(start) && !it.isAfter(today) }.values.maxOrNull() ?: 0.0
        return (0 until weeks * 7).map { i ->
            val d = start.plusDays(i.toLong())
            val v = byDay[d]
            if (d.isAfter(today)) Day(d, 0.0, -1) else Day(d, v ?: 0.0, level(v ?: 0.0, max, v != null))
        }
    }

    data class Streaks(val current: Int, val best: Int)

    /**
     * Weeks in a row with the goal met. Frozen weeks (ISO keys) neither count nor break a run, and
     * the running week only breaks it once it is over, as on the streak card.
     */
    fun streaks(history: List<Workout>, goal: Int, today: LocalDate, frozen: Set<String> = emptySet()): Streaks {
        if (history.isEmpty()) return Streaks(0, 0)
        val perWeek = history.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount()
        val now = Stats.weekStart(today)
        var week = perWeek.keys.min()
        var run = 0
        var best = 0
        while (!week.isAfter(now)) {
            when {
                (perWeek[week] ?: 0) >= goal.coerceAtLeast(1) -> { run++; best = maxOf(best, run) }
                week == now || Stats.weekKey(week) in frozen -> Unit
                else -> run = 0
            }
            week = week.plusWeeks(1)
        }
        return Streaks(run, best)
    }

    /** Hit [hit] of [of]; [pct] rounded. */
    data class Rate(val hit: Int, val of: Int) {
        val pct: Int get() = if (of == 0) 0 else (hit * 100.0 / of).roundToInt()
    }

    /**
     * Share of the last [weeks] weeks (from the first workout on) with the weekly goal met. The
     * running week only counts once it is met, so an unfinished week never pulls the rate down.
     */
    fun goalRate(history: List<Workout>, goal: Int, today: LocalDate, weeks: Int = 16): Rate {
        if (history.isEmpty()) return Rate(0, 0)
        val perWeek = history.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount()
        val now = Stats.weekStart(today)
        val first = maxOf(perWeek.keys.min(), now.minusWeeks(weeks - 1L))
        var hit = 0
        var of = 0
        var week = first
        while (!week.isAfter(now)) {
            val met = (perWeek[week] ?: 0) >= goal.coerceAtLeast(1)
            if (met) hit++
            if (met || week != now) of++
            week = week.plusWeeks(1)
        }
        return Rate(hit, of)
    }

    enum class TimeOfDay(private val de: String, private val en: String) {
        Morning("morgens", "in the morning"),
        Midday("mittags", "around midday"),
        Afternoon("nachmittags", "in the afternoon"),
        Evening("abends", "in the evening"),
        Night("nachts", "at night"),
        ;

        val label get() = L(de, en)

        companion object {
            fun of(hour: Int) = when (hour) {
                in 5..10 -> Morning
                in 11..13 -> Midday
                in 14..17 -> Afternoon
                in 18..21 -> Evening
                else -> Night
            }
        }
    }

    /** The weekday most workouts started on; ties go to the earlier day. */
    fun favouriteDay(history: List<Workout>): DayOfWeek? =
        history.groupingBy { Stats.day(it.startedAt).dayOfWeek }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<DayOfWeek, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key

    fun favouriteTime(history: List<Workout>): TimeOfDay? =
        history.groupingBy { TimeOfDay.of(Instant.ofEpochMilli(it.startedAt).atZone(zone).hour) }.eachCount()
            .maxByOrNull { it.value }?.key

    // --- Sets per muscle group -----------------------------------------------------------------

    /** Groups the weekly set ranges are judged by; [muscles] are the dataset's names, target and secondary. */
    enum class MuscleGroup(private val de: String, private val en: String, val muscles: Set<String>) {
        Chest("Brust", "Chest", setOf("pectorals", "chest", "serratus anterior")),
        Back("Rücken", "Back", setOf("lats", "upper back", "traps", "spine", "lower back", "rhomboids", "latissimus dorsi", "trapezius")),
        Shoulders("Schultern", "Shoulders", setOf("delts", "shoulders", "deltoids", "rear deltoids", "rotator cuff", "levator scapulae")),
        Biceps("Bizeps", "Biceps", setOf("biceps", "brachialis")),
        Triceps("Trizeps", "Triceps", setOf("triceps")),
        Quads("Quadrizeps", "Quads", setOf("quads", "quadriceps")),
        Hamstrings("Beinbeuger", "Hamstrings", setOf("hamstrings")),
        Glutes("Gesäß", "Glutes", setOf("glutes", "abductors")),
        Calves("Waden", "Calves", setOf("calves", "soleus")),
        Core("Bauch", "Core", setOf("abs", "core", "obliques", "lower abs", "hip flexors")),
        ;

        val label get() = L(de, en)

        companion object {
            fun of(muscle: String) = entries.firstOrNull { muscle.lowercase() in it.muscles }
        }
    }

    enum class Band { Under, Good, High }

    fun band(sets: Double) = when {
        sets < SETS_LOW -> Band.Under
        sets > SETS_HIGH -> Band.High
        else -> Band.Good
    }

    /**
     * Hard sets per group in [workouts]: completed working sets only (no warm-ups); the target
     * muscle's group gets 1 per set, every other group among the secondary muscles 0.5.
     */
    fun groupSets(workouts: List<Workout>, lookup: Lookup): Map<MuscleGroup, Double> {
        val out = mutableMapOf<MuscleGroup, Double>()
        workouts.forEach { w ->
            w.entries.forEach { e ->
                val n = e.sets.count { it.done && !it.warmup }
                val ex = if (n > 0) lookup(e.exerciseId) else null
                if (ex != null) {
                    val main = MuscleGroup.of(ex.target)
                    main?.let { out[it] = (out[it] ?: 0.0) + n }
                    ex.secondary.mapNotNull(MuscleGroup::of).distinct().filter { it != main }
                        .forEach { g -> out[g] = (out[g] ?: 0.0) + n * 0.5 }
                }
            }
        }
        return out
    }

    /** Hard sets per group in the 7 days up to and including [today]. */
    fun lastWeekSets(history: List<Workout>, lookup: Lookup, today: LocalDate): Map<MuscleGroup, Double> {
        val from = today.minusDays(6)
        return groupSets(history.filter { Stats.day(it.startedAt).let { d -> !d.isBefore(from) && !d.isAfter(today) } }, lookup)
    }

    /** Hard sets per group for each of the last [weeks] calendar weeks, oldest first. */
    fun weeklyGroupSets(history: List<Workout>, lookup: Lookup, today: LocalDate, weeks: Int = 12): Map<MuscleGroup, List<Double>> {
        val now = Stats.weekStart(today)
        val byWeek = history.groupBy { Stats.weekStart(Stats.day(it.startedAt)) }
        val perWeek = (weeks - 1 downTo 0).map { i -> groupSets(byWeek[now.minusWeeks(i.toLong())].orEmpty(), lookup) }
        return MuscleGroup.entries.associateWith { g -> perWeek.map { it[g] ?: 0.0 } }
    }

    // --- Strength ------------------------------------------------------------------------------

    data class Point(val date: LocalDate, val e1rm: Double)

    data class Lift(
        val exerciseId: String,
        /** Best estimated one-rep max per session, oldest first. */
        val points: List<Point>,
        val best: Double,
        /** Change over the last 8 weeks in percent, null without a session to compare with. */
        val change: Double?,
        val plateau: Boolean,
        /** Whole weeks since the last new high. */
        val weeksSinceBest: Int,
    )

    /**
     * Change of the estimated max over [weeks] weeks: the last session on or before the start of
     * the window (or the first one inside it) against the better of the two latest sessions.
     */
    fun liftChange(points: List<Point>, today: LocalDate, weeks: Int = 8): Double? {
        val from = today.minusWeeks(weeks.toLong())
        val base = points.lastOrNull { !it.date.isAfter(from) } ?: points.firstOrNull() ?: return null
        val recent = points.takeLast(2).filter { it.date.isAfter(base.date) && it.date.isAfter(from) }
        if (recent.isEmpty()) return null
        return pctChange(base.e1rm, recent.maxOf { it.e1rm })
    }

    /** Date of the session that set the current all-time high. */
    fun bestDate(points: List<Point>): LocalDate? {
        var best = Double.NEGATIVE_INFINITY
        var at: LocalDate? = null
        points.forEach { if (it.e1rm > best + 1e-9) { best = it.e1rm; at = it.date } }
        return at
    }

    /** Stuck: no new high for 4 weeks or more, with at least 3 sessions since the last one. */
    fun plateau(points: List<Point>, today: LocalDate): Boolean {
        val at = bestDate(points) ?: return false
        return ChronoUnit.DAYS.between(at, today) >= 28 && points.count { it.date.isAfter(at) } >= 3
    }

    /** The [count] weighted exercises trained in the most sessions, each with its trend. */
    fun topLifts(history: List<Workout>, today: LocalDate, count: Int = 5): List<Lift> {
        val sessions = mutableMapOf<String, Int>()
        history.forEach { w ->
            w.entries.filter { e -> e.sets.any { !it.warmup && it.weight > 0 && it.reps > 0 } }
                .map { it.exerciseId }.distinct().forEach { sessions[it] = (sessions[it] ?: 0) + 1 }
        }
        return sessions.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(count).mapNotNull { (id, _) ->
            // Several sessions on one day (rare) keep the better one, so the line never jumps back.
            val points = Stats.sessions(history, id).filter { it.e1rm > 0 }.groupBy { it.date }
                .map { (d, s) -> Point(d, s.maxOf { it.e1rm }) }.sortedBy { it.date }
            if (points.size < 2) return@mapNotNull null
            val at = bestDate(points)!!
            Lift(id, points, points.maxOf { it.e1rm }, liftChange(points, today), plateau(points, today), (ChronoUnit.DAYS.between(at, today) / 7).toInt())
        }
    }

    // --- Sessions ------------------------------------------------------------------------------

    data class Sessions(
        /** Average length of finished sessions, null when none has a plausible duration. */
        val avgMinutes: Int?,
        val avgSets: Double,
        /** Workouts per week for the last 12 weeks, oldest first. */
        val perWeek: List<Pair<LocalDate, Int>>,
        val count: Int,
    )

    fun sessions(history: List<Workout>, today: LocalDate, weeks: Int = 12): Sessions {
        val now = Stats.weekStart(today)
        val from = now.minusWeeks(weeks - 1L)
        val recent = history.filter { !Stats.day(it.startedAt).isBefore(from) }
        // Sessions left running overnight or closed after a minute would skew the average.
        val minutes = recent.filter { it.finishedAt != null }.map { it.durationMs / 60_000 }.filter { it in 5..240 }
        val perWeek = recent.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount()
        return Sessions(
            minutes.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            if (recent.isEmpty()) 0.0 else recent.sumOf { it.doneSets.size }.toDouble() / recent.size,
            (weeks - 1 downTo 0).map { i -> now.minusWeeks(i.toLong()).let { it to (perWeek[it] ?: 0) } },
            recent.size,
        )
    }

    /** Volume of the last [days] days against the [days] before, in percent; null without a base. */
    fun volumeChange(history: List<Workout>, today: LocalDate, days: Int = 28): Double? {
        var cur = 0.0
        var prev = 0.0
        history.forEach { w ->
            val ago = ChronoUnit.DAYS.between(Stats.day(w.startedAt), today)
            when (ago) {
                in 0 until days -> cur += w.volume
                in days until 2 * days -> prev += w.volume
            }
        }
        return pctChange(prev, cur)
    }

    // --- Records -------------------------------------------------------------------------------

    data class Pr(val exerciseId: String, val date: LocalDate, val set: SetEntry, val e1rm: Double, val gain: Double?)

    /**
     * Records as they were broken, newest first: a session's best set beating every earlier one of
     * that exercise (estimated max, or reps for body weight). The first session only sets the bar.
     */
    fun prTimeline(history: List<Workout>, count: Int = 10): List<Pr> {
        val best = mutableMapOf<String, Double>()
        val out = mutableListOf<Pr>()
        history.sortedBy { it.startedAt }.forEach { w ->
            w.entries.groupBy { it.exerciseId }.forEach { (id, entries) ->
                val sets = entries.flatMap { it.sets }.filter { it.done && !it.warmup }
                val top = sets.maxByOrNull { score(it) } ?: return@forEach
                val s = score(top)
                val prev = best[id]
                // A gain in percent only between two weighted bests; reps against kg is no percentage.
                val gain = if (prev != null && prev >= 1e6 && s >= 1e6) pctChange(prev - 1e6, s - 1e6) else null
                if (prev != null && s > prev + 1e-9) out += Pr(id, Stats.day(w.startedAt), top, Stats.e1rm(top), gain)
                if (prev == null || s > prev) best[id] = s
            }
        }
        return out.asReversed().take(count)
    }

    private fun score(s: SetEntry) = Stats.recordScore(s)

    // --- Nutrition -----------------------------------------------------------------------------

    data class Food(
        /** Days with anything logged among the last 14. */
        val logged: Int,
        val proteinHit: Int,
        val avgKcal: Int,
        val avgProtein: Int,
        val targets: Nutrition.Targets?,
        /** Average water on days with water logged, null without any. */
        val avgWater: Int?,
        /** Protein per day, oldest first; 0 on days without entries. */
        val protein: List<Int>,
    )

    /** The last [days] days of the food diary, or null when nothing was logged in them. */
    fun food(entries: List<FoodEntry>, water: Map<String, Int>, targets: Nutrition.Targets?, today: LocalDate, days: Int = 14): Food? {
        val range = (days - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
        val byDay = entries.groupBy { it.date }
        val logged = range.mapNotNull { byDay[it] }
        if (logged.isEmpty()) return null
        val kcal = logged.map { day -> day.sumOf { it.kcal } }
        val protein = logged.map { day -> day.sumOf { it.protein } }
        val waters = range.mapNotNull { water[it]?.takeIf { ml -> ml > 0 } }
        return Food(
            logged.size,
            targets?.let { t -> protein.count { it >= t.protein } } ?: 0,
            kcal.average().roundToInt(),
            protein.average().roundToInt(),
            targets,
            waters.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            range.map { d -> byDay[d]?.sumOf { it.protein } ?: 0 },
        )
    }

    // --- Body ----------------------------------------------------------------------------------

    /** Each value replaced by the mean of all values in the [days] days up to it; input in any order. */
    fun movingAverage(points: List<Pair<Long, Double>>, days: Int = 7): List<Pair<Long, Double>> {
        val sorted = points.sortedBy { it.first }
        val window = days * Stats.DAY_MS
        return sorted.map { (t, _) -> t to sorted.filter { it.first in (t - window + 1)..t }.map { it.second }.average() }
    }

    data class Weight(val points: List<Pair<Long, Double>>, val average: List<Pair<Long, Double>>, val change: Double, val sinceDays: Int)

    /** Weight with its 7-day average, from 5 entries on; [change] is the average's change over up to 4 weeks. */
    fun weight(entries: List<BodyWeight>): Weight? {
        if (entries.size < 5) return null
        val points = entries.map { it.at to it.kg }.sortedBy { it.first }
        val avg = movingAverage(points)
        val last = avg.last()
        val base = avg.lastOrNull { it.first <= last.first - 28 * Stats.DAY_MS } ?: avg.first()
        return Weight(points, avg, last.second - base.second, ((last.first - base.first) / Stats.DAY_MS).toInt())
    }

    /** Pearson correlation, or null for fewer than three pairs or no spread. */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = minOf(xs.size, ys.size)
        if (n < 3) return null
        val mx = xs.take(n).average()
        val my = ys.take(n).average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }

    /** Link between the readiness score at the start and the session's volume; needs 8 rated sessions. */
    fun readinessLink(history: List<Workout>): Double? {
        val rated = history.filter { it.readinessScore > 0 && it.volume > 0 }
        if (rated.size < 8) return null
        return pearson(rated.map { it.readinessScore.toDouble() }, rated.map { it.volume })
    }

    // --- Effort --------------------------------------------------------------------------------

    data class Effort(val sets: Int, val rated: Int, val avgRpe: Double?, val failure: Int, val drop: Int) {
        fun share(n: Int) = if (sets == 0) 0f else n / sets.toFloat()
    }

    /** Working sets of the last [weeks] weeks: how many carry an RPE, its average, failure and drop sets. */
    fun effort(history: List<Workout>, today: LocalDate, weeks: Int = 12): Effort {
        val from = today.minusWeeks(weeks.toLong())
        val sets = history.filter { Stats.day(it.startedAt).isAfter(from) }.flatMap { w -> w.doneSets.map { it.second } }
        val rated = sets.filter { it.rpe > 0 }
        return Effort(
            sets.size, rated.size, rated.takeIf { it.isNotEmpty() }?.map { it.rpe }?.average(),
            sets.count { it.type == SetType.Failure }, sets.count { it.type == SetType.Drop },
        )
    }

    // --- Everything at once --------------------------------------------------------------------

    data class Report(
        val calendar: List<Day>,
        val streaks: Streaks,
        val goalRate: Rate,
        val goal: Int,
        val favouriteDay: DayOfWeek?,
        val favouriteTime: TimeOfDay?,
        val lastWeek: Map<MuscleGroup, Double>,
        val weekly: Map<MuscleGroup, List<Double>>,
        val lifts: List<Lift>,
        val sessions: Sessions,
        val volumeChange: Double?,
        val prs: List<Pr>,
        val food: Food?,
        val weight: Weight?,
        val readiness: Double?,
        val effort: Effort,
    )

    fun report(d: AppData, lookup: Lookup, today: LocalDate): Report {
        val h = d.history
        val goal = d.settings.weeklyGoal
        // Body weight is stored newest first; targets follow the latest entry, as on the food tab.
        val targets = d.bodyWeights.maxByOrNull { it.at }?.kg?.let { Nutrition.targets(it, d.profile.goal) }
        return Report(
            calendar(h, today), streaks(h, goal, today, d.frozenWeeks), goalRate(h, goal, today), goal,
            favouriteDay(h), favouriteTime(h),
            lastWeekSets(h, lookup, today), weeklyGroupSets(h, lookup, today),
            // The whole timeline: the card shows the newest ten, the 30-day count needs them all.
            topLifts(h, today), sessions(h, today), volumeChange(h, today), prTimeline(h, Int.MAX_VALUE),
            food(d.food, d.water, targets, today), weight(d.bodyWeights), readinessLink(h), effort(h, today),
        )
    }

    // --- Takeaways -----------------------------------------------------------------------------

    private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, I18n.locale)

    fun consistencyLine(r: Report): String {
        val rate = r.goalRate
        val habit = r.favouriteDay?.let { day ->
            val time = r.favouriteTime?.let { " ${it.label}" }.orEmpty()
            // German weekday adverbs are lowercase: "dienstags abends".
            L(", am liebsten ${dayName(day).lowercase(I18n.locale)}s$time", ", mostly on ${dayName(day)}s$time")
        }.orEmpty()
        return if (rate.of == 0) L("Noch keine ganze Woche im Kalender$habit.", "No full week on the calendar yet$habit.")
        else L(
            "Wochenziel in ${rate.hit} von ${rate.of} Wochen erreicht (${rate.pct} %)$habit.",
            "Weekly goal met in ${rate.hit} of ${rate.of} weeks (${rate.pct} %)$habit.",
        )
    }

    fun volumeLine(sets: Map<MuscleGroup, Double>): String {
        val under = MuscleGroup.entries.filter { band(sets[it] ?: 0.0) == Band.Under }
        val high = MuscleGroup.entries.filter { band(sets[it] ?: 0.0) == Band.High }
        val good = MuscleGroup.entries.size - under.size - high.size
        return when {
            under.isEmpty() && high.isEmpty() -> L("Alle Muskelgruppen liegen im Bereich von 10 bis 20 Sätzen. Stark.", "Every muscle group sits between 10 and 20 sets. Strong.")
            under.isNotEmpty() -> {
                val least = under.sortedBy { sets[it] ?: 0.0 }.take(2).joinToString(L(" und ", " and ")) { it.label }
                L("$good von ${MuscleGroup.entries.size} Gruppen im Zielbereich; am wenigsten bekommen $least.",
                    "$good of ${MuscleGroup.entries.size} groups in range; $least get the least.")
            }
            else -> L("Über 20 Sätze bei ${high.joinToString { it.label }}; mehr bringt dort selten mehr.",
                "Over 20 sets for ${high.joinToString { it.label }}; more rarely helps there.")
        }
    }

    fun strengthLine(lifts: List<Lift>, lookup: Lookup): String {
        fun name(id: String) = lookup(id)?.title ?: id
        val stuck = lifts.filter { it.plateau }
        // A lift that stalled is not also named as the one going up.
        val best = lifts.filter { it.change != null && !it.plateau }.maxByOrNull { it.change!! }
        val gain = best?.takeIf { it.change!! >= 0.5 }?.let {
            L("${name(it.exerciseId)} legt in 8 Wochen ${pct(it.change!!)} zu", "${name(it.exerciseId)} is up ${pct(it.change!!)} in 8 weeks")
        }
        val plateau = stuck.firstOrNull()?.let {
            L("${name(it.exerciseId)} stagniert seit ${it.weeksSinceBest} Wochen", "${name(it.exerciseId)} has stalled for ${it.weeksSinceBest} weeks")
        }
        return when {
            gain != null && plateau != null -> "$gain; $plateau."
            gain != null -> "$gain."
            plateau != null -> L("$plateau. Zeit für neue Wiederholungszahlen oder eine Entlastungswoche.", "$plateau. Time for a new rep range or a deload week.")
            else -> L("Deine Hauptübungen halten ihr Niveau.", "Your main lifts are holding steady.")
        }
    }

    fun sessionLine(s: Sessions, volumeChange: Double?): String {
        // Average over the weeks since the first session in the window, not over empty weeks before the app was used.
        val active = s.perWeek.dropWhile { it.second == 0 }.size
        val perWeek = s.count / active.toDouble().coerceAtLeast(1.0)
        val one = dec(perWeek) == "1"
        val freq = L("im Schnitt ${dec(perWeek)} ${if (one) "Training" else "Trainings"} pro Woche", "${dec(perWeek)} ${if (one) "session" else "sessions"} a week on average")
        return when {
            volumeChange == null -> freq.replaceFirstChar { it.uppercase() } + "."
            abs(volumeChange) < 3 -> L("Dein Volumen ist in 4 Wochen stabil geblieben, $freq.", "Your volume held steady over 4 weeks, $freq.")
            volumeChange > 0 -> L("Dein Volumen ist in 4 Wochen um ${pct(volumeChange)} gestiegen, $freq.", "Your volume rose ${pct(volumeChange)} in 4 weeks, $freq.")
            else -> L("Dein Volumen ist in 4 Wochen um ${pct(volumeChange)} gesunken, $freq.", "Your volume fell ${pct(volumeChange)} in 4 weeks, $freq.")
        }
    }

    fun recordsLine(prs: List<Pr>, today: LocalDate): String {
        val month = prs.count { ChronoUnit.DAYS.between(it.date, today) < 30 }
        val last = prs.firstOrNull() ?: return ""
        val ago = ChronoUnit.DAYS.between(last.date, today)
        return when {
            month > 1 -> L("$month neue Rekorde in den letzten 30 Tagen.", "$month new records in the last 30 days.")
            month == 1 -> L("1 neuer Rekord in den letzten 30 Tagen.", "1 new record in the last 30 days.")
            else -> L("Der letzte Rekord ist $ago Tage her. Zeit für einen neuen.", "Your last record was $ago days ago. Time for a new one.")
        }
    }

    fun foodLine(f: Food): String {
        val t = f.targets ?: return L(
            "An ${f.logged} von 14 Tagen eingetragen, im Schnitt ${f.avgKcal} kcal und ${f.avgProtein} g Protein.",
            "Logged on ${f.logged} of 14 days, ${f.avgKcal} kcal and ${f.avgProtein} g protein on average.",
        )
        return L(
            "Proteinziel an ${f.proteinHit} von ${f.logged} eingetragenen Tagen erreicht, im Schnitt ${f.avgKcal} von ${t.kcal} kcal.",
            "Protein target hit on ${f.proteinHit} of ${f.logged} logged days, ${f.avgKcal} of ${t.kcal} kcal on average.",
        )
    }

    fun weightLine(w: Weight): String {
        val weeks = (w.sinceDays / 7).coerceAtLeast(1)
        val span = if (weeks == 1) L("1 Woche", "1 week") else L("$weeks Wochen", "$weeks weeks")
        return when {
            abs(w.change) < 0.2 -> L("Der 7-Tage-Schnitt ist in $span stabil geblieben.", "The 7-day average held steady over $span.")
            w.change > 0 -> L("Der 7-Tage-Schnitt ist in $span um ${dec(w.change)} kg gestiegen.", "The 7-day average rose ${dec(w.change)} kg in $span.")
            else -> L("Der 7-Tage-Schnitt ist in $span um ${dec(-w.change)} kg gefallen.", "The 7-day average fell ${dec(-w.change)} kg in $span.")
        }
    }

    fun readinessLine(r: Double): String = when {
        r >= 0.3 -> L("An gut erholten Tagen bewegst du deutlich mehr Volumen.", "On well-recovered days you move clearly more volume.")
        r <= -0.3 -> L("Überraschend: an weniger erholten Tagen trainierst du mit mehr Volumen.", "Surprisingly, you train with more volume on less-recovered days.")
        else -> L("Kein klarer Zusammenhang zwischen Erholung und Volumen.", "No clear link between recovery and volume.")
    }

    fun effortLine(e: Effort): String = when {
        e.rated == 0 -> L("Trag die RPE bei deinen Sätzen ein, dann siehst du hier, wie hart du trainierst.", "Log RPE on your sets to see how hard you train.")
        else -> {
            val avg = e.avgRpe!!
            val verdict = when {
                avg < 7 -> L("eher locker, da ist Luft nach oben", "on the easy side, there is room to push")
                avg <= 9 -> L("genau im wirksamen Bereich", "right in the effective range")
                else -> L("sehr nah am Limit, plane Erholung ein", "very close to the limit, plan in recovery")
            }
            L("Im Schnitt RPE ${dec(avg)}, $verdict.", "Average RPE ${dec(avg)}, $verdict.")
        }
    }
}
