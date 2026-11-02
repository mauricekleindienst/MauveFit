package dev.mauve.fit.data

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The rule-based coach: builds programs from the profile, picks exercises and alternatives,
 * and turns the training history into short, specific advice.
 */
object Coach {

    // --- Exercise choice ------------------------------------------------------------

    /** Well-known staples the generator prefers when they fit the equipment. */
    private val staples = setOf(
        "0025", "0314", "0405", "0334", "0201", "0652", "0027", "0861", "0203", "0313", "0043", "0085", "1463",
        "0586", "0585", "0605", "0662", "0514", "1460", "3013", "0276", "0630", "1160", "1760", "0549", "0032",
        "0294", "1326", "2330", "0289", "0294",
    )
    private val strongWords = listOf("press", "squat", "row", "deadlift", "pull-up", "chin-up", "curl", "raise", "lunge", "push-up",
        "extension", "fly", "pulldown", "dip", "thrust", "bridge", "crunch", "plank", "swing", "calf")
    private val compoundWords = listOf("squat", "deadlift", "bench press", "press", "row", "pull-up", "chin-up", "leg press", "lunge", "dip", "hip thrust")
    private val isolationWords = listOf("extension", "curl", "raise", "fly", "jump", "kickback", "crunch", "shrug", "pushdown")
    private val weakWords = listOf("stretch", "(female)", "v. 2", "v. 3", "pov", "with towel", "assisted", "on bosu", "on stability",
        "isometric", "massage", "roll", "circles", "throw", "jerk", "jump step", "snatch", "clean", "(male)", "one arm", "single arm", "one leg", "kneeling")

    /** Higher is more typical: staples first, plain well-named movements next, oddities last. */
    fun score(e: Exercise, level: Level = Level.Intermediate, main: Boolean = false): Double {
        var s = 0.0
        // Opening slots of a day should be big compound lifts, not isolation or jumps.
        // Each word group counts once: a name that strings two movements together ("squat row",
        // "squatting curl") is a hybrid oddity, not twice as typical.
        if (main) {
            if (compoundWords.any { it in e.name }) s += 4
            if (isolationWords.any { it in e.name }) s -= 5
        }
        if (e.id in staples) s += 10
        val n = e.name
        val movements = strongWords.filter { it in n }
        if (movements.isNotEmpty()) s += 2
        if (movements.size >= 2 && "bench press" !in n && "leg press" !in n) s -= 3
        weakWords.forEach { if (it in n) s -= 4 }
        // Variants spelled out in brackets ("with rope attachment", "v. 2") are rarely the plain lift.
        if ('(' in n) s -= 1.5
        s -= n.length / 20.0
        when (e.equipment) {
            "barbell" -> s += if (level == Level.Beginner) 0.5 else 2.0
            "dumbbell", "cable" -> s += 1.5
            "leverage machine" -> s += if (level == Level.Beginner) 2.0 else 0.5
            "body weight" -> s += 1.0
        }
        if (e.category == "cardio" || e.category == "neck") s -= 3
        return s
    }

    private fun candidates(all: List<Exercise>, target: String, allow: (String) -> Boolean, level: Level, exclude: Set<String>, main: Boolean = false) =
        all.filter { it.target == target && allow(it.equipment) && it.id !in exclude }
            .sortedByDescending { score(it, level, main) }

    private fun candidates(repo: ExerciseRepo, target: String, allow: (String) -> Boolean, level: Level, exclude: Set<String>, main: Boolean = false) =
        candidates(repo.all, target, allow, level, exclude, main)

    /**
     * Picks for a target with the user's kit, leaving out what strains a joint they named; if the
     * kit has nothing for it, the setup alone decides.
     */
    private fun candidates(all: List<Exercise>, target: String, p: Profile, exclude: Set<String>, main: Boolean = false): List<Exercise> {
        val pool = if (p.limits.isEmpty()) all else all.filter { spares(it, p.limits) }
        return candidates(pool, target, p::allows, p.level, exclude, main).ifEmpty { candidates(pool, target, p.setup::allows, p.level, exclude, main) }
    }

    /** Alternatives for a swap: same target muscle, sorted by how typical they are. */
    fun alternatives(repo: ExerciseRepo, exerciseId: String, allow: (String) -> Boolean, limit: Int = 12): List<Exercise> {
        val e = repo[exerciseId] ?: return emptyList()
        // A compound lift gets compound alternatives, an isolation move gets isolation ones.
        val compound = compoundWords.any { it in e.name } && isolationWords.none { it in e.name }
        return candidates(repo, e.target, allow, Level.Intermediate, setOf(exerciseId), main = compound).take(limit)
    }

    /** Alternatives per equipment group, e.g. the machine version of a free-weight lift. */
    fun alternativesByKit(repo: ExerciseRepo, exerciseId: String, limit: Int = 8): Map<Kit, List<Exercise>> =
        Kit.entries.associateWith { kit -> alternatives(repo, exerciseId, { it in kit.equipment }, limit) }.filterValues { it.isNotEmpty() }

    // --- Programs ---------------------------------------------------------------------

    private data class Prescription(val sets: Int, val reps: Int, val rest: Int)

    private fun prescription(goal: Goal, level: Level, main: Boolean): Prescription {
        val base = when (goal) {
            Goal.Strength -> if (main) Prescription(5, 5, 180) else Prescription(3, 8, 120)
            Goal.Muscle -> if (main) Prescription(4, 8, 120) else Prescription(3, 12, 75)
            Goal.Fitness -> Prescription(3, 12, 60)
            Goal.FatLoss -> Prescription(3, 15, 45)
        }
        return if (level == Level.Beginner) base.copy(sets = (base.sets - 1).coerceAtLeast(2)) else base
    }

    private val dayTemplates = mapOf(
        "Push" to listOf("pectorals", "pectorals", "delts", "delts", "triceps"),
        "Pull" to listOf("lats", "upper back", "lats", "delts", "biceps", "biceps"),
        "Beine" to listOf("quads", "hamstrings", "quads", "glutes", "calves", "abs"),
        "Oberkörper" to listOf("pectorals", "lats", "delts", "upper back", "biceps", "triceps"),
        "Unterkörper" to listOf("quads", "hamstrings", "glutes", "calves", "abs"),
        "Ganzkörper A" to listOf("quads", "pectorals", "lats", "delts", "hamstrings", "abs"),
        "Ganzkörper B" to listOf("glutes", "upper back", "pectorals", "quads", "biceps", "triceps"),
        "Ganzkörper C" to listOf("hamstrings", "lats", "delts", "quads", "pectorals", "abs"),
    )

    private fun notes(day: String) = when (day) {
        "Push" -> "Brust · Schultern · Trizeps" to "Chest · Shoulders · Triceps"
        "Pull" -> "Rücken · Bizeps" to "Back · Biceps"
        "Beine", "Unterkörper" -> "Beine · Gesäß · Bauch" to "Legs · Glutes · Core"
        "Oberkörper" -> "Brust · Rücken · Schultern · Arme" to "Chest · Back · Shoulders · Arms"
        else -> "Alle großen Muskelgruppen" to "All major muscle groups"
    }

