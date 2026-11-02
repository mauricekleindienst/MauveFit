package dev.mauve.fit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** One exercise slot in a plan: what to aim for each time it is trained. */
@Serializable
data class PlanExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val restSec: Int = 90,
    /** Consecutive exercises sharing this id form a superset and are trained alternately. */
    val superset: String? = null,
)

@Serializable
data class Routine(
    val id: String = newId(),
    val name: String,
    val note: String = "",
    val exercises: List<PlanExercise> = emptyList(),
)

@Serializable
data class SetEntry(
    val weight: Double = 0.0,
    val reps: Int = 0,
    val done: Boolean = false,
    /** Warm-up sets are logged but left out of volume and records. */
    val warmup: Boolean = false,
    /** Reps the plan asked for (0 = unknown); the coach compares it with what was done. */
    val target: Int = 0,
    /** Drop set or to failure; warm-ups keep using [warmup]. */
    val type: SetType = SetType.Normal,
    /** Rate of perceived exertion, 6 to 10 (0 = not rated). */
    val rpe: Int = 0,
)

/** A ticked-off set as it was logged; [number] counts working sets from 1, 0 for a warm-up. */
@Serializable
data class LastSet(val exerciseId: String, val weight: Double, val reps: Int, val number: Int = 0, val at: Long = 0)

@Serializable
enum class SetType { Normal, Drop, Failure }

@Serializable
data class ExerciseEntry(
    val exerciseId: String,
    val restSec: Int = 90,
    val sets: List<SetEntry> = listOf(SetEntry()),
    /** Superset group taken over from the plan, see [PlanExercise.superset]. */
    val superset: String? = null,
    /**
     * Factor the suggested weight was scaled with (light week, block phase, readiness), so the
     * next suggestion can progress from the unscaled weight instead of keeping the cut.
     */
    val load: Double = 1.0,
)

/** A workout in progress (AppData.active) or finished (AppData.history). */
@Serializable
data class Workout(
    val id: String = newId(),
    val name: String,
    val routineId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val entries: List<ExerciseEntry> = emptyList(),
    /** Wall-clock time the running rest timer ends, kept here so it survives the app being killed. */
    val restEndsAt: Long? = null,
    val restTotalSec: Int = 0,
    /** How it felt (0 = not rated, 1 easy, 2 good, 3 hard) and a free note, set on the summary. */
    val feel: Int = 0,
    val note: String = "",
    /** Last time anything changed in the running workout; 0 for workouts saved before it existed. */
    val lastActivityAt: Long = 0,
    /** Percent the starting weights were lowered for poor recovery (0 = not adjusted), and the score behind it. */
    val readinessCut: Int = 0,
    val readinessScore: Int = 0,
    /** The set ticked off last, for the notification and the widget ("Set 2 done · 80 kg × 8"). */
    val lastSet: LastSet? = null,
) {
    /** When the workout was last touched, falling back to its start. */
    val lastActive: Long get() = lastActivityAt.takeIf { it > 0 } ?: startedAt

    /** Completed working sets (warm-ups excluded). */
    val doneSets: List<Pair<String, SetEntry>>
        get() = entries.flatMap { e -> e.sets.filter { it.done && !it.warmup }.map { e.exerciseId to it } }
    val volume: Double get() = doneSets.sumOf { (_, s) -> s.weight * s.reps }
    val durationMs: Long get() = (finishedAt ?: System.currentTimeMillis()) - startedAt
}

@Serializable
enum class Goal(private val de: String, private val en: String, private val deBlurb: String, private val enBlurb: String) {
    Strength("Kraft", "Strength", "Schwerer heben, wenige Wiederholungen", "Lift heavier, fewer reps"),
    Muscle("Muskelaufbau", "Build muscle", "Mehr Muskelmasse, mittlere Wiederholungen", "More muscle, moderate reps"),
    Fitness("Fitness", "Fitness", "Fit und belastbar, zügige Einheiten", "Fit and resilient, brisk sessions"),
    FatLoss("Abnehmen", "Lose fat", "Viel Bewegung, kurze Pausen", "Lots of movement, short rests"),
    ;

