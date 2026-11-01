package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {
    @Test fun unitsDecideWhatANumberMeans() {
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("60 Kilo 8 Wiederholungen"))
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("8 reps 60 kg"))
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("8 reps at 60"))
    }

    @Test fun timesFormat() {
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("60 mal 8"))
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("60x8"))
        assertEquals(VoiceInput.Parsed(62.5, 6), VoiceInput.parse("62,5 x 6"))
    }

    @Test fun numberWordsAndBareNumbers() {
        assertEquals(VoiceInput.Parsed(60.0, 8), VoiceInput.parse("sechzig acht"))
        assertEquals(VoiceInput.Parsed(40.0, 10), VoiceInput.parse("40 10"))
    }

    @Test fun bodyWeightLoneNumberIsReps() {
        assertEquals(VoiceInput.Parsed(null, 12), VoiceInput.parse("12", bodyweight = true))
    }

    @Test fun doneWordTicksTheSet() {
        assertTrue(VoiceInput.parse("fertig").done)
        val p = VoiceInput.parse("60 kg 8 done")
        assertEquals(60.0, p.weight!!, 1e-9)
        assertEquals(8, p.reps)
        assertTrue(p.done)
    }

    @Test fun nonsenseIsEmpty() {
        assertTrue(VoiceInput.parse("hallo wie geht's").empty)
    }
}