    private fun note(day: String) = notes(day).let { (de, en) -> L(de, en) }

    private fun nameEn(day: String) = when (day) {
        "Beine" -> "Legs"
        "Oberkörper" -> "Upper body"
        "Unterkörper" -> "Lower body"
        else -> day.replace("Ganzkörper", "Full body")
    }

    /** Day keys stay German internally; this is what the user sees. */
    private fun dayName(day: String) = if (I18n.german) day else nameEn(day)

    /**
     * A generated plan whose name and note are still the generated ones gets them in the current
     * language; anything the user renamed or rewrote stays as it is.
     */
    fun relocalize(r: Routine): Routine {
        if (!r.id.startsWith("gen-")) return r
        val day = dayTemplates.keys.firstOrNull { "gen-" + it.lowercase().replace(' ', '-') == r.id } ?: return r
        val name = if (r.name == day || r.name == nameEn(day)) dayName(day) else r.name
        val note = if (r.note in notes(day).toList()) note(day) else r.note
        return if (name == r.name && note == r.note) r else r.copy(name = name, note = note)
    }

    /** The split that fits the number of training days. */
    fun split(p: Profile): List<String> = when {
        p.days <= 2 -> listOf("Ganzkörper A", "Ganzkörper B")
        p.days == 3 && p.level == Level.Beginner -> listOf("Ganzkörper A", "Ganzkörper B", "Ganzkörper C")
        p.days == 3 -> listOf("Push", "Pull", "Beine")
        p.days == 4 -> listOf("Oberkörper", "Unterkörper")
        else -> listOf("Push", "Pull", "Beine")
    }

    fun splitLabel(p: Profile): String = when (split(p).first()) {
        "Ganzkörper A" -> L("Ganzkörper-Plan", "Full-body plan")
        "Oberkörper" -> L("Oberkörper / Unterkörper", "Upper / Lower")
        else -> L("Push / Pull / Beine", "Push / Pull / Legs")
    }

    /** Builds the plans for a profile; each day uses distinct exercises. */
    fun buildProgram(repo: ExerciseRepo, p: Profile, seed: Int = 0): List<Routine> = buildProgram(repo.all, p, seed)

    /**
     * Same, from a plain exercise list (the tests use it). Focus areas get a set more and one extra
     * exercise on days that train them; a chosen session length fits each day to the minutes.
     */
    fun buildProgram(all: List<Exercise>, p: Profile, seed: Int = 0): List<Routine> {
        val random = Random(seed)
        val focused = focusTargets(p)
        return split(p).map { day ->
            val used = mutableSetOf<String>()
            val template = dayTemplates.getValue(day)
            fun slot(target: String, main: Boolean): Slot? {
                val pick = candidates(all, target, p, used, main).take(if (seed == 0) 1 else 4).randomOrNull(random) ?: return null
                used += pick.id
                val rx = prescription(p.goal, p.level, main)
                val focus = pick.target in focused
                val sets = if (focus) (rx.sets + 1).coerceAtMost(6) else rx.sets
                return Slot(PlanExercise(pick.id, sets, if (pick.target == "abs") 15 else rx.reps, rx.rest), main, focus)
            }
            val slots = template.mapIndexedNotNull { i, target -> slot(target, main = i < 2) }.toMutableList()
            // One extra exercise per focus area this day already trains.
            Focus.entries.filter { it.key in p.focus }.mapNotNull { f -> f.targets.firstOrNull { it in template } }
                .forEach { target -> slot(target, main = false)?.let { slots += it } }
            if (p.sessionMinutes > 0) {
                // Extra exercises come from the focus areas first, then the day's own targets again.
                val more = (template.filter { it in focused } + template.drop(2) + template.take(2)).iterator()
                fitToMinutes(slots, p.sessionMinutes) {
                    var next: Slot? = null
                    while (next == null && more.hasNext()) next = slot(more.next(), main = false)
                    next
                }
            }
            Routine(id = "gen-" + day.lowercase().replace(' ', '-'), name = dayName(day), note = note(day), exercises = slots.map { it.ex })
        }
    }

    /** Body regions offered by the quick-workout generator, mapped to target muscles. */
    val regions = linkedMapOf(
        "Ganzkörper" to listOf("quads", "pectorals", "lats", "delts", "hamstrings", "glutes", "upper back", "abs"),
        "Oberkörper" to listOf("pectorals", "lats", "delts", "upper back", "biceps", "triceps", "pectorals", "lats"),
        "Beine" to listOf("quads", "hamstrings", "glutes", "calves", "quads", "adductors", "abductors"),
        "Brust & Arme" to listOf("pectorals", "triceps", "biceps", "pectorals", "triceps", "biceps"),
        "Rücken" to listOf("lats", "upper back", "lats", "traps", "spine", "upper back"),
        "Core" to listOf("abs", "abs", "spine", "abs", "glutes", "abs"),
    )

    /** Display name of a quick-workout region key. */
    fun regionLabel(key: String) = if (I18n.german) key else when (key) {
        "Ganzkörper" -> "Full body"
        "Oberkörper" -> "Upper body"
        "Beine" -> "Legs"
        "Brust & Arme" -> "Chest & arms"
        "Rücken" -> "Back"
        else -> key
    }

    /** A one-off workout for a region and duration, varied by [seed]. */
    fun quickWorkout(repo: ExerciseRepo, p: Profile, region: String, minutes: Int, seed: Int): Routine {
        val random = Random(seed)
        val rx = prescription(p.goal, p.level, main = false)
        val perExercise = (rx.sets * (40 + rx.rest)) / 60.0
        val count = (minutes / perExercise).toInt().coerceIn(3, 8)
        val targets = regions.getValue(region)
        val used = mutableSetOf<String>()
        val exercises = (0 until count).mapNotNull { i ->
            val pick = candidates(repo.all, targets[i % targets.size], p, used).take(5).randomOrNull(random)
                ?: return@mapNotNull null
            used += pick.id
            PlanExercise(pick.id, rx.sets, rx.reps, rx.rest)
        }
        return Routine(id = "quick-" + newId(), name = "${regionLabel(region)} · $minutes min", note = L("Vom Coach erstellt", "Built by the coach"), exercises = exercises)
    }

    // --- In-workout helpers ---------------------------------------------------------------

    /** Ramp-up sets before the first working set: 50 %, 70 %, 85 %. */
    fun warmups(workWeight: Double, step: Double, bar: Double): List<SetEntry> {
        if (workWeight <= bar) return emptyList()
        return listOf(0.5 to 8, 0.7 to 5, 0.85 to 3).map { (f, reps) ->
            SetEntry(weight = Stats.roundTo(workWeight * f, step).coerceAtLeast(bar), reps = reps, warmup = true)
        }.distinctBy { it.weight }.filter { it.weight < workWeight - 1e-6 }
    }

