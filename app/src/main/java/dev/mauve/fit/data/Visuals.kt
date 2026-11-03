package dev.mauve.fit.data

import androidx.compose.ui.graphics.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class BodySide { FRONT, BACK }

/**
 * The body outline and muscle regions from react-native-body-highlighter (MIT, see
 * THIRD_PARTY_NOTICES.md), converted once into plain move/line/curve commands in bodymap.json,
 * which the iOS app reads too. Both sides share one coordinate space of [width] x [height].
 */
class BodyMap(val width: Float, val height: Float, val front: Side, val back: Side) {
    class Side(val outline: Path, val parts: Map<String, Path>)

    operator fun get(side: BodySide) = if (side == BodySide.FRONT) front else back

    @Serializable
    private class RawSide(val outline: String, val parts: Map<String, List<String>>)

    @Serializable
    private class Raw(val viewBox: List<Float>, val front: RawSide, val back: RawSide)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BodyMap {
            val raw = json.decodeFromString<Raw>(text)
            fun side(s: RawSide) = Side(
                path(s.outline),
                s.parts.mapValues { (_, list) -> Path().apply { list.forEach { addPath(path(it)) } } },
            )
            return BodyMap(raw.viewBox[0], raw.viewBox[1], side(raw.front), side(raw.back))
        }

        /** Reads "M x y L x y C x1 y1 x2 y2 x y Z", the only commands the converter writes. */
        fun path(commands: String): Path {
            val p = Path()
            val t = commands.split(' ').filter { it.isNotEmpty() }
            var i = 0
            fun num(): Float = (t.getOrNull(i++)?.toFloatOrNull() ?: 0f)
            while (i < t.size) {
                when (t[i++]) {
                    "M" -> p.moveTo(num(), num())
                    "L" -> p.lineTo(num(), num())
                    "C" -> p.cubicTo(num(), num(), num(), num(), num(), num())
                    "Z" -> p.close()
                }
            }
            return p
        }
    }
}

/** Which map regions a muscle of the exercise data covers. */
object MuscleRegions {
    /**
     * Every muscle name used in exercises.json, as targets or secondary muscles. An empty list
     * means "nothing to highlight" on purpose (cardio). A unit test keeps this complete.
     */
    val map: Map<String, List<String>> = mapOf(
        "abs" to listOf("abs"), "abdominals" to listOf("abs"), "lower abs" to listOf("abs"), "core" to listOf("abs", "obliques"),
        "obliques" to listOf("obliques"),
        "quads" to listOf("quadriceps"), "quadriceps" to listOf("quadriceps"), "hip flexors" to listOf("quadriceps"),
        "lats" to listOf("upper-back"), "latissimus dorsi" to listOf("upper-back"), "upper back" to listOf("upper-back"),
        "back" to listOf("upper-back"), "rhomboids" to listOf("upper-back"),
        "spine" to listOf("lower-back"), "lower back" to listOf("lower-back"),
        "calves" to listOf("calves"), "soleus" to listOf("calves"), "shins" to listOf("tibialis"),
        "pectorals" to listOf("chest"), "chest" to listOf("chest"), "upper chest" to listOf("chest"), "serratus anterior" to listOf("chest"),
        "glutes" to listOf("gluteal"), "abductors" to listOf("gluteal"),
        "hamstrings" to listOf("hamstring"),
        "adductors" to listOf("adductors"), "inner thighs" to listOf("adductors"), "groin" to listOf("adductors"),
        "triceps" to listOf("triceps"),
        "biceps" to listOf("biceps"), "brachialis" to listOf("biceps"),
        "delts" to listOf("deltoids"), "shoulders" to listOf("deltoids"), "deltoids" to listOf("deltoids"), "rear deltoids" to listOf("deltoids"),
        "rotator cuff" to listOf("deltoids"),
        "forearms" to listOf("forearm"), "wrists" to listOf("forearm"), "wrist flexors" to listOf("forearm"), "wrist extensors" to listOf("forearm"),
        "grip muscles" to listOf("forearm"),
        "traps" to listOf("trapezius"), "trapezius" to listOf("trapezius"),
        "levator scapulae" to listOf("neck"), "sternocleidomastoid" to listOf("neck"),
        "ankles" to listOf("ankles"), "ankle stabilizers" to listOf("ankles"), "feet" to listOf("feet"), "hands" to listOf("hands"),
        "cardiovascular system" to emptyList(),
    )

    /** Regions drawn only on the back of the figure. */
    private val backOnly = setOf("upper-back", "lower-back", "gluteal", "hamstring")
    /** Regions on both sides that read better from behind. */
    private val backFirst = setOf("triceps", "trapezius", "calves")

    fun regions(muscles: List<String>): Set<String> = muscles.flatMap { map[it].orEmpty() }.toSet()

    /** The side that shows what the exercise mainly trains; the back for back-only regions. */
    fun side(e: Exercise): BodySide {
        val target = regions(listOf(e.target))
        val back = target.any { it in backOnly } || (target.isNotEmpty() && target.all { it in backFirst || it in backOnly })
        return if (back) BodySide.BACK else BodySide.FRONT
    }
}

/**
 * Line drawings from Everkinetic (github.com/everkinetic/data, CC BY-SA 4.0), bundled with the
 * app: a start and an end position for the exercises everkinetic.json matches by hand. Scaled
 * to 640 px and reduced to four grey levels to keep the app small.
 */
object Drawings {
    const val LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
    const val SOURCE_URL = "https://github.com/everkinetic/data"

    @Serializable
    private class Item(val id: String)

    @Serializable
    private class Match(val images: List<String>, val exercises: List<Item>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Exercise id to the drawings' asset paths, start position first. */
    fun parse(text: String): Map<String, List<String>> {
        val out = HashMap<String, List<String>>()
        for (m in json.decodeFromString<List<Match>>(text)) {
            // "relaxation" is the start position, "tension" the end.
            val files = m.images.sortedBy { !it.contains("relaxation") }.map { "Everkinetic/ek-$it" }
            m.exercises.forEach { out[it.id] = files }
        }
        return out
    }
}
