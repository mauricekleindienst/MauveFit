package dev.mauve.fit.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Opt-in cycle tracking. Kept apart from [AppData] on purpose: [Store] writes it to the no-backup
 * folder, so it never ends up in a backup, the JSON export or a device transfer. Off means nothing
 * is stored at all.
 */
@Serializable
data class CycleSettings(
    val on: Boolean = false,
    /** "manual" (date entered by hand) or "health" (read from Health Connect). */
    val source: String = "manual",
    /** First day of the last period, ISO yyyy-MM-dd; "" = not entered yet. */
    val lastStart: String = "",
    /** Usual cycle length in days, 21 to 40. */
    val length: Int = 28,
)

/** Pure phase arithmetic for [CycleSettings]; the coaching words live in [Coach]. */
object Cycle {
    const val MIN_LENGTH = 21
    const val MAX_LENGTH = 40
    const val DEFAULT_LENGTH = 28

    /** Days 1 to 5 count as the period itself. */
    private const val PERIOD_DAYS = 5

    enum class Phase(private val de: String, private val en: String) {
        Menstrual("Menstruation", "Menstruation"),
        Follicular("Follikelphase", "Follicular phase"),
        Ovulation("Eisprung-Fenster", "Ovulation window"),
        Luteal("Lutealphase", "Luteal phase"),
        ;

        val label get() = L(de, en)
    }

    /**
     * Where today falls: [day] of [length] (1-based) and its [phase]. [stale] means the last start
     * is more than two cycles back, so the guess is no longer worth showing; [projected] means the
     * entered start is over one cycle old and the day is counted on from it.
     */
    data class Status(val day: Int, val length: Int, val phase: Phase, val stale: Boolean = false, val projected: Boolean = false)

    /** Ovulation is about 14 days before the next period; the window is that day plus or minus one. */
    fun ovulationDay(length: Int) = length.coerceIn(MIN_LENGTH, MAX_LENGTH) - 14

    /** Phase of cycle day [day] (1-based) in a cycle of [length] days. */
    fun phase(day: Int, length: Int): Phase {
        val ov = ovulationDay(length)
        return when {
            day <= PERIOD_DAYS -> Phase.Menstrual
            day < ov - 1 -> Phase.Follicular
            day <= ov + 1 -> Phase.Ovulation
            else -> Phase.Luteal
        }
    }

    /** Today's status, or null when nothing usable is entered (no date, or one in the future). */
    fun status(lastStart: LocalDate?, length: Int, today: LocalDate): Status? {
        lastStart ?: return null
        val n = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val since = ChronoUnit.DAYS.between(lastStart, today)
        if (since < 0) return null
        val day = (since % n).toInt() + 1
        return Status(day, n, phase(day, n), stale = since >= 2L * n, projected = since >= n)
    }

    fun status(c: CycleSettings, today: LocalDate = LocalDate.now()): Status? =
        if (!c.on) null else status(parse(c.lastStart), c.length, today)

    fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    /**
     * Usual length from several period starts (any order): the median gap, leaving out gaps outside
     * 21 to 40 days (a missed entry or a doubled one). Null with fewer than one usable gap.
     */
    fun lengthFrom(starts: List<LocalDate>): Int? {
        val gaps = starts.distinct().sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
            .filter { it in MIN_LENGTH..MAX_LENGTH }.sorted()
        if (gaps.isEmpty()) return null
        return gaps[gaps.size / 2]
    }
}