    private val plateSizes = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /** Plates per side for a barbell load; null if the load is below the bar. */
    fun plates(total: Double, bar: Double): List<Double>? {
        // Bounded: a typed 100000000 kg would otherwise loop millions of times on every keystroke.
        if (total < bar || !total.isFinite() || total > 1000) return null
        // Nearest load the smallest plate allows, up or down: 102 kg is 102.5, not 100.
        var side = Math.round((total - bar) / 2 / 1.25) * 1.25
        val out = mutableListOf<Double>()
        for (p in plateSizes) while (side >= p - 1e-6) { out += p; side -= p }
        return out
    }

    fun usesBarbell(e: Exercise?) = e?.equipment in setOf("barbell", "olympic barbell", "smith machine")

    // --- Smart weights and sets -------------------------------------------------------------

    fun deloadActive(d: AppData) = System.currentTimeMillis() < d.settings.deloadUntil

    /**
     * Weight to start an exercise with: progression from its own history; otherwise an estimate
     * from the strongest similar exercise (same target muscle and equipment) at 85 %, because a
     * new movement needs practice. Lighter weeks take 70 %.
     */
    /**
     * Load factor for today's readiness (auto-regulation): a short night or a raised resting pulse
     * means lighter starting weights; normal days change nothing. Null when there is no watch data.
     */
    fun readinessLoad(score: Int?): Double = when {
        score == null -> 1.0
        score < 40 -> 0.9
        score < 60 -> 0.95
        else -> 1.0
    }

    /** Load of the running block phase, or of the light week outside a block; 1.0 otherwise. */
    fun loadFactor(d: AppData): Double = blockWeek(d)?.phase?.load ?: if (deloadActive(d)) 0.7 else 1.0

    /**
     * Last working sets of an exercise at full load: a session started lighter (light week, block
     * phase, poor recovery) is scaled back up, so the cut does not carry over into the next one.
     */
    fun lastFullSets(d: AppData, exerciseId: String): List<SetEntry> {
        val prev = d.history.firstNotNullOfOrNull { w -> w.entries.firstOrNull { it.exerciseId == exerciseId } } ?: return emptyList()
        val sets = prev.sets.filter { !it.warmup }
        // The full-load weight remembered on the entry: no dividing a rounded weight back up
        // (5 kg cut to 4 rounds to 5, and 5 / 0.8 would come back as 7.5).
        if (prev.fullTop > 0) {
            val top = sets.maxOfOrNull { it.weight } ?: return sets
            if (top <= 0 || top >= prev.fullTop - 1e-6) return sets
            return sets.map { it.copy(weight = Stats.roundTo(it.weight * prev.fullTop / top, d.settings.weightStep)) }
        }
        val f = prev.load
        if (f <= 0 || abs(f - 1.0) < 1e-6) return sets
        return sets.map { it.copy(weight = Stats.roundTo(it.weight / f, d.settings.weightStep)) }
    }

    /**
     * Rough one-rep max as a share of body weight for the classic free-weight lifts, per level.
     * Deliberately low (between typical women's and men's numbers), since the app does not ask.
     */
    private val strengthRatios = listOf(
        "deadlift" to Triple(0.8, 1.15, 1.5),
        "squat" to Triple(0.65, 0.95, 1.3),
        "bench press" to Triple(0.5, 0.75, 1.0),
        "row" to Triple(0.45, 0.65, 0.85),
        "shoulder press" to Triple(0.33, 0.48, 0.65),
        "overhead press" to Triple(0.33, 0.48, 0.65),
        "military press" to Triple(0.33, 0.48, 0.65),
        "curl" to Triple(0.2, 0.3, 0.4),
    )

    /**
     * A careful first weight for a barbell or dumbbell lift that has never been logged, from body
     * weight and level: 80 % of the working weight the estimated max allows for [reps]. Dumbbells
     * are per hand. Null for anything else, e.g. machines, whose numbers differ from gym to gym.
     */
    fun firstEstimate(e: Exercise, bodyKg: Double?, level: Level, reps: Int, bar: Double = 20.0): Double? {
        if (bodyKg == null || bodyKg <= 0) return null
        val perHand = when (e.equipment) {
            "barbell" -> 1.0
            "dumbbell" -> 0.4
            else -> return null
        }
        val r = strengthRatios.firstOrNull { it.first in e.name }?.second ?: return null
        val ratio = when (level) { Level.Beginner -> r.first; Level.Intermediate -> r.second; Level.Advanced -> r.third }
        val max = bodyKg * ratio * perHand
        val w = max / (1 + reps / 30.0) * 0.8
        // An empty barbell is the least a barbell lift can start with.
        return if (e.equipment == "barbell") w.coerceAtLeast(bar) else w
    }

    /** [load] scales the suggestion, e.g. for readiness; 1.0 leaves it as progression decided. */
    fun startWeight(d: AppData, repo: ExerciseRepo, exerciseId: String, reps: Int, load: Double = 1.0, scaled: Boolean = true): Double {
        val step = d.settings.weightStep
        // Drop and failure sets end a session on purpose; the straight sets say whether to go up.
        val last = lastFullSets(d, exerciseId).let { all -> all.filter { it.type == SetType.Normal }.ifEmpty { all } }
        // After a break the full weight is last time's, not one step past it: the comeback session
        // does not count as progress.
        var w = if (last.isNotEmpty() && longBreak(d, exerciseId)) last.maxOf { it.weight } * (if (scaled) 0.8 else 1.0)
        else if (last.isNotEmpty()) rpeWeight(last, reps, step) ?: Stats.suggestWeight(last, reps, step) else {
            val e = repo[exerciseId]
            val similar = Stats.records(d.history).filter { r ->
                val o = repo[r.exerciseId]
                o != null && e != null && o.target == e.target && o.equipment == e.equipment && r.best.weight > 0
            }.maxOfOrNull { it.e1rm }
            if (similar != null) similar / (1 + reps / 30.0) * 0.85
            else e?.let { firstEstimate(it, d.bodyWeights.firstOrNull()?.kg, d.profile.level, reps, d.settings.barWeight) } ?: 0.0
        }
        // A block's phase sets the load; outside a block the light week does. Unscaled, this is the
        // full-load weight the entry remembers, so the next session continues from it exactly.
        if (scaled) w *= loadFactor(d) * load
        return if (w <= 0) 0.0 else Stats.roundTo(w, step).coerceAtLeast(step)
    }

    /**
     * No training at all for over a week: start at about 80 %, as the welcome-back tip says. Not
     * per exercise, or anything trained once a week would never progress.
     */
    fun longBreak(d: AppData, @Suppress("UNUSED_PARAMETER") exerciseId: String, now: Long = System.currentTimeMillis()): Boolean =
        d.history.firstOrNull()?.let { now - it.startedAt > 7 * Stats.DAY_MS } == true

