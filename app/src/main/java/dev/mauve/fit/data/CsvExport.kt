package dev.mauve.fit.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Training history as CSV, one row per set, for spreadsheets and other apps. Comma separated with a
 * decimal point and fixed English column names, so Excel, Google Sheets and Numbers all read it the
 * same way regardless of the phone's language.
 */
object CsvExport {
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    private val header = listOf(
        "date", "time", "workout", "exercise", "exercise_id", "muscle", "set", "type", "weight_kg", "reps", "rpe", "e1rm_kg", "workout_minutes",
    )

    /** [exercise] looks up names and muscles by id (the repo in the app, a stub in tests). */
    fun sets(d: AppData, exercise: (String) -> Exercise?): String = buildString {
        appendLine(header.joinToString(","))
        // Oldest first reads naturally in a spreadsheet.
        d.history.sortedBy { it.startedAt }.forEach { w ->
            val start = Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault())
            val minutes = (w.durationMs / 60_000).toString()
            w.entries.forEach { e ->
                val ex = exercise(e.exerciseId)
                var n = 0
                e.sets.filter { it.done }.forEach { s ->
                    if (!s.warmup) n++
                    val type = when {
                        s.warmup -> "warmup"
                        s.type == SetType.Drop -> "drop"
                        s.type == SetType.Failure -> "failure"
                        else -> "normal"
                    }
                    val row = listOf(
                        start.format(date), start.format(time), w.name, ex?.name ?: e.exerciseId, e.exerciseId, ex?.target.orEmpty(),
                        if (s.warmup) "" else "$n", type, num(s.weight), "${s.reps}", if (s.rpe > 0) "${s.rpe}" else "",
                        if (s.warmup || s.weight <= 0) "" else num(Stats.e1rm(s)), minutes,
                    )
                    appendLine(row.joinToString(",") { cell(it) })
                }
            }
        }
    }

    /** Whole numbers without ".0", otherwise one decimal; always with a point. */
    private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString()
    // Quarter kilos keep both decimals (11.25 kg stays 11.25), like Stats.fmtKg.
    else (if (kotlin.math.abs(v * 4 - Math.round(v * 4)) < 1e-9) "%.2f" else "%.1f").format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')

    /** Quotes a cell when it contains a comma, quote or line break (RFC 4180). */
    private fun cell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
