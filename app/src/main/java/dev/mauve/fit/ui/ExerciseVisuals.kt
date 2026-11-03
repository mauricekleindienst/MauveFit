package dev.mauve.fit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import dev.mauve.fit.App
import dev.mauve.fit.data.BodyMap
import dev.mauve.fit.data.BodySide
import dev.mauve.fit.data.Drawings
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels
import kotlin.math.max
import kotlin.math.min

/**
 * A body silhouette with the exercise's target muscles in the accent and the secondary ones in a
 * lighter tint. With [zoom], it closes in on what is trained, for small thumbnails.
 */
@Composable
fun MuscleMap(exercise: Exercise?, modifier: Modifier = Modifier, side: BodySide? = null, zoom: Boolean = false) {
    val regions = App.repo.muscleRegions
    val primary = remember(exercise) { regions.regions(listOfNotNull(exercise?.target)) }
    val secondary = remember(exercise) { regions.regions(exercise?.secondary.orEmpty()) - primary }
    val shown = side ?: exercise?.let(regions::side) ?: BodySide.FRONT
    val map = App.repo.bodyMap
    val accent = Ink.accent
    val body = Ink.line
    val outline = Ink.lineStrong.copy(alpha = 0.6f)
    Canvas(modifier) {
        val s = map[shown]
        val full = Rect(0f, 0f, map.width, map.height)
        val focus = if (zoom) focusRect(map, s, primary.ifEmpty { secondary }, size.width / max(1f, size.height)) else full
        val k = min(size.width / focus.width, size.height / focus.height)
        translate((size.width - focus.width * k) / 2 - focus.left * k, (size.height - focus.height * k) / 2 - focus.top * k) {
            scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                for ((slug, path) in s.parts) {
                    drawPath(path, if (slug in primary) accent else if (slug in secondary) accent.copy(alpha = 0.42f) else body)
                }
                drawPath(s.outline, outline, style = Stroke(1.2f / k))
            }
        }
    }
}

/** The trained regions with room around them, at the view's aspect, kept inside the body. */
private fun focusRect(map: BodyMap, side: BodyMap.Side, wanted: Set<String>, aspect: Float): Rect {
    val full = Rect(0f, 0f, map.width, map.height)
    val boxes = wanted.mapNotNull { side.parts[it]?.getBounds() }
    if (boxes.isEmpty()) return full
    val box = boxes.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
    // At least half the body tall and plenty of room around the muscles, so the figure reads as a
    // body and not as cut-off pieces, even for one small muscle.
    var h = maxOf(box.height * 2.2f, box.width * 2.2f / aspect, full.height * 0.5f)
    var w = h * aspect
    if (w > full.width) { w = full.width; h = w / aspect }
    val left = (box.center.x - w / 2).coerceIn(0f, full.width - w)
    val top = (box.center.y - h / 2).coerceIn(0f, full.height - h)
    return Rect(left, top, left + w, top + h)
}

/**
 * Turns a black-on-white line drawing into lines in [color] on a clear background: the darker a
 * pixel, the more opaque, so the drawing fits both themes and follows the accent.
 */
private fun lineArt(color: Color) = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, color.red * 255f,
            0f, 0f, 0f, 0f, color.green * 255f,
            0f, 0f, 0f, 0f, color.blue * 255f,
            -0.299f, -0.587f, -0.114f, 0f, 255f,
        ),
    ),
)

@Composable
private fun Drawing(path: String, modifier: Modifier = Modifier) {
    val accent = Ink.accent
    val filter = remember(accent) { lineArt(accent) }
    val painter = rememberAsyncImagePainter("file:///android_asset/$path")
    // Read from the file header, so the layout has the drawing's shape before it is decoded.
    val context = LocalContext.current
    val aspect = remember(path) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, o) }
            o.outWidth.toFloat() / o.outHeight
        }.getOrNull()?.takeIf { it.isFinite() && it > 0f }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter, null,
            // Sized to the drawing itself, so the fade sits on its edges and not on empty stage.
            // Some drawings are cropped in the original artwork; fading both sides out makes a cut
            // plate look intended instead of clipped.
            (if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize())
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(EdgeFade, blendMode = BlendMode.DstIn)
                },
            contentScale = ContentScale.Fit, colorFilter = filter,
        )
    }
}