    val label get() = L(de, en)
    val blurb get() = L(deBlurb, enBlurb)
}

@Serializable
enum class Level(private val de: String, private val en: String, private val deBlurb: String, private val enBlurb: String) {
    Beginner("Einsteiger", "Beginner", "Weniger als 6 Monate Training", "Less than 6 months of training"),
    Intermediate("Fortgeschritten", "Intermediate", "Regelmäßig seit über 6 Monaten", "Training regularly for 6+ months"),
    Advanced("Erfahren", "Advanced", "Mehrere Jahre strukturiertes Training", "Several years of structured training"),
    ;

    val label get() = L(de, en)
    val blurb get() = L(deBlurb, enBlurb)
}

@Serializable
enum class Setup(
    private val de: String, private val en: String, private val deBlurb: String, private val enBlurb: String,
    val equipment: Set<String>,
) {
    Gym("Fitnessstudio", "Gym", "Hanteln, Kabelzug und Maschinen", "Free weights, cables and machines", emptySet()),
    Dumbbells("Kurzhanteln", "Dumbbells", "Zuhause mit Kurzhanteln und Bändern", "At home with dumbbells and bands",
        setOf("body weight", "dumbbell", "kettlebell", "band", "resistance band", "stability ball")),
    Home("Ohne Geräte", "No equipment", "Nur Körpergewicht", "Body weight only",
        setOf("body weight")),
    ;

    val label get() = L(de, en)
    val blurb get() = L(deBlurb, enBlurb)

    /** Empty set means everything is available. */
    fun allows(equipment: String) = this.equipment.isEmpty() || equipment in this.equipment
}

/**
 * Equipment groups for the user's preference, e.g. "machines only" or "no body-weight moves like
 * pull-ups". Equipment outside every group (cardio machines, tyres...) counts as off once the user
 * has narrowed the choice.
 */
enum class Kit(val key: String, private val de: String, private val en: String, val equipment: Set<String>) {
    Machines("machines", "Maschinen", "Machines", setOf("leverage machine", "sled machine", "smith machine", "assisted")),
    Cables("cables", "Kabelzug", "Cables", setOf("cable", "rope")),
    Dumbbells("dumbbells", "Kurzhanteln", "Dumbbells", setOf("dumbbell", "kettlebell")),
    Barbells("barbells", "Langhanteln", "Barbells", setOf("barbell", "ez barbell", "olympic barbell", "trap bar")),
    Bodyweight("bodyweight", "Körpergewicht", "Body weight", setOf("body weight", "weighted", "stability ball", "bosu ball", "medicine ball", "roller", "wheel roller")),
    Bands("bands", "Bänder", "Bands", setOf("band", "resistance band")),
    ;

    val label get() = L(de, en)

    companion object {
        fun of(equipment: String) = entries.firstOrNull { equipment in it.equipment }
    }
}

@Serializable
data class Profile(
    val goal: Goal = Goal.Muscle,
    val level: Level = Level.Beginner,
    val days: Int = 3,
    val setup: Setup = Setup.Gym,
    val onboarded: Boolean = false,
    /** Kit keys the user wants to train with; empty means everything the setup allows. */
    val kit: Set<String> = emptySet(),
    /** Preferred session length in minutes (30, 45, 60, 75); 0 lets the goal decide. */
    val sessionMinutes: Int = 0,
    /** [Focus] keys of body areas that get extra work, and [Limit] keys of joints to spare. */
    val focus: Set<String> = emptySet(),
    val limits: Set<String> = emptySet(),
    /** Body data from the intro; 0 = not given. */
    val heightCm: Int = 0,
    val birthYear: Int = 0,
) {
    fun allows(equipment: String) = setup.allows(equipment) && (kit.isEmpty() || Kit.of(equipment)?.key in kit)
}

