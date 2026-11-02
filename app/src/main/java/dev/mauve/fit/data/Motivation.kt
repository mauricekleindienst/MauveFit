package dev.mauve.fit.data

import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/** Weekly challenge and streak freezes, both derived from the history plus a little bookkeeping. */
object Motivation {

    // --- Weekly challenge ------------------------------------------------------------

    enum class Kind { Workouts, Sets, Volume, Groups, Superset, Legs }

    data class Challenge(
        /** ISO week the challenge belongs to, e.g. "2026-W44". */
        val week: String,
        val kind: Kind,
        val title: String,
        val detail: String,
        val progress: Int,
        val target: Int,
        /** "2 von 3", "4,2 von 10 t". */
        val label: String,
    ) {
        val done get() = progress >= target
        val fraction get() = (progress.toFloat() / target).coerceIn(0f, 1f)
    }

    private val legs = setOf("quads", "hamstrings", "glutes", "calves", "adductors", "abductors")

    /**
     * This week's challenge. The pick only depends on the week, so it stays put all week and is
     * the same after a reinstall; the target follows the weekly goal and level. Progress counts
     * finished workouts of the week only.
     */
    fun challenge(d: AppData, repo: ExerciseRepo, today: LocalDate = LocalDate.now(), slot: Int = 0): Challenge =
        challenge(d, { repo[it]?.target }, today, slot)

    /** [targetOf] gives an exercise's target muscle by id (the repo in the app, a map in tests). */
    fun challenge(d: AppData, targetOf: (String) -> String?, today: LocalDate = LocalDate.now(), slot: Int = 0): Challenge {
        val start = Stats.weekStart(today)
        val key = Stats.weekKey(start)
        val week = d.history.filter { val day = Stats.day(it.startedAt); !day.isBefore(start) && day.isBefore(start.plusDays(7)) }
        val goal = d.settings.weeklyGoal.coerceIn(1, 7)
        val level = d.profile.level
        // Stride 5 over six kinds: neighbouring weeks never repeat, every kind comes round in six weeks.
        val n = start.get(IsoFields.WEEK_BASED_YEAR) * 53 + start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        // Side quests (slot 1, 2) take the kinds two and four further on: never the main one's.
        val kind = Kind.entries[Math.floorMod(n * 5 + slot * 2, Kind.entries.size)]
        fun of(p: Int, t: Int) = L("${p.coerceAtMost(t)} von $t", "${p.coerceAtMost(t)} of $t")

        return when (kind) {
            Kind.Workouts -> {
                // Beginners aim for the goal itself, everyone else for one more.
                val t = if (level == Level.Beginner) goal else (goal + 1).coerceAtMost(7)
                Challenge(key, kind, L("$t Trainings diese Woche", "$t workouts this week"),
                    if (t > goal) L("Eins mehr als dein Wochenziel", "One more than your weekly goal") else L("Dein Wochenziel, ganz erfüllt", "Your weekly goal, met in full"),
                    week.size, t, of(week.size, t))
            }
            Kind.Sets -> {
                val perWorkout = when (level) { Level.Beginner -> 12; Level.Intermediate -> 16; Level.Advanced -> 20 }
                val t = ((goal * perWorkout) / 5.0).roundToInt().coerceAtLeast(2) * 5
                val p = week.sumOf { it.doneSets.size }
                Challenge(key, kind, L("Insgesamt $t Sätze", "$t sets in total"), L("Arbeitssätze, Aufwärmen zählt nicht", "Working sets, warm-ups don't count"), p, t, of(p, t))
            }
            Kind.Volume -> if (d.profile.setup == Setup.Home) {
                // Without weights there is no tonnage; reps take its place.
                val perWorkout = when (level) { Level.Beginner -> 120; Level.Intermediate -> 180; Level.Advanced -> 250 }
                val t = ((goal * perWorkout) / 50.0).roundToInt().coerceAtLeast(2) * 50
                val p = week.sumOf { w -> w.doneSets.sumOf { it.second.reps } }
                Challenge(key, kind, L("Insgesamt $t Wiederholungen", "$t reps in total"), L("Über alle Trainings der Woche", "Across all workouts this week"), p, t, of(p, t))
            } else {
                val perWorkout = when (level) { Level.Beginner -> 2.0; Level.Intermediate -> 3.5; Level.Advanced -> 5.0 }
                val tons = (goal * perWorkout).roundToInt().coerceAtLeast(3)
                val kg = week.sumOf { it.volume }
                // Rounded down: 2,950 kg is "2.9 of 3 t", not a "3 of 3" that is not done yet.
                val shown = Stats.fmtKg((kotlin.math.floor(kg / 100) / 10).coerceAtMost(tons.toDouble()))
                Challenge(key, kind, L("$tons Tonnen Volumen", "$tons tons of volume"), L("Gewicht mal Wiederholungen, über alle Trainings", "Weight times reps, across all workouts"),
                    kotlin.math.floor(kg).toInt(), tons * 1000, L("$shown von $tons t", "$shown of $tons t"))
            }
            Kind.Groups -> {
                val groups = Progress.groups
                val hit = groups.values.count { muscles -> week.any { w -> w.entries.any { e -> targetOf(e.exerciseId) in muscles && e.sets.any { !it.warmup } } } }
                Challenge(key, kind, L("Jede große Muskelgruppe einmal", "Every major muscle group once"), groups.keys.joinToString(", "), hit, groups.size, of(hit, groups.size))
            }
            Kind.Superset -> {
                val p = week.count { w -> w.entries.groupBy { it.superset }.any { (group, es) -> group != null && es.size >= 2 } }
                Challenge(key, kind, L("Ein Training mit Supersatz", "A workout with a superset"),
                    L("Im Plan zwei Übungen als Supersatz verknüpfen", "Link two exercises as a superset in a plan"), p, 1, of(p, 1))
            }
            Kind.Legs -> {
                val t = if (goal <= 1) 1 else 2
                val p = week.count { w -> w.entries.any { e -> targetOf(e.exerciseId) in legs && e.sets.any { !it.warmup } } }
                Challenge(key, kind, if (t == 1) L("Einmal Beine", "Legs once") else L("Zwei Mal Beine", "Legs twice"),
                    L("Trainings mit mindestens einer Beinübung", "Workouts with at least one leg exercise"), p, t, of(p, t))
            }
        }
    }