private val EdgeFade = Brush.horizontalGradient(0f to Color.Transparent, 0.15f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent)

/**
 * The picture of an exercise. [full]: its Everkinetic drawings of the start and end position
 * where there are any, otherwise the muscle map from the front and the back. Otherwise the
 * muscle map closed in on what it trains, for thumbnails. All bundled, so it works offline.
 */
@Composable
fun ExerciseMedia(exercise: Exercise?, modifier: Modifier = Modifier, full: Boolean = true, corner: Dp = 24.dp) {
    val label = exercise?.let { muscleLabel(it) }.orEmpty()
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Ink.stage).clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val drawings = exercise?.let { App.repo.drawings[it.id] }
        when {
            exercise == null -> Icon(Icons.Rounded.FitnessCenter, null, tint = Ink.faint, modifier = Modifier.size(28.dp))
            !full -> MuscleMap(exercise, Modifier.fillMaxSize().padding(corner * 0.25f), zoom = true)
            drawings != null -> Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                drawings.forEach { Drawing(it, Modifier.weight(1f).fillMaxHeight()) }
            }
            else -> Row(Modifier.fillMaxSize().padding(vertical = 14.dp)) {
                MuscleMap(exercise, Modifier.weight(1f).fillMaxHeight(), BodySide.FRONT)
                MuscleMap(exercise, Modifier.weight(1f).fillMaxHeight(), BodySide.BACK)
            }
        }
    }
}

private fun muscleLabel(e: Exercise) = L("Muskelkarte: ", "Muscle map: ") + e.muscles.map(Labels::muscle).joinToString(", ")

/** Height of a stage that shows an exercise's drawings or muscle map. */
fun mediaStageHeight(): Dp = 280.dp

@Composable
fun Thumb(exercise: Exercise?, size: Dp = 56.dp) = ExerciseMedia(exercise, Modifier.size(size), full = false, corner = 16.dp)

/**
 * The top of an exercise's page: the Everkinetic drawings of the start and end position where
 * there are any, with their credit, and the muscle map from the front and the back.
 */
@Composable
fun ExerciseHero(e: Exercise, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier) {
        App.repo.drawings[e.id]?.let { files ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink.stage).padding(16.dp)
                    .clearAndSetSemantics { contentDescription = L("Zeichnung von Start- und Endposition", "Drawing of the start and end position") },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    files.forEachIndexed { i, f ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Drawing(f, Modifier.fillMaxWidth().height(220.dp))
                            if (files.size > 1) {
                                Spacer(Modifier.height(4.dp))
                                Text(if (i == 0) L("Start", "Start") else L("Ende", "End"), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                            }
                        }
                    }
                }
            }
            Text(
                L("Illustrationen: Everkinetic, CC BY-SA 4.0", "Illustrations: Everkinetic, CC BY-SA 4.0"),
                Modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp).clickable { uri.openUri(Drawings.LICENSE_URL) },
                style = MaterialTheme.typography.labelSmall, color = Ink.faint, textDecoration = TextDecoration.Underline,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(mediaStageHeight()).clip(RoundedCornerShape(28.dp)).background(Ink.stage)
                .clearAndSetSemantics { contentDescription = muscleLabel(e) },
        ) {
            Row(Modifier.fillMaxSize().padding(vertical = 14.dp)) {
                MuscleMap(e, Modifier.weight(1f).fillMaxHeight(), BodySide.FRONT)
                MuscleMap(e, Modifier.weight(1f).fillMaxHeight(), BodySide.BACK)
            }
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(Ink.accent, L("Ziel", "Target"))
                if (e.secondary.isNotEmpty()) Legend(Ink.accent.copy(alpha = 0.42f), L("Mit dabei", "Also works"))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
    }
}
