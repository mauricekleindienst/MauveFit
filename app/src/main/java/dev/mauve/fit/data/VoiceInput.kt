package dev.mauve.fit.data

/**
 * Reads a spoken set like "60 Kilo 8 Wiederholungen", "sechzig mal acht", "8 reps at 60" or "fertig".
 * Units decide what a number means; without units the first number is the weight and the second
 * the reps (for body-weight exercises a lone number is the reps).
 */
object VoiceInput {
    data class Parsed(val weight: Double? = null, val reps: Int? = null, val done: Boolean = false) {
        val empty get() = weight == null && reps == null && !done
    }

    private val weightUnits = setOf("kg", "kilo", "kilos", "kilogramm", "kilogram", "kilograms")
    private val repUnits = setOf("wdh", "wiederholung", "wiederholungen", "rep", "reps", "repetitions", "times", "mal", "x")
    private val doneWords = setOf("fertig", "erledigt", "geschafft", "done", "finished", "check")

    private val words = mapOf(
        "null" to 0, "zero" to 0, "ein" to 1, "eins" to 1, "eine" to 1, "one" to 1, "zwei" to 2, "two" to 2, "drei" to 3, "three" to 3,
        "vier" to 4, "four" to 4, "fünf" to 5, "five" to 5, "sechs" to 6, "six" to 6, "sieben" to 7, "seven" to 7, "acht" to 8, "eight" to 8,
        "neun" to 9, "nine" to 9, "zehn" to 10, "ten" to 10, "elf" to 11, "eleven" to 11, "zwölf" to 12, "twelve" to 12,
        "fünfzehn" to 15, "fifteen" to 15, "zwanzig" to 20, "twenty" to 20, "dreißig" to 30, "thirty" to 30, "vierzig" to 40, "forty" to 40,
        "fünfzig" to 50, "fifty" to 50, "sechzig" to 60, "sixty" to 60, "siebzig" to 70, "seventy" to 70, "achtzig" to 80, "eighty" to 80,
        "neunzig" to 90, "ninety" to 90, "hundert" to 100, "hundred" to 100,
    )

    fun parse(text: String, bodyweight: Boolean = false): Parsed {
        // "60,5" and "60.5" both mean sixty and a half; "60x8" is split into "60 x 8".
        val tokens = text.lowercase()
            .replace(Regex("(\\d),(\\d)"), "$1.$2")
            .replace(Regex("(\\d)(x|kg)"), "$1 $2")
            .replace(Regex("x(\\d)"), "x $1")
            .split(Regex("[^\\p{L}\\p{Nd}.]+")).filter { it.isNotBlank() }
        var weight: Double? = null
        var reps: Int? = null
        val loose = mutableListOf<Double>()
        tokens.forEachIndexed { i, t ->
            val n = t.toDoubleOrNull() ?: words[t]?.toDouble() ?: return@forEachIndexed
            val next = tokens.getOrNull(i + 1)
            val prev = tokens.getOrNull(i - 1)
            when {
                next in weightUnits || prev == "at" || prev == "mit" -> weight = n
                next in repUnits && next != "x" && next != "mal" -> reps = n.toInt()
                // "60 x 8" / "60 mal 8": the number before x is the weight, the one after it the reps.
                // ...but only when a number follows: "acht mal" alone is reps, not 8 kg.
                (next == "x" || next == "mal") && tokens.drop(i + 2).any { it.toDoubleOrNull() != null || words[it] != null } ->
                    if (weight == null && !bodyweight) weight = n else loose += n
                next == "x" || next == "mal" -> reps = n.toInt()
                prev == "x" || prev == "mal" -> reps = n.toInt()
                else -> loose += n
            }
        }
        loose.forEach { n ->
            when {
                bodyweight && reps == null -> reps = n.toInt()
                weight == null && !bodyweight -> weight = n
                reps == null -> reps = n.toInt()
            }
        }
        return Parsed(weight?.takeIf { it in 0.0..1000.0 }, reps?.takeIf { it in 0..200 }, tokens.any { it in doneWords })
    }
}