@Serializable
data class BodyWeight(val at: Long, val kg: Double)

/** A tape measurement in cm; [kind] is a key of Progress.measures (waist, chest, hips, arm, thigh). */
@Serializable
data class Measurement(val at: Long, val kind: String, val cm: Double)

@Serializable
data class Settings(
    val weeklyGoal: Int = 3,
    val defaultRest: Int = 90,
    val weightStep: Double = 2.5,
    val barWeight: Double = 20.0,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Workout reminder: weekdays 1 (Monday) to 7, at hour:minute. */
    val reminder: Boolean = false,
    val reminderDays: Set<Int> = setOf(1, 3, 5),
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    /** "system", "de" or "en". */
    val language: String = "system",
    /** "system", "dark" or "light". */
    val theme: String = "system",
    /** Accent key, see ui.Accent. */
    val accent: String = "copper",
    /** While now < deloadUntil, suggested weights are lowered for a lighter week. */
    val deloadUntil: Long = 0,
    /** Running training block: start (0 = none) and length in weeks (4 or 6). */
    val blockStart: Long = 0,
    val blockWeeks: Int = 4,
    /** Lower starting weights on days the watch reports poor recovery. */
    val autoRegulate: Boolean = true,
    /** Target body weight in kg; 0 = none. */
    val goalWeight: Double = 0.0,
    /** Monday-morning notification with last week's numbers. */
    val weeklyReport: Boolean = true,
    /** Write finished workouts and body weights to Health Connect. */
    val healthWrite: Boolean = false,
)

@Serializable
data class AppData(
    val routines: List<Routine> = Templates.routines,
    val history: List<Workout> = emptyList(),
    val active: Workout? = null,
    val favorites: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val bodyWeights: List<BodyWeight> = emptyList(),
    /** Meal-prep week: recipe id to portions, and ticked shopping items. */
    val mealPlan: Map<String, Int> = emptyMap(),
    val shoppingDone: Set<String> = emptySet(),
    /** Start times of Health Connect sessions deleted in the app, so an import skips them. */
    val cardioDismissed: List<Long> = emptyList(),
    /** Body measurements, newest first. */
    val measurements: List<Measurement> = emptyList(),
    /** Personal setup notes per exercise id (seat height, grip...). */
    val exerciseNotes: Map<String, String> = emptyMap(),
    /** Food diary and water per day (ISO date to ml), kept for about two months. */
    val food: List<FoodEntry> = emptyList(),
    val water: Map<String, Int> = emptyMap(),
    /** Recipes the user wrote; they work everywhere the built-in ones do. */
    val ownRecipes: List<Nutrition.Recipe> = emptyList(),
    /** Products scanned by barcode, newest first, so they can be logged again offline. */
    val scannedFoods: List<ScannedFood> = emptyList(),
    /** ISO weeks ("2026-W44") whose weekly challenge was completed; its size is the count shown. */
    val challengesDone: Set<String> = emptySet(),
    /** Streak freezes in stock (0 to 2) and the missed weeks they covered, as ISO week keys. */
    val freezes: Int = 0,
    val frozenWeeks: Set<String> = emptySet(),
    /** Last week the freeze bookkeeping settled ("" = never) and the goal weeks in a row up to it. */
    val streakSettled: String = "",
    val streakRun: Int = 0,
    /** Cardio sessions, logged by hand or taken over from the watch, newest first. */
    val cardio: List<CardioSession> = emptyList(),
)

/** One thing eaten on [date] (ISO yyyy-MM-dd); [recipeId] is set when it came from a recipe. */
@Serializable
data class FoodEntry(
    val id: String = newId(),
    val date: String,
    val name: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int = 0,
    val fat: Int = 0,
    val recipeId: String? = null,
)

