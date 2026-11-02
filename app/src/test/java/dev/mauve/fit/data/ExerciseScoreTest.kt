package dev.mauve.fit.data

import org.junit.Assert.assertTrue
import org.junit.Test

/** The generator should pick the plain, well-known movement over hybrids and odd variants. */
class ExerciseScoreTest {
    private fun ex(name: String, equipment: String) = Exercise(name, name, equipment = equipment, target = "t")

    private fun assertBetter(plain: Exercise, odd: Exercise, main: Boolean) {
        val a = Coach.score(plain, main = main)
        val b = Coach.score(odd, main = main)
        assertTrue("${plain.name} ($a) should beat ${odd.name} ($b)", a > b)
    }

    @Test fun plainRowBeatsSquatRowHybrid() =
        assertBetter(ex("cable seated row", "cable"), ex("cable squat row (with rope attachment)", "cable"), main = true)

    @Test fun plainCurlBeatsSquattingCurl() =
        assertBetter(ex("dumbbell biceps curl", "dumbbell"), ex("cable squatting curl", "cable"), main = false)

    // "0025" is the dataset's barbell bench press, one of the staples.
    @Test fun stapleBeatsAnOddVariant() =
        assertBetter(Exercise("0025", "barbell bench press", equipment = "barbell", target = "t"), ex("barbell pin presses", "barbell"), main = true)
}