    /**
     * The comeback cut as a load factor, recorded on the entry like a light week, so the next
     * session progresses from the full weight again, not from the 80 %.
     */
    /**
     * Reps to prefill: the plan's, except for a body-weight exercise done without extra weight
     * where last time every set reached the target: then one more than the best set, so the
     * "add a rep" advice shows in the numbers too.
     */
    fun startReps(d: AppData, repo: ExerciseRepo, exerciseId: String, planned: Int): Int {
        if (repo[exerciseId]?.bodyweight != true) return planned
        val last = Stats.lastSets(d.history, exerciseId).filter { !it.warmup }
        if (last.isEmpty() || last.any { it.weight > 0 }) return planned
        val target = last.firstOrNull { it.target > 0 }?.target ?: planned
        return if (last.all { it.reps >= target }) maxOf(planned, last.maxOf { it.reps } + 1) else planned
    }

    /** The full-load weight to remember on an entry started at [load] below 1, else 0 (nothing to remember). */
    fun fullTop(d: AppData, repo: ExerciseRepo, exerciseId: String, reps: Int, load: Double): Double =
        if (abs(load - 1.0) < 1e-6) 0.0 else startWeight(d, repo, exerciseId, reps, scaled = false)

    fun breakLoad(d: AppData, exerciseId: String): Double =
        if (longBreak(d, exerciseId) && lastFullSets(d, exerciseId).isNotEmpty()) 0.8 else 1.0

    /** Fewer exercises and sets, shorter rests: for days with little time. */
    fun shortVersion(r: Routine): Routine {
        val keep = ((r.exercises.size + 1) * 0.6).toInt().coerceAtLeast(minOf(3, r.exercises.size))
        return r.copy(
            name = L("${r.name} · kurz", "${r.name} · short"),
            // Never more than the plan: a 1-set exercise or a 30 s rest stays as it is.
            exercises = r.exercises.take(keep).map {
                it.copy(sets = minOf(it.sets, (it.sets - 1).coerceAtLeast(2)), restSec = minOf(it.restSec, (it.restSec * 3 / 4).coerceAtLeast(45)))
            },
        )
    }

    /** Rest after a set: shorter after warm-ups, longer when the set fell well short of its target. */
    fun restFor(entry: ExerciseEntry, set: SetEntry): Int = when {
        set.warmup -> 45
        set.target > 0 && set.reps <= set.target - 2 -> entry.restSec + 30
        else -> entry.restSec
    }

    data class SetAdvice(val text: String, val delta: Double)

    /**
     * After a working set: clearly more reps than planned means the weight is too light, clearly
     * fewer means too heavy. A rated set (RPE) speaks for itself and comes first.
     * Suggests a change for the sets still to come.
     */
    fun setAdvice(entry: ExerciseEntry, step: Double): SetAdvice? {
        val done = entry.sets.lastOrNull { it.done && !it.warmup && it.type == SetType.Normal } ?: return null
        if (entry.sets.none { !it.done && !it.warmup } || done.weight <= 0) return null
        rpeAdvice(done, step)?.let { return it }
        if (done.target <= 0) return null
        val diff = done.reps - done.target
        return when {
            diff >= 3 -> {
                val d = step * if (diff >= 5) 2 else 1
                SetAdvice(L("${done.reps} statt ${done.target} Wiederholungen: Das Gewicht ist zu leicht.", "${done.reps} instead of ${done.target} reps: the weight is too light."), d)
            }
            diff <= -3 -> {
                val d = -step * if (diff <= -5) 2 else 1
                SetAdvice(L("Nur ${done.reps} von ${done.target} Wiederholungen: etwas leichter bleibt sauber.", "Only ${done.reps} of ${done.target} reps: a little lighter keeps it clean."), d)
            }
            else -> null
        }
    }

    // --- Advice ------------------------------------------------------------------------------

    enum class Tone { Push, Rest, Info, Win }

    /** Something the user can do straight from a tip. */
    sealed class Action(val label: String) {
        class StartDeload : Action(L("Leichte Woche starten", "Start a light week"))
        class EndDeload : Action(L("Leichte Woche beenden", "End the light week"))
        class StartBlock : Action(L("Neuen Block starten", "Start a new block"))
        class AddExercise(val routineId: String, val exerciseId: String, routineName: String) :
            Action(L("Zu „$routineName“ hinzufügen", "Add to \"$routineName\""))
    }

    data class Tip(val title: String, val body: String, val tone: Tone = Tone.Info, val action: Action? = null)