/** Kind of cardio; the keys are what the backup stores. */
@Serializable
enum class CardioKind(private val de: String, private val en: String) {
    @SerialName("run") Run("Laufen", "Run"),
    @SerialName("bike") Bike("Rad", "Bike"),
    @SerialName("row") Row("Rudern", "Row"),
    @SerialName("walk") Walk("Gehen", "Walk"),
    @SerialName("swim") Swim("Schwimmen", "Swim"),
    @SerialName("other") Other("Sonstiges", "Other"),
    ;

    val label get() = L(de, en)
}

/**
 * One cardio session starting at [at]. [rpe] is effort on a 1 to 10 scale (null = not rated);
 * [source] is "manual" or "health" (imported from Health Connect). Cardio never counts toward
 * the weekly goal, which stays about strength workouts.
 */
@Serializable
data class CardioSession(
    val id: String = newId(),
    val at: Long = System.currentTimeMillis(),
    val kind: CardioKind = CardioKind.Run,
    val minutes: Int = 30,
    val distanceKm: Double? = null,
    val avgHr: Int? = null,
    val rpe: Int? = null,
    val source: String = "manual",
    val note: String = "",
)

/** Body areas the user can ask the coach to emphasise, mapped to the dataset's target muscles. */
enum class Focus(val key: String, private val de: String, private val en: String, val targets: List<String>, val sample: String) {
    Chest("chest", "Brust", "Chest", listOf("pectorals"), "0025"),
    Back("back", "Rücken", "Back", listOf("lats", "upper back"), "0861"),
    Shoulders("shoulders", "Schultern", "Shoulders", listOf("delts"), "0405"),
    Arms("arms", "Arme", "Arms", listOf("biceps", "triceps"), "0313"),
    Legs("legs", "Beine", "Legs", listOf("quads", "hamstrings", "calves"), "0585"),
    Glutes("glutes", "Po", "Glutes", listOf("glutes"), "3013"),
    Core("core", "Bauch", "Core", listOf("abs"), "0276"),
    ;

    val label get() = L(de, en)

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Joints to spare. The coach leaves out exercises whose name contains one of [words] (lower case,
 * as in the dataset), or that train one of [targets].
 */
enum class Limit(
    val key: String, private val de: String, private val en: String, private val deBlurb: String, private val enBlurb: String,
    val words: List<String>, val targets: Set<String> = emptySet(), val sample: String,
) {
    Knees("knees", "Knie", "Knees", "Keine Sprünge, Ausfallschritte oder Beinstrecker", "No jumps, lunges or leg extensions",
        listOf("jump", "lunge", "leg extension", "pistol", "sissy", "split squat", "step-up", "burpee", "hop", "skater"), sample = "1460"),
    LowerBack("back", "Unterer Rücken", "Lower back", "Kein Kreuzheben, Good Mornings oder Rückenstrecker", "No deadlifts, good mornings or back extensions",
        listOf("deadlift", "good morning", "back extension", "hyperextension", "superman", "bent over"), setOf("spine"), "0032"),
    Shoulders("shoulders", "Schultern", "Shoulders", "Kein Drücken über Kopf, Aufrechtes Rudern oder Dips", "No overhead pressing, upright rows or dips",
        listOf("overhead", "military", "shoulder press", "upright row", "dip", "behind neck", "handstand", "arnold press", "push press", "jerk", "snatch"), sample = "0405"),
    Wrists("wrists", "Handgelenke", "Wrists", "Keine Liegestütze oder Frontkniebeugen", "No push-ups or front squats",
        listOf("push-up", "push up", "front squat", "handstand", "wrist", "clean", "burpee"), sample = "0662"),
    ;

    val label get() = L(de, en)
    val blurb get() = L(deBlurb, enBlurb)

    /** True when [e] would stress this joint. */
    fun hits(e: Exercise): Boolean = e.target in targets || words.any { it in e.name } ||
        // Pressing for the shoulders goes overhead even when the name does not say so.
        (this == Shoulders && e.target == "delts" && "press" in e.name)

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}
