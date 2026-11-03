package dev.mauve.fit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisualsTest {
    private val regions = MuscleRegions.parse(File("../ios/MauveFit/Resources/muscle_regions.json").readText())
    private val exercises = Json.parseToJsonElement(File("src/main/assets/exercises.json").readText()).jsonArray.map { it.jsonObject }

    @Test
    fun everyMuscleHasARegion() {
        val muscles = exercises.flatMap { e ->
            listOf(e["target"]!!.jsonPrimitive.content) + (e["secondary"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
        }.toSet()
        val missing = muscles - regions.map.keys
        assertTrue("No map region for $missing", missing.isEmpty())
    }

    @Test
    fun drawingsPointAtBundledFiles() {
        val drawings = Drawings.parse(File("../ios/MauveFit/Resources/everkinetic.json").readText())
        val ids = exercises.map { it["id"]!!.jsonPrimitive.content }.toSet()
        assertTrue(drawings.isNotEmpty())
        drawings.forEach { (id, files) ->
            assertTrue("unknown exercise $id", id in ids)
            if (files.any { "relaxation" in it }) assertTrue(files.first().contains("relaxation"))
            files.forEach { assertTrue("missing $it", File("../ios/MauveFit/Resources/$it").exists()) }
        }
    }

    @Test
    fun backMusclesShowFromBehind() {
        val e = Exercise("x", "row", target = "lats")
        assertEquals(BodySide.BACK, regions.side(e))
        assertEquals(BodySide.FRONT, regions.side(e.copy(target = "pectorals")))
    }
}