    /** The single most useful message for the home screen. */
    fun homeTip(d: AppData, repo: ExerciseRepo): Tip? {
        val now = System.currentTimeMillis()
        val h = d.history
        val goal = d.settings.weeklyGoal
        // Without a plan there is no suggestion below to point at.
        if (Stats.nextRoutine(d.routines, h, repo) == null) return Tip(
            L("Kein Plan", "No plan"),
            L("Leg unter Training einen Plan an oder starte ein Schnelltraining.", "Create a plan under Training or start a quick workout."),
            Tone.Info,
        )
        if (h.isEmpty()) return Tip(
            L("Willkommen", "Welcome"),
            if (d.profile.setup == Setup.Home) L(
                "Starte mit dem vorgeschlagenen Training. Hör jeden Satz auf, wenn noch 2 bis 3 saubere Wiederholungen gingen.",
                "Start with the suggested workout. End each set with 2 to 3 clean reps still in the tank.",
            ) else L(
                "Starte mit dem vorgeschlagenen Training. Wähle Gewichte, bei denen noch 2 bis 3 Wiederholungen Reserve bleiben.",
                "Start with the suggested workout. Pick weights that leave 2 to 3 reps in reserve.",
            ),
            Tone.Push,
        )
        // A running light week is the one thing to know (and to end early); it goes before the rest.
        if (deloadActive(d)) deloadTip(d)?.let { return it }
        val sinceLast = now - h.first().startedAt
        if (sinceLast > 7 * Stats.DAY_MS) return Tip(
            L("Willkommen zurück", "Welcome back"),
            L(
                "Deine letzte Einheit ist ${sinceLast / Stats.DAY_MS} Tage her. Nimm heute etwa 80 % deiner letzten Gewichte und steigere dich danach wieder.",
                "Your last session was ${sinceLast / Stats.DAY_MS} days ago. Use about 80 % of your last weights today and build back up from there.",
            ),
            Tone.Rest,
        )
        cardioTip(d, now)?.let { return it }

        // A record set in the last two days deserves a mention.
        // Only a real improvement counts: the exercise needs an earlier session to beat.
        Stats.records(h).firstOrNull {
            now - it.date.toEpochDay() * Stats.DAY_MS < 2 * Stats.DAY_MS && Stats.sessions(h, it.exerciseId).size >= 2
        }?.let { r ->
            val name = repo[r.exerciseId]?.title ?: return@let
            val what = if (r.best.weight > 0) "${Stats.fmtKg(r.best.weight)} kg × ${r.best.reps}" else L("${r.best.reps} Wiederholungen", "${r.best.reps} reps")
            return Tip(L("Neuer Rekord", "New record"), L("$name: $what. Genau so geht Fortschritt.", "$name: $what. That is what progress looks like."), Tone.Win)
        }

        // Lighter on purpose is no plateau.
        if (!deloadActive(d) && blockWeek(d)?.phase != Phase.Deload) plateau(h, repo)?.let { return it }

        val done = Stats.workoutsThisWeek(h)
        val daysLeft = 7 - java.time.LocalDate.now().dayOfWeek.value + 1
        // Pointless to push for today once today is done.
        val trainedToday = Stats.day(h.first().startedAt) == java.time.LocalDate.now()
        if (!trainedToday && done < goal && goal - done >= daysLeft) return Tip(
            L("Wochenziel in Gefahr", "Weekly goal at risk"),
            L(
                "Noch ${goal - done} ${if (goal - done == 1) "Einheit" else "Einheiten"} und $daysLeft ${if (daysLeft == 1) "Tag" else "Tage"} übrig. Heute zu trainieren hält deine Serie.",
                "${goal - done} ${if (goal - done == 1) "session" else "sessions"} and $daysLeft ${if (daysLeft == 1) "day" else "days"} left. Training today keeps your streak.",
            ),
            Tone.Push,
        )
        if (done >= goal) return Tip(
            L("Wochenziel erreicht", "Weekly goal reached"),
            L(
                "$done von $goal Einheiten geschafft. Zusätzliches Training ist ein Bonus, Erholung zählt genauso.",
                "$done of $goal sessions done. Extra training is a bonus, rest counts just as much.",
            ),
            Tone.Win,
        )

        // Recovery: point at what was trained in the last day.
        if (sinceLast < Stats.DAY_MS) {
            val sore = h.first().entries.mapNotNull { repo[it.exerciseId]?.target }.distinct().take(2)
            if (sore.isNotEmpty()) return Tip(
                L("Erholung beachten", "Mind your recovery"),
                run {
                    val today = Stats.day(h.first().startedAt) == java.time.LocalDate.now()
                    L(
                        "${sore.joinToString(" und ") { Labels.muscle(it) }} ${if (sore.size == 1) "wurde" else "wurden"} " +
                            (if (today) "heute" else "gestern") + " trainiert. Der Vorschlag unten setzt auf ausgeruhte Muskeln.",
                        "${sore.joinToString(" and ") { Labels.muscle(it) }} " + (if (sore.size == 1) "was" else "were") +
                            " trained " + (if (today) "today" else "yesterday") + ". The suggestion below focuses on rested muscles.",
                    )
                },
                Tone.Rest,
            )
        }
        val blockEnd = d.settings.blockStart + d.settings.blockWeeks * 7 * Stats.DAY_MS
        if (blockFinished(d) && now < blockEnd + 7 * Stats.DAY_MS) return Tip(
            L("Block geschafft", "Block complete"),
            L("Alle Wochen sind durch. Ein neuer Block startet wieder mit der Grundlage, mit deinen neuen Gewichten.",
                "Every week is done. A new block starts again from the base, with your new weights."),
            Tone.Win, Action.StartBlock(),
        )
        if (blockWeek(d) == null) deloadTip(d)?.let { return it }
        imbalanceTip(d, repo)?.let { return it }
        balanceTip(d, repo)?.let { return it }

        return Tip(
            L("Bereit", "Ready"),
            L("Der Vorschlag unten trainiert die Muskeln, die sich am längsten erholt haben.", "The suggestion below trains the muscles that have rested longest."),
            Tone.Info,
        )
    }

    /**
     * A lighter week when the last three sessions all felt hard; while one runs, a reminder with
     * the option to end it early.
     */
    private fun deloadTip(d: AppData): Tip? {
        if (deloadActive(d)) {
            val left = ((d.settings.deloadUntil - System.currentTimeMillis()) / Stats.DAY_MS + 1).coerceAtLeast(1)
            return Tip(
                L("Leichte Woche läuft", "Light week running"),
                L("Noch $left ${if (left == 1L) "Tag" else "Tage"} mit 70 % der Gewichte. Danach geht es mit frischer Kraft weiter.",
                    "$left more ${if (left == 1L) "day" else "days"} at 70 % of your weights. Then back to it, fully recovered."),
                Tone.Rest, Action.EndDeload(),
            )
        }
        val rated = d.history.take(3)
        if (rated.size == 3 && rated.all { it.feel == 3 }) return Tip(
            L("Zeit für eine leichtere Woche", "Time for a lighter week"),
            L("Deine letzten drei Einheiten waren hart. Eine Woche mit 70 % der Gewichte lässt den Körper aufholen, danach bist du stärker.",
                "Your last three sessions were hard. A week at 70 % of your weights lets your body catch up, and you come back stronger."),
            Tone.Rest, Action.StartDeload(),
        )
        return null
    }

    /**
     * Over the last two weeks: a big muscle group with far fewer sets than the others gets a
     * suggestion for an exercise to add to the next plan.
     */
    private fun balanceTip(d: AppData, repo: ExerciseRepo): Tip? {
        if (addTipTaken(d)) return null
        val since = System.currentTimeMillis() - 14 * Stats.DAY_MS
        val recent = d.history.filter { it.startedAt >= since }
        if (recent.size < 3) return null
        val perGroup = Progress.groups.mapValues { (_, muscles) ->
            recent.sumOf { w -> w.entries.filter { repo[it.exerciseId]?.target in muscles }.sumOf { e -> e.sets.count { !it.warmup } } }
        }
        val max = perGroup.values.maxOrNull() ?: return null
        val (group, sets) = perGroup.entries.minByOrNull { it.value } ?: return null
        if (max < 12 || sets * 4 > max) return null
        val muscles = Progress.groups[group].orEmpty()
        val routine = Stats.nextRoutine(d.routines, d.history, repo) ?: return null
        val pick = repo.all.filter { it.target in muscles && d.profile.allows(it.equipment) && spares(it, d.profile.limits) && routine.exercises.none { p -> p.exerciseId == it.id } }
            .maxByOrNull { score(it, d.profile.level, main = true) } ?: return null
        return Tip(
            L("$group kommt zu kurz", "$group is falling behind"),
            L("Nur $sets Sätze in zwei Wochen, andere Gruppen bis zu $max. Vorschlag: ${pick.title}.",
                "Only $sets sets in two weeks, other groups up to $max. Suggestion: ${pick.title}."),
            Tone.Push, Action.AddExercise(routine.id, pick.id, routine.name),
        )
    }

