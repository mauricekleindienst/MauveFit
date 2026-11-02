package dev.mauve.fit.data

import java.time.LocalDate
import kotlin.math.abs

/** Cardio bookkeeping: effort levels, duplicates on import and the weekly minutes. */
object Cardio {

    /** The three effort choices of the log sheet, stored as an RPE on the 1 to 10 scale. */
    enum class Effort(val rpe: Int, private val de: String, private val en: String) {
        Easy(3, "Locker", "Easy"),
        Moderate(5, "Mittel", "Moderate"),
        Hard(8, "Hart", "Hard"),
        ;

        val label get() = L(de, en)

        companion object {
            fun of(rpe: Int?): Effort? = when {
                rpe == null -> null
                rpe >= 7 -> Hard
                rpe >= 5 -> Moderate
                else -> Easy
            }
        }
    }

    /** At or above this average pulse an unrated session counts as hard. */
    const val HARD_HR = 155

    /** A session this long counts as heavy for the legs whatever the effort. */
    const val LONG_MINUTES = 45

    /** Rated hard, or unrated with a high average pulse (typical for watch imports). */
    fun isHard(s: CardioSession) = (s.rpe ?: 0) >= 7 || (s.rpe == null && (s.avgHr ?: 0) >= HARD_HR)

    /** Kinds that load the legs enough to matter for leg day. */
    fun loadsLegs(s: CardioSession) = s.kind == CardioKind.Run || s.kind == CardioKind.Bike

    /** End of the session, which is when recovery starts. */
    fun end(s: CardioSession) = s.at + s.minutes * 60_000L

    /**
     * Sessions from [incoming] that are not already known: none starting within a minute of a known
     * one, and none of the same kind on the same day as one logged by hand (that is stamped when it
     * was logged, not when it was done, so the times rarely line up).
     */
    fun newSessions(existing: List<CardioSession>, incoming: List<CardioSession>): List<CardioSession> {
        val seen = existing.toMutableList()
        return incoming.filter { s ->
            val dup = seen.any { o ->
                abs(o.at - s.at) < 60_000L ||
                    // ...and started before that entry ended: an evening run after a logged morning run is new.
                    (o.source == "manual" && o.kind == s.kind && Stats.day(o.at) == Stats.day(s.at) && s.at < end(o) + 60_000L)
            }
            if (dup) false else { seen += s; true }
        }
    }

    /** Minutes per day of the current week, Monday first. */
    fun weekMinutes(cardio: List<CardioSession>, today: LocalDate = LocalDate.now()): List<Int> {
        val start = Stats.weekStart(today)
        val out = IntArray(7)
        cardio.forEach { s ->
            val i = Stats.day(s.at).toEpochDay() - start.toEpochDay()
            if (i in 0..6) out[i.toInt()] += s.minutes
        }
        return out.toList()
    }

    /** "45 min" or "1:20 h". */
    fun fmtMinutes(m: Int) = if (m < 60) "$m min" else "%d:%02d h".format(m / 60, m % 60)

    /** "10 km" or "7,5 km". */
    fun fmtKm(km: Double) = Stats.fmtKg(Math.round(km * 10) / 10.0) + " km"
}