    // --- Streak freezes ----------------------------------------------------------------

    /** Freezes earned per run of goal weeks, and the most kept in stock. */
    const val FREEZE_EVERY = 4
    const val FREEZE_MAX = 2

    data class FreezeState(val freezes: Int, val frozen: Set<String>, val settled: String, val run: Int)

    /**
     * Walks every finished week after [s].settled up to last week: a week that met [goal] extends
     * the run and every [FREEZE_EVERY]th one earns a freeze; a missed week during a run spends a
     * freeze and is remembered as frozen, without one the run ends. The first call starts at the
     * oldest workout, so earlier runs count too.
     */
    fun settle(history: List<Workout>, goal: Int, s: FreezeState, today: LocalDate = LocalDate.now()): FreezeState {
        val last = Stats.weekStart(today).minusWeeks(1)
        val first = Stats.weekOf(s.settled)?.plusWeeks(1)
            ?: history.minOfOrNull { it.startedAt }?.let { Stats.weekStart(Stats.day(it)) }
            ?: return s.copy(settled = Stats.weekKey(last))
        if (first.isAfter(last)) return s
        val perWeek = history.groupingBy { Stats.weekStart(Stats.day(it.startedAt)) }.eachCount()
        var freezes = s.freezes.coerceIn(0, FREEZE_MAX)
        var run = s.run
        val frozen = s.frozen.toMutableSet()
        // Ten years at most, should the clock ever jump.
        var week = maxOf(first, last.minusWeeks(520))
        while (!week.isAfter(last)) {
            when {
                (perWeek[week] ?: 0) >= goal.coerceAtLeast(1) -> {
                    run++
                    if (run % FREEZE_EVERY == 0) freezes = (freezes + 1).coerceAtMost(FREEZE_MAX)
                }
                run > 0 && freezes > 0 -> { freezes--; frozen += Stats.weekKey(week) }
                else -> run = 0
            }
            week = week.plusWeeks(1)
        }
        return FreezeState(freezes, frozen, Stats.weekKey(last), run)
    }

    /** "1 Joker", "2 freezes". */
    fun freezeLabel(n: Int) = L("$n Joker", if (n == 1) "1 freeze" else "$n freezes")
}