    /** An exercise whose best set has not improved over its last three sessions. */
    private fun plateau(h: List<Workout>, repo: ExerciseRepo): Tip? {
        val recent = h.take(12).flatMap { w -> w.entries.map { it.exerciseId } }.distinct()
        for (id in recent) {
            val s = Stats.sessions(h, id)
            if (s.size < 4 || s.last().best.weight <= 0) continue
            val before = s.dropLast(3).maxOf { it.e1rm }
            if (s.takeLast(3).all { it.e1rm <= before + 0.01 }) {
                val name = repo[id]?.title ?: continue
                return Tip(
                    L("Plateau bei $name", "Plateau on $name"),
                    L(
                        "Seit drei Einheiten kein Fortschritt. Versuche 1 bis 2 Wiederholungen mehr statt mehr Gewicht, oder eine leichtere Woche mit 70 %.",
                        "No progress for three sessions. Try 1 to 2 more reps instead of more weight, or a lighter week at 70 %.",
                    ),
                    Tone.Info,
                )
            }
        }
        return null
    }

    /** A one-line cue for the exercise page during a workout. */
    fun exerciseTip(h: List<Workout>, exerciseId: String, targetReps: Int, bodyweight: Boolean): Tip {
        val last = Stats.lastSets(h, exerciseId)
        val sessions = Stats.sessions(h, exerciseId)
        if (last.isEmpty()) return Tip(
            L("Erstes Mal", "First time"),
            if (bodyweight) L("Sauber und kontrolliert, die Wiederholungen sind ein Richtwert.", "Clean and controlled; the reps are a guide.")
            else L(
                "Starte leicht. Das Gewicht passt, wenn nach dem Satz noch 2 bis 3 Wiederholungen gingen.",
                "Start light. The weight is right if you could do 2 to 3 more reps after the set.",
            ),
        )
        val lastAt = h.firstOrNull()?.startedAt ?: 0L
        if (!bodyweight && System.currentTimeMillis() - lastAt > 7 * Stats.DAY_MS) return Tip(
            L("Wieder einsteigen", "Easing back in"),
            L("Über eine Woche Trainingspause: heute etwa 80 % vom letzten Mal.", "Over a week without training: about 80 % of last time today."), Tone.Info,
        )
        if (sessions.size >= 4 && !bodyweight) {
            val before = sessions.dropLast(3).maxOf { it.e1rm }
            if (sessions.takeLast(3).all { it.e1rm <= before + 0.01 }) return Tip(
                "Plateau", L("Bleib beim Gewicht und hol dir 1 bis 2 Wiederholungen mehr pro Satz.", "Keep the weight and add 1 to 2 reps per set."), Tone.Info,
            )
        }
        val allHit = last.all { it.reps >= targetReps }
        return when {
            bodyweight && allHit -> Tip(L("Steigern", "Progress"), L("Letztes Mal alle Wiederholungen geschafft. Heute 1 bis 2 mehr pro Satz.", "You hit every rep last time. Add 1 to 2 per set today."), Tone.Push)
            allHit -> Tip(L("Steigern", "Progress"), L("Letztes Mal alle Wiederholungen geschafft, daher ist das Gewicht heute eine Stufe höher.", "You hit every rep last time, so the weight is one step up today."), Tone.Push)
            else -> Tip(L("Festigen", "Consolidate"), L("Letztes Mal knapp. Gleiches Gewicht, Ziel sind alle Wiederholungen sauber.", "Close last time. Same weight, aim for every rep with good form."), Tone.Info)
        }
    }

    // --- Training extras ---------------------------------------------------------------------

    /**
     * Next weight from rated sets: RPE 7 or easier left reps in the tank, so go up (two steps if
     * every rep was hit as well); RPE 10 was all-out, so hold, or step back if reps were missed.
     * Null when the last session was not rated, then the rep rule decides.
     */
    private fun rpeWeight(last: List<SetEntry>, targetReps: Int, step: Double): Double? {
        val rpe = last.filter { it.rpe > 0 }.maxOfOrNull { it.rpe } ?: return null
        val top = last.maxOf { it.weight }
        if (top <= 0) return null
        val allHit = last.all { it.reps >= targetReps }
        return when {
            rpe <= 7 -> top + step * if (allHit) 2 else 1
            rpe >= 10 -> if (allHit) top else (top - step).coerceAtLeast(0.0)
            else -> null
        }
    }

    private fun rpeAdvice(done: SetEntry, step: Double): SetAdvice? {
        if (done.rpe <= 0) return null
        val short = done.target > 0 && done.reps < done.target
        return when {
            done.rpe <= 7 && !short -> SetAdvice(
                L("RPE ${done.rpe}: Da ist noch Luft, etwas mehr Gewicht passt.", "RPE ${done.rpe}: there is room left, a bit more weight fits."), step,
            )
            done.rpe >= 10 && short -> SetAdvice(
                L("RPE 10 und Wiederholungen verpasst: etwas leichter hält die Sätze sauber.", "RPE 10 and reps missed: a little lighter keeps the sets clean."), -step,
            )
            else -> null
        }
    }

    /** Rear-delt and face-pull moves target the delts but belong to the pulling side. */
    private fun isPull(e: Exercise) = e.target in setOf("lats", "upper back", "biceps", "traps") ||
        (e.target == "delts" && listOf("rear", "row", "face pull", "reverse fly").any { it in e.name })

    private fun isPush(e: Exercise) = !isPull(e) && e.target in setOf("pectorals", "triceps", "delts", "serratus anterior")

    private val legMuscles = setOf("quads", "hamstrings", "glutes", "calves", "adductors", "abductors")

    /**
     * Over the last two weeks: pushing far outweighing pulling (or the reverse), or legs far behind
     * the upper body (or the reverse). Suggests a fitting exercise for the plan that already trains
     * that side, so the gap closes without a new plan.
     */
    /**
     * The gap behind an "add exercise" tip is measured over two weeks of history, so it outlives
     * the tap: without this the card offered another exercise for the same plan on every tap.
     */
    private fun addTipTaken(d: AppData) = d.addTipWeek == Stats.weekKey(java.time.LocalDate.now())

