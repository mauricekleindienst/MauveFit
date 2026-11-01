package dev.mauve.fit.system

import android.graphics.Bitmap
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.exp
import kotlin.math.floor

/**
 * The dataset's GIFs are only 180 px. Stretched to a phone-wide stage by the bilinear filter they
 * look soft, so every frame is upscaled once at decode time with a bicubic (Catmull-Rom) filter and
 * an unsharp mask, which keeps the outlines crisp.
 */
class SharpGifDecoder(private val source: ImageSource, private val options: Options) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val gif = StandardGifDecoder(Provider)
        gif.read(bytes)
        val res = options.context.resources
        // Frames must be read in order (disposal depends on the previous one); sharpening them is
        // independent, so it runs on all cores.
        val raw = ArrayList<Triple<IntArray, Int, Int>>()
        val delays = ArrayList<Int>()
        repeat(gif.frameCount.coerceAtLeast(1)) {
            gif.advance()
            val delay = gif.nextDelay.let { if (it < 20) 100 else it }
            val frame = gif.nextFrame ?: return@repeat
            val px = IntArray(frame.width * frame.height)
            frame.getPixels(px, 0, frame.width, 0, 0, frame.width, frame.height)
            raw += Triple(px, frame.width, frame.height)
            delays += delay
        }
        gif.clear()
        val sharp = coroutineScope {
            raw.map { (px, w, h) -> async(Dispatchers.Default) { Upscale.sharp(px, w, h, SCALE) } }.awaitAll()
        }
        val anim = AnimationDrawable().apply { isOneShot = false }
        sharp.forEachIndexed { i, out ->
            val (_, w, h) = raw[i]
            val bmp = Bitmap.createBitmap(out, w * SCALE, h * SCALE, Bitmap.Config.ARGB_8888)
            anim.addFrame(BitmapDrawable(res, bmp).apply { isFilterBitmap = true }, delays[i])
        }
        return DecodeResult(image = anim.asImage(), isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (!result.source.source().rangeEquals(0, GIF)) return null
            return SharpGifDecoder(result.source, options)
        }
    }

    private object Provider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config) = Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) = Unit
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun release(bytes: ByteArray) = Unit
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun release(array: IntArray) = Unit
    }

    private companion object {
        /** 180 px becomes 540 px; the remaining stretch to the screen is small enough to stay sharp. */
        const val SCALE = 3
        val GIF = "GIF8".encodeUtf8()
    }
}

/** Integer-factor bicubic upscale followed by an unsharp mask, on packed ARGB pixels. */
object Upscale {
    private const val AMOUNT = 0.9f
    private const val SIGMA = 1.4f

    fun sharp(src: IntArray, w: Int, h: Int, s: Int): IntArray {
        val nw = w * s
        val nh = h * s
        val planes = Array(4) { c -> FloatArray(w * h) { i -> ((src[i] ushr (24 - 8 * c)) and 0xFF).toFloat() } }
        val (tx, wx) = taps(w, s)
        val (ty, wy) = taps(h, s)
        val out = IntArray(nw * nh)
        val up = Array(4) { FloatArray(nw * nh) }
        val row = FloatArray(nw * h)
        for (c in 0 until 4) {
            val p = planes[c]
            for (y in 0 until h) for (x in 0 until nw) {
                var v = 0f
                for (k in 0 until 4) v += p[y * w + tx[x * 4 + k]] * wx[x * 4 + k]
                row[y * nw + x] = v
            }
            val u = up[c]
            for (y in 0 until nh) for (x in 0 until nw) {
                var v = 0f
                for (k in 0 until 4) v += row[ty[y * 4 + k] * nw + x] * wy[y * 4 + k]
                u[y * nw + x] = v
            }
            // Alpha only needs the smooth upscale; colour channels get the unsharp mask.
            if (c > 0) unsharp(u, nw, nh)
        }
        for (i in out.indices) {
            out[i] = (clamp(up[0][i]) shl 24) or (clamp(up[1][i]) shl 16) or (clamp(up[2][i]) shl 8) or clamp(up[3][i])
        }
        return out
    }

    /** Source taps and Catmull-Rom weights for every output index along one axis. */
    private fun taps(n: Int, s: Int): Pair<IntArray, FloatArray> {
        val t = IntArray(n * s * 4)
        val wt = FloatArray(n * s * 4)
        for (o in 0 until n * s) {
            val x = (o + 0.5f) / s - 0.5f
            val x0 = floor(x).toInt()
            val f = x - x0
            for (k in 0 until 4) {
                t[o * 4 + k] = (x0 - 1 + k).coerceIn(0, n - 1)
                wt[o * 4 + k] = cubic(f - (k - 1))
            }
        }
        return t to wt
    }

    private fun cubic(d: Float): Float {
        val x = if (d < 0) -d else d
        return when {
            x < 1f -> 1.5f * x * x * x - 2.5f * x * x + 1f
            x < 2f -> -0.5f * x * x * x + 2.5f * x * x - 4f * x + 2f
            else -> 0f
        }
    }

    private fun unsharp(p: FloatArray, w: Int, h: Int) {
        val r = 3
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * SIGMA * SIGMA)) }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        val tmp = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = 0f
            for (i in -r..r) v += p[y * w + (x + i).coerceIn(0, w - 1)] * k[i + r]
            tmp[y * w + x] = v
        }
        for (y in 0 until h) for (x in 0 until w) {
            var v = 0f
            for (i in -r..r) v += tmp[(y + i).coerceIn(0, h - 1) * w + x] * k[i + r]
            val o = p[y * w + x]
            p[y * w + x] = o + AMOUNT * (o - v)
        }
    }

    private fun clamp(v: Float) = v.toInt().coerceIn(0, 255)
}
