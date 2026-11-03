package dev.mauve.fit.data

import androidx.compose.ui.graphics.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class BodySide { FRONT, BACK }

/**
 * The body outline and muscle regions from react-native-body-highlighter (MIT, see
 * THIRD_PARTY_NOTICES.md at the repository root), converted once into plain move/line/curve commands in bodymap.json,
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

/**
 * Which map regions a muscle of the exercise data covers, from muscle_regions.json, which the iOS
 * app reads too. An empty list means "nothing to highlight" on purpose (cardio). A unit test keeps
 * it complete for every muscle in exercises.json.
 */
class MuscleRegions(
    val map: Map<String, List<String>>,
    /** Regions drawn only on the back of the figure. */
    private val backOnly: Set<String>,
    /** Regions on both sides that read better from behind. */
    private val backFirst: Set<String>,
) {
    fun regions(muscles: List<String>): Set<String> = muscles.flatMap { map[it].orEmpty() }.toSet()

    /** The side that shows what the exercise mainly trains; the back for back-only regions. */
    fun side(e: Exercise): BodySide {
        val target = regions(listOf(e.target))
        val back = target.any { it in backOnly } || (target.isNotEmpty() && target.all { it in backFirst || it in backOnly })
        return if (back) BodySide.BACK else BodySide.FRONT
    }

    @Serializable
    private class Raw(val regions: Map<String, List<String>>, val backOnly: List<String>, val backFirst: List<String>)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String) = json.decodeFromString<Raw>(text).let { MuscleRegions(it.regions, it.backOnly.toSet(), it.backFirst.toSet()) }
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