    private fun imbalanceTip(d: AppData, repo: ExerciseRepo): Tip? {
        if (addTipTaken(d)) return null
        val since = System.currentTimeMillis() - 14 * Stats.DAY_MS
        val recent = d.history.filter { it.startedAt >= since }
        if (recent.size < 3) return null
        var push = 0
        var pull = 0
        var legs = 0
        recent.forEach { w ->
            w.entries.forEach { en ->
                val e = repo[en.exerciseId] ?: return@forEach
                val sets = en.sets.count { !it.warmup }
                when {
                    isPull(e) -> pull += sets
                    isPush(e) -> push += sets
                    e.target in legMuscles -> legs += sets
                }
            }
        }
        val upper = push + pull
        // Each case: title, body, which exercises qualify, and the muscles a suggestion should hit.
        data class Gap(val title: String, val body: String, val fits: (Exercise) -> Boolean, val muscles: Set<String>)
        val gap = when {
            upper >= 12 && push >= pull * 1.6 && push - pull >= 6 -> Gap(
                L("Mehr ziehen als drücken", "Pull more than you push"),
                L("$push Drück- gegen $pull Zugsätze in zwei Wochen. Ein Ausgleich schont die Schultern",
                    "$push pushing against $pull pulling sets in two weeks. Balancing them protects your shoulders"),
                ::isPull, setOf("lats", "upper back"),
            )
            upper >= 12 && pull >= push * 1.6 && pull - push >= 6 -> Gap(
                L("Drücken kommt zu kurz", "Pushing is falling behind"),
                L("$pull Zug- gegen $push Drücksätze in zwei Wochen", "$pull pulling against $push pushing sets in two weeks"),
                ::isPush, setOf("pectorals", "delts"),
            )
            upper >= 16 && legs * 4 < upper -> Gap(
                L("Beine kommen zu kurz", "Legs are falling behind"),
                L("$legs Beinsätze gegen $upper für den Oberkörper in zwei Wochen", "$legs leg sets against $upper for the upper body in two weeks"),
                { it.target in legMuscles }, setOf("quads", "hamstrings", "glutes"),
            )
            legs >= 16 && upper * 2 < legs -> Gap(
                L("Oberkörper kommt zu kurz", "Upper body is falling behind"),
                L("$upper Oberkörpersätze gegen $legs Beinsätze in zwei Wochen", "$upper upper-body sets against $legs leg sets in two weeks"),
                { isPush(it) || isPull(it) }, setOf("pectorals", "lats", "upper back"),
            )
            else -> return null
        }
        // The plan that already trains this side most; otherwise the next one due.
        val routine = d.routines.filter { it.exercises.isNotEmpty() }
            .maxByOrNull { r -> r.exercises.count { p -> repo[p.exerciseId]?.let(gap.fits) == true } }
            ?.takeIf { r -> r.exercises.any { p -> repo[p.exerciseId]?.let(gap.fits) == true } }
            ?: Stats.nextRoutine(d.routines, d.history, repo) ?: return null
        val pick = repo.all.filter { it.target in gap.muscles && gap.fits(it) && d.profile.allows(it.equipment) && spares(it, d.profile.limits) && routine.exercises.none { p -> p.exerciseId == it.id } }
            .maxByOrNull { score(it, d.profile.level, main = true) } ?: return null
        return Tip(
            gap.title,
            gap.body + L(". Vorschlag: ${pick.title}.", ". Suggestion: ${pick.title}."),
            Tone.Push, Action.AddExercise(routine.id, pick.id, routine.name),
        )
    }

    // --- Training blocks ------------------------------------------------------------------

    /**
     * A week of a training block. Each phase shifts the plan a little: more reps and a bit lighter
     * to lay the base, an extra set to build, fewer reps and heavier at the peak, then a light week.
     */
    enum class Phase(
        private val de: String, private val en: String, private val deBlurb: String, private val enBlurb: String,
        val extraSets: Int, val extraReps: Int, val load: Double,
    ) {
        Base("Grundlage", "Base", "Mehr Wiederholungen, etwas leichter: Technik und Volumen.", "More reps, a bit lighter: technique and volume.", 0, 2, 0.95),
        Build("Aufbau", "Build", "Ein Satz mehr pro Übung bei gleichem Gewicht.", "One more set per exercise at the same weight.", 1, 0, 1.0),
        Peak("Spitze", "Peak", "Weniger Wiederholungen, schwerer: zeig, was du aufgebaut hast.", "Fewer reps, heavier: show what you built.", 0, -2, 1.05),
        Deload("Entlastung", "Deload", "Weniger Sätze bei 70 %: der Körper holt nach.", "Fewer sets at 70 %: your body catches up.", -1, 0, 0.7),
        ;

        val label get() = L(de, en)
        val blurb get() = L(deBlurb, enBlurb)
    }

    /** Phase order: 4 weeks Base, Build, Peak, Deload; 6 weeks repeat Base and Build. */
    fun phases(weeks: Int): List<Phase> =
        if (weeks >= 6) listOf(Phase.Base, Phase.Base, Phase.Build, Phase.Build, Phase.Peak, Phase.Deload)
        else listOf(Phase.Base, Phase.Build, Phase.Peak, Phase.Deload)

    data class BlockWeek(val week: Int, val weeks: Int, val phase: Phase)

    /** The current week of the running block, or null when there is none or it is over. */
    fun blockWeek(d: AppData, now: Long = System.currentTimeMillis()): BlockWeek? {
        val start = d.settings.blockStart.takeIf { it > 0 } ?: return null
        val week = ((now - start) / (7 * Stats.DAY_MS)).toInt()
        val plan = phases(d.settings.blockWeeks)
        return plan.getOrNull(week)?.let { BlockWeek(week + 1, plan.size, it) }
    }

    /** Whether a block was started and has run its course. */
    fun blockFinished(d: AppData) = d.settings.blockStart > 0 && blockWeek(d) == null

    /** A plan entry as this week of the block wants it. */
    fun applyBlock(d: AppData, p: PlanExercise): PlanExercise {
        val phase = blockWeek(d)?.phase ?: return p
        val sets = if (phase == Phase.Deload) (p.sets * 0.6).roundToInt().coerceAtLeast(1) else p.sets + phase.extraSets
        val reps = (p.reps + phase.extraReps).coerceAtLeast(3)
        return p.copy(sets = sets.coerceIn(1, 10), reps = reps)
    }

    /** A running workout untouched this long is treated as forgotten and closed. */
    const val IDLE_LIMIT_MS = 3 * 60 * 60_000L

    // --- Cycle ---------------------------------------------------------------------------

    /** Shown under every cycle hint. */
    val cycleDisclaimer get() = L("Richtwerte, keine medizinische Beratung.", "Guidance, not medical advice.")

    /**
     * One practical training line per phase. Kept general on purpose: how people feel across the
     * cycle varies a lot, so these are options, never rules.
     */
    fun cycleTraining(phase: Cycle.Phase): String = when (phase) {
        Cycle.Phase.Menstrual -> L(
            "Trainiere nach Gefühl. Leichtere oder kürzere Einheiten sind völlig in Ordnung, wenn dir danach ist.",
            "Train by feel. Lighter or shorter sessions are fine if you need them.",
        )
        Cycle.Phase.Follicular -> L(
            "Gutes Fenster für schwere Sätze und neue Rekorde.",
            "A good window for heavy sets and new records.",
        )
        Cycle.Phase.Ovulation -> L(
            "Viele fühlen sich jetzt kräftig. Gründlich aufwärmen und bei Sprüngen und Richtungswechseln auf saubere Technik achten.",
            "Many feel strong now. Warm up well and keep clean technique on jumps and changes of direction.",
        )
        Cycle.Phase.Luteal -> L(
            "Höhere Körpertemperatur und Müdigkeit sind häufig: längere Pausen sind okay, viel trinken, etwas mehr Kohlenhydrate sind in Ordnung.",
            "Higher body temperature and fatigue are common: allow longer rests, keep hydrated, a little more carbohydrate is fine.",
        )
    }

