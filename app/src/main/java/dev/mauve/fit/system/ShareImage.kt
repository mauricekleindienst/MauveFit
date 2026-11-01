package dev.mauve.fit.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import dev.mauve.fit.R
import dev.mauve.fit.data.L
import dev.mauve.fit.data.SetEntry
import dev.mauve.fit.data.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A workout or the records as a 1080×1350 picture for stories and chats: drawn straight onto a
 * bitmap in the app's ink colours and Archivo, then handed to the share sheet through the
 * FileProvider. Always dark with the copper accent, whatever the app's own theme is.
 */
object ShareImage {
    data class Card(
        val title: String,
        val date: String,
        /** Up to three figures, value to caption. */
        val stats: List<Pair<String, String>>,
        val heading: String,
        /** Up to four lines, name to value. */
        val rows: List<Pair<String, String>>,
    )

    private const val W = 1080
    private const val H = 1350
    private const val M = 96f

    private const val BG = 0xFF111315.toInt()
    private const val SURFACE = 0xFF1A1D21.toInt()
    private const val LINE = 0xFF2E3338.toInt()
    private const val TEXT = 0xFFEDEFF2.toInt()
    private const val MUTED = 0xFFA6ACB4.toInt()
    private const val COPPER = 0xFFDB9062.toInt()

    /** "80 kg × 5", or reps alone for body-weight sets. */
    fun setText(s: SetEntry): String = if (s.weight > 0) "${Stats.fmtKg(s.weight)} kg × ${s.reps}" else L("${s.reps} Wdh.", "${s.reps} reps")

    /** Renders and saves off the main thread, then opens the share sheet. */
    suspend fun share(context: Context, card: Card) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            // Only the latest picture is kept; older ones have been shared already.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "mauve-fit-${System.currentTimeMillis()}.png")
            val bitmap = render(context, card)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The clip carries the read grant through the chooser to the app picked there.
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, L("Als Bild teilen", "Share as image")))
    }

    fun render(context: Context, card: Card): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val face = runCatching { context.resources.getFont(R.font.archivo) }.getOrDefault(Typeface.DEFAULT)
        fun paint(size: Float, weight: Int, width: Int, color: Int, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                textSize = size
                fontVariationSettings = "'wght' $weight, 'wdth' $width"
                this.color = color
                textAlign = align
            }
        val inner = W - 2 * M
        c.drawColor(BG)

        // Brand line: the heartbeat mark and the name.
        heartbeat(c, M, 104f, 112f, 64f)
        c.drawText("Mauve Fit", M + 140f, 154f, paint(46f, 800, 125, TEXT))

        // Date and workout name; the name shrinks to fit one line before it is shortened.
        c.drawText(card.date, M, 310f, paint(36f, 500, 100, MUTED))
        val titlePaint = paint(104f, 800, 125, TEXT)
        while (titlePaint.measureText(card.title) > inner && titlePaint.textSize > 64f) titlePaint.textSize -= 4f
        c.drawText(fit(card.title, titlePaint, inner), M, 420f, titlePaint)

        // Figures, scoreboard-style; the last one in copper.
        val col = inner / card.stats.size.coerceAtLeast(1)
        card.stats.take(3).forEachIndexed { i, (value, label) ->
            val x = M + i * col
            val valuePaint = paint(64f, 750, 120, if (i == card.stats.lastIndex) COPPER else TEXT)
            while (valuePaint.measureText(value) > col - 24f && valuePaint.textSize > 36f) valuePaint.textSize -= 2f
            c.drawText(value, x, 560f, valuePaint)
            c.drawText(fit(label, paint(30f, 500, 100, MUTED), col - 24f), x, 608f, paint(30f, 500, 100, MUTED))
        }

        // Records or best sets on a tonal panel.
        if (card.rows.isNotEmpty()) {
            val rows = card.rows.take(4)
            val top = 680f
            val rowH = 118f
            val panel = RectF(M - 36f, top, W - M + 36f, top + 110f + rows.size * rowH)
            c.drawRoundRect(panel, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE })
            c.drawText(card.heading, M, top + 84f, paint(38f, 650, 114, COPPER))
            val namePaint = paint(42f, 600, 100, TEXT)
            val valuePaint = paint(46f, 700, 112, TEXT, Paint.Align.RIGHT)
            val rule = Paint().apply { color = LINE; strokeWidth = 2f }
            rows.forEachIndexed { i, (name, value) ->
                val base = top + 110f + i * rowH + 72f
                if (i > 0) c.drawLine(M, base - 84f, W - M, base - 84f, rule)
                val valueW = valuePaint.measureText(value)
                c.drawText(fit(name, namePaint, inner - valueW - 40f), M, base, namePaint)
                c.drawText(value, W - M, base, valuePaint)
            }
        }

        c.drawText(L("Mit Mauve Fit trainiert", "Trained with Mauve Fit"), M, H - M, paint(32f, 500, 100, MUTED))
        return bitmap
    }

    /** The mark: a flat line with one heartbeat in it, drawn into the box at x, y. */
    private fun heartbeat(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val mid = y + h / 2
        val path = Path().apply {
            moveTo(x, mid)
            lineTo(x + w * 0.28f, mid)
            lineTo(x + w * 0.38f, mid - h * 0.22f)
            lineTo(x + w * 0.48f, mid)
            lineTo(x + w * 0.56f, y)
            lineTo(x + w * 0.68f, y + h)
            lineTo(x + w * 0.76f, mid)
            lineTo(x + w, mid)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COPPER
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }

    /** Shortens [text] with an ellipsis until it fits [max] pixels. */
    private fun fit(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return t.trimEnd() + "…"
    }
}
