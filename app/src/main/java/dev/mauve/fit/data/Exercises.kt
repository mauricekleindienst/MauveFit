package dev.mauve.fit.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One entry of exercises-dataset (github.com/hasaneyldrm/exercises-dataset), slimmed to the
 * fields the app uses. Animations © Gym visual (gymvisual.com), loaded from the repository and cached.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val category: String = "",
    val equipment: String = "",
    val target: String = "",
    val secondary: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val gif: String = "",
    val image: String = "",
) {
    /** Display name in the app's language; [name] stays the English key the coach matches words in. */
    val title: String get() = (if (I18n.german) ExerciseRepo.namesDe[id] else null) ?: titleEn
    val titleEn: String get() = name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    /** Original animation and still, served by the jsDelivr CDN straight from the GitHub repository. */
    val gifUrl: String get() = MEDIA_BASE + gif
    val imageUrl: String get() = MEDIA_BASE + image
    /** The same files from raw.githubusercontent.com, used if the CDN is unreachable. */
    val gifFallback: String get() = FALLBACK_BASE + gif
    val imageFallback: String get() = FALLBACK_BASE + image
    /** Body-weight exercises are logged by reps only. */
    val bodyweight: Boolean get() = equipment == "body weight"
    val muscles: List<String> get() = listOf(target) + secondary

    companion object {
        const val MEDIA_BASE = "https://cdn.jsdelivr.net/gh/hasaneyldrm/exercises-dataset@main/"
        const val FALLBACK_BASE = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/"
        // Wording the media notice of exercises-dataset asks for on every use.
        const val ATTRIBUTION = "© Gym visual — https://gymvisual.com/"
    }
}

/** The bundled database, parsed once (warmed up off the main thread at app start). */
class ExerciseRepo(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<Exercise> by lazy {
        // The German names come with the list: a widget drawn by a freshly started process must
        // not read the titles before they are there and show English ones.
        loadNames()
        context.assets.open("exercises.json").bufferedReader().use { json.decodeFromString<List<Exercise>>(it.readText()) }
            .sortedBy { it.name }
    }
    private val byId by lazy { all.associateBy { it.id } }

    operator fun get(id: String): Exercise? = byId[id]

    /** German how-to steps by exercise id (our own translation; the dataset has none). */
    private val stepsDe: Map<String, List<String>> by lazy {
        runCatching {
            context.assets.open("exercises_de.json").bufferedReader().use { json.decodeFromString<Map<String, List<String>>>(it.readText()) }
        }.getOrDefault(emptyMap())
    }

    /** The how-to in the app's language, falling back to English. */
    fun steps(e: Exercise): List<String> =
        if (I18n.german) stepsDe[e.id]?.takeIf { it.size == e.steps.size } ?: e.steps else e.steps

    /** Loads everything up front, off the main thread. */
    fun warm() {
        all.size
        loadNames()
        if (I18n.german) stepsDe.size
    }

    private fun loadNames() {
        if (namesDe.isNotEmpty()) return
        namesDe = runCatching {
            context.assets.open("exercise_names_de.json").bufferedReader().use { json.decodeFromString<Map<String, String>>(it.readText()) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        /** German display names by id (our own translation), read once by [warm]. */
        @Volatile
        var namesDe: Map<String, String> = emptyMap()
            private set
    }

    val categories: List<String> by lazy { all.map { it.category }.distinct().sortedBy { Labels.category(it) } }
    val equipment: List<String> by lazy {
        // Most-used equipment first; the long tail of single machines would only clutter the filter.
        all.groupingBy { it.equipment }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key }
    }
}

/** Display names for the dataset's English vocabulary, in German or English. */
object Labels {
    private val muscles = mapOf(
        "abductors" to "Abduktoren", "abs" to "Bauch", "adductors" to "Adduktoren", "biceps" to "Bizeps",
        "calves" to "Waden", "cardiovascular system" to "Ausdauer", "delts" to "Schultern", "forearms" to "Unterarme",
        "glutes" to "Gesäß", "hamstrings" to "Beinbeuger", "lats" to "Latissimus", "levator scapulae" to "Nacken",
        "pectorals" to "Brust", "quads" to "Quadrizeps", "serratus anterior" to "Sägezahnmuskel", "spine" to "Rückenstrecker",
        "traps" to "Trapez", "triceps" to "Trizeps", "upper back" to "Oberer Rücken", "lower back" to "Unterer Rücken",
        "hip flexors" to "Hüftbeuger", "obliques" to "Seitl. Bauch", "core" to "Rumpf", "shoulders" to "Schultern",
        "chest" to "Brust", "quadriceps" to "Quadrizeps", "rhomboids" to "Rhomboiden", "rotator cuff" to "Rotatoren",
        "deltoids" to "Schultern", "trapezius" to "Trapez", "wrists" to "Handgelenke", "ankles" to "Sprunggelenke",
        "soleus" to "Waden", "latissimus dorsi" to "Latissimus", "rear deltoids" to "Hintere Schulter",
    )
    private val categories = mapOf(
        "back" to "Rücken", "cardio" to "Cardio", "chest" to "Brust", "lower arms" to "Unterarme",
        "lower legs" to "Unterschenkel", "neck" to "Nacken", "shoulders" to "Schultern", "upper arms" to "Arme",
        "upper legs" to "Beine", "waist" to "Bauch",
    )
    private val equipment = mapOf(
        "body weight" to "Körpergewicht", "dumbbell" to "Kurzhantel", "barbell" to "Langhantel", "cable" to "Kabelzug",
        "leverage machine" to "Maschine", "band" to "Band", "resistance band" to "Widerstandsband", "smith machine" to "Multipresse",
        "kettlebell" to "Kettlebell", "weighted" to "Zusatzgewicht", "stability ball" to "Gymnastikball", "ez barbell" to "SZ-Stange",
        "sled machine" to "Beinpresse", "assisted" to "Assistiert", "medicine ball" to "Medizinball", "olympic barbell" to "Olympiastange",
        "trap bar" to "Trap Bar", "rope" to "Seil", "roller" to "Roller", "bosu ball" to "Bosu",
    )

    // English: the dataset's own words, with a few made friendlier.
    private val musclesEn = mapOf(
        "abs" to "Abs", "cardiovascular system" to "Cardio", "delts" to "Shoulders", "pectorals" to "Chest",
        "quads" to "Quads", "lats" to "Lats", "spine" to "Lower back", "levator scapulae" to "Neck",
        "serratus anterior" to "Serratus", "upper back" to "Upper back",
    )
    private val categoriesEn = mapOf(
        "upper arms" to "Arms", "upper legs" to "Legs", "lower arms" to "Forearms", "lower legs" to "Calves", "waist" to "Core",
    )
    private val equipmentEn = mapOf("leverage machine" to "Machine", "sled machine" to "Leg press", "ez barbell" to "EZ bar")

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    fun muscle(m: String) = if (I18n.german) muscles[m] ?: cap(m) else musclesEn[m] ?: cap(m)
    fun category(c: String) = if (I18n.german) categories[c] ?: cap(c) else categoriesEn[c] ?: cap(c)
    fun equipment(e: String) = if (I18n.german) equipment[e] ?: cap(e) else equipmentEn[e] ?: cap(e)
}