    /** A matching food line, e.g. for the nutrition screen; null when cycle training is off or out of date. */
    fun cycleNutrition(c: CycleSettings, today: java.time.LocalDate = java.time.LocalDate.now()): String? {
        val s = Cycle.status(c, today)?.takeIf { !it.stale } ?: return null
        return when (s.phase) {
            Cycle.Phase.Menstrual -> L(
                "Zyklus: Eisenreiches wie Hülsenfrüchte, Fleisch oder Haferflocken und genug Flüssigkeit tun jetzt gut.",
                "Cycle: iron-rich foods such as legumes, meat or oats and enough fluids are helpful now.",
            )
            Cycle.Phase.Follicular -> L(
                "Zyklus: Kohlenhydrate rund ums Training unterstützen schwere Einheiten.",
                "Cycle: carbohydrates around training support heavy sessions.",
            )
            Cycle.Phase.Ovulation -> L(
                "Zyklus: ausgewogen essen und genug trinken, mehr braucht es nicht.",
                "Cycle: eat balanced and drink enough, nothing more is needed.",
            )
            Cycle.Phase.Luteal -> L(
                "Zyklus: Der Energiebedarf kann leicht steigen. Etwas mehr Kohlenhydrate und genug Wasser sind in Ordnung.",
                "Cycle: energy needs can rise slightly. A little more carbohydrate and enough water are fine.",
            )
        }
    }

    // --- Cardio ------------------------------------------------------------------------------

    /** Cardio counts against the legs for this long after it ends. */
    private const val LEG_FATIGUE_MS = 48 * 3_600_000L

    /**
     * The latest run or ride of the last 48 h that was hard or long (see [Cardio.isHard] and
     * [Cardio.LONG_MINUTES]); easy short sessions leave the legs alone.
     */
    fun legLoad(cardio: List<CardioSession>, now: Long = System.currentTimeMillis()): CardioSession? =
        cardio.filter { Cardio.loadsLegs(it) && (Cardio.isHard(it) || it.minutes >= Cardio.LONG_MINUTES) }
            .filter { now - Cardio.end(it) in 0 until LEG_FATIGUE_MS }
            .maxByOrNull { it.at }

    /**
     * [recovery] from [Progress.recovery] with cardio taken into account: after a heavy run or
     * ride the legs start at 30 to 50 % (lower when hard and long) and recover linearly over 48 h.
     * The lower of the two values wins, so a leg day just before still counts.
     */
    fun withCardio(recovery: Map<String, Float>, cardio: List<CardioSession>, now: Long = System.currentTimeMillis()): Map<String, Float> {
        val s = legLoad(cardio, now) ?: return recovery
        val legs = Progress.groups.entries.firstOrNull { "quads" in it.value }?.key ?: return recovery
        val floor = if (Cardio.isHard(s) && s.minutes >= Cardio.LONG_MINUTES) 0.3f else 0.5f
        val cap = (floor + (1 - floor) * (now - Cardio.end(s)) / LEG_FATIGUE_MS.toFloat()).coerceIn(0f, 1f)
        return recovery.mapValues { (group, r) -> if (group == legs) minOf(r, cap) else r }
    }

    /** After a heavy run or ride: suggest the upper body or the short version today. */
    private fun cardioTip(d: AppData, now: Long): Tip? {
        val s = legLoad(d.cardio, now) ?: return null
        // A strength session after the cardio already settled the day.
        if (d.history.firstOrNull()?.let { it.startedAt > Cardio.end(s) } == true) return null
        val days = Stats.day(now).toEpochDay() - Stats.day(s.at).toEpochDay()
        val whenDe = when (days) { 0L -> "Vorhin"; 1L -> "Gestern"; else -> "Vorgestern" }
        val whenEn = when (days) { 0L -> "Earlier today"; 1L -> "Yesterday"; else -> "Two days ago" }
        val amount = s.distanceKm?.takeIf { it > 0 }?.let(Cardio::fmtKm) ?: Cardio.fmtMinutes(s.minutes)
        val run = s.kind == CardioKind.Run
        return Tip(
            L("Beine schonen", "Go easy on the legs"),
            L(
                "$whenDe $amount ${if (run) "gelaufen" else "Rad gefahren"}: heute Oberkörper oder die Kurzversion.",
                "$whenEn you ${if (run) "ran" else "rode"} $amount: upper body today, or the short version.",
            ),
            Tone.Rest,
        )
    }

    // --- Intro: focus, limits and session length -------------------------------------------

    /** False when [e] strains one of the joints in [limits] ([Limit] keys). */
    fun spares(e: Exercise, limits: Set<String>): Boolean = limits.none { key -> Limit.of(key)?.hits(e) == true }

    /** Target muscles of the profile's focus areas. */
    fun focusTargets(p: Profile): Set<String> = p.focus.mapNotNull(Focus::of).flatMap { it.targets }.toSet()

    /** Minutes a list of plan exercises takes, the same estimate the plan cards show. */
    fun minutes(exercises: List<PlanExercise>): Double = exercises.sumOf { it.sets * (40 + it.restSec) } / 60.0

    private class Slot(var ex: PlanExercise, val main: Boolean, val focus: Boolean)

    private const val MIN_EXERCISES = 3
    private const val MAX_EXERCISES = 9
    private const val MAX_SETS = 5

    /**
     * Grows or shrinks a day to about [target] minutes. Longer: more exercises from [more], then a
     * set more on the accessories. Shorter: accessories go first (focus kept longest), then sets,
     * the main lifts last. A step is only taken when it does not overshoot the target.
     */
    private fun fitToMinutes(slots: MutableList<Slot>, target: Int, more: () -> Slot?) {
        val slack = 3.0
        fun total() = minutes(slots.map { it.ex })
        fun cost(ex: PlanExercise) = ex.sets * (40 + ex.restSec) / 60.0
        while (slots.size < MAX_EXERCISES && total() < target - slack) {
            val next = more() ?: break
            if (total() + cost(next.ex) > target + slack) break
            slots += next
        }
        while (total() < target - slack) {
            val s = slots.filter { it.ex.sets < MAX_SETS }.minWithOrNull(compareBy<Slot> { it.main }.thenBy { it.ex.sets }) ?: break
            if (total() + cost(s.ex.copy(sets = 1)) > target + slack) break
            s.ex = s.ex.copy(sets = s.ex.sets + 1)
        }
        while (total() > target + slack && slots.size > MIN_EXERCISES) {
            val i = slots.indexOfLast { !it.main && !it.focus }.takeIf { it >= 0 } ?: slots.indexOfLast { !it.main }.takeIf { it >= 0 } ?: break
            slots.removeAt(i)
        }
        while (total() > target + slack) {
            val s = slots.filter { it.ex.sets > 2 }.maxWithOrNull(compareBy<Slot> { !it.main }.thenBy { it.ex.sets }) ?: break
            s.ex = s.ex.copy(sets = s.ex.sets - 1)
        }
    }
}
