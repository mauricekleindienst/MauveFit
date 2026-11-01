package dev.mauve.fit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.L


/**
 * "Smart invert": flips lightness but keeps hue, so the white-background GIFs turn into a dark
 * X-ray look with the target muscles still red. Inverting, then rotating hue by 180 degrees
 * (luma-preserving matrix), scaled so pure white lands on the stage colour. Runs on the GPU per
 * frame. Only used in the dark palette; on paper the GIFs keep their own white.
 */
private fun smartInvert(stage: Color): ColorFilter = run {
    val hue = arrayOf(
        floatArrayOf(-0.574f, 1.430f, 0.144f),
        floatArrayOf(0.426f, 0.430f, 0.144f),
        floatArrayOf(0.426f, 1.430f, -0.856f),
    )
    val k = 1f - stage.red // keep the darkest output at the stage colour, not pure black
    val bg = floatArrayOf(stage.red, stage.green, stage.blue)
    val m = FloatArray(20)
    for (r in 0..2) {
        for (c in 0..2) m[r * 5 + c] = -k * hue[r][c]
        // Rows of the hue matrix sum to 1, so white (1,1,1) maps to bg; values are 0..255 offsets.
        m[r * 5 + 4] = (k + bg[r]) * 255f
    }
    m[18] = 1f
    ColorFilter.colorMatrix(ColorMatrix(m))
}

/**
 * The exercise's animation (or, with [animated] = false, its still) straight from the dataset
 * repository, recoloured for the dark UI. Falls back to raw GitHub, then to an icon you can tap
 * to retry. Everything is cached on disk, so it works offline once seen.
 */
@Composable
fun ExerciseMedia(exercise: Exercise?, modifier: Modifier = Modifier, animated: Boolean = true, corner: Dp = 24.dp) {
    var retry by remember { mutableIntStateOf(0) }
    val shape = if (corner >= 20.dp) Facet() else RoundedCornerShape(minOf(corner, 8.dp))
    val filter = remember(Ink.dark) { if (Ink.dark) smartInvert(Ink.stage) else null }
    Box(modifier.clip(shape).background(Ink.stage), contentAlignment = Alignment.Center) {
        val placeholder = @Composable { Icon(Icons.Rounded.FitnessCenter, null, tint = Ink.faint, modifier = Modifier.size(28.dp)) }
        if (exercise == null) placeholder()
        else key(retry) {
            SubcomposeAsyncImage(
                model = if (animated) exercise.gifUrl else exercise.imageUrl,
                contentDescription = exercise.title,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
                loading = { if (animated) CircularProgressIndicator(Modifier.size(22.dp), color = Ink.faint, strokeWidth = 2.dp) },
                error = {
                    SubcomposeAsyncImage(
                        model = if (animated) exercise.gifFallback else exercise.imageFallback,
                        contentDescription = exercise.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = filter,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Box(Modifier.fillMaxSize().clickable { Haptics.tick(); retry++ }, contentAlignment = Alignment.Center) { placeholder() }
                        },
                    )
                },
            )
        }
    }
}

@Composable
fun Thumb(exercise: Exercise?, size: Dp = 56.dp) = ExerciseMedia(exercise, Modifier.size(size), animated = false, corner = 16.dp)

/** Flat raised card. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Ink.surface,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(Facet()).background(color).border(1.dp, Ink.line, Facet())
            .then(if (onClick != null) Modifier.clickable { Haptics.click(); onClick() } else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ink.muted) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)

/** The site's boxed label: mono caps in a hairline frame. */
@Composable
fun BoxedLabel(text: String, modifier: Modifier = Modifier, color: Color = Ink.muted) =
    Text(
        text.uppercase(),
        modifier.border(1.dp, Ink.lineStrong, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = color,
    )

/** Screen title block: small label above a large heading. */
@Composable
fun ScreenTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        BoxedLabel(eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}

/** Big number with a caption, used in stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Ink.text) {
    Card(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1)
    }
}

/** Filter chip. */
@Composable
fun Chip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.clip(Pill)
            .background(if (selected) Ink.accent else Ink.surface)
            .then(if (onClick != null) Modifier.clickable { Haptics.tick(); onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink.bg else Ink.text)
    }
}

/** Small read-only tag. */
@Composable
fun Tag(text: String, color: Color = Ink.muted) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.raised).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Primary action: lime, full-strength. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Ink.accent).clickable { Haptics.click(); onClick() }
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Ink.bg); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Ink.bg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Secondary action: outlined. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).border(1.5.dp, Ink.line, RoundedCornerShape(8.dp)).clickable { Haptics.click(); onClick() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Ink.text); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Ink.text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun RoundButton(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(Pill).background(Ink.raised).clickable(enabled = enabled) { Haptics.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = if (enabled) Ink.text else Ink.faint, modifier = Modifier.size(size * 0.5f)) }
}

/** "− value +" with a caption; used for plan targets. */
@Composable
fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Ink.raised).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(Pill).clickable { Haptics.tick(); onMinus() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, L("Weniger", "Less"), tint = Ink.muted, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
            Box(Modifier.size(34.dp).clip(Pill).clickable { Haptics.tick(); onPlus() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, L("Mehr", "More"), tint = Ink.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Progress ring for the rest timer. */
@Composable
fun Ring(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 6.dp, color: Color = Ink.accent) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "ring")
    Canvas(modifier) {
        val w = stroke.toPx()
        val tl = Offset(w / 2, w / 2)
        val s = Size(size.width - w, size.height - w)
        drawArc(Ink.raised, 0f, 360f, false, tl, s, style = Stroke(w))
        drawArc(color, -90f, 360f * p, false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Bar chart; the last bar (current week) is lime. */
@Composable
fun Bars(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier) {
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val h by animateFloatAsState(v / max, tween(600), label = "bar")
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(h.coerceAtLeast(0.04f)).clip(RoundedCornerShape(6.dp))
                            .background(if (i == values.lastIndex) Ink.accent else Ink.raised)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(labels[i], style = MaterialTheme.typography.labelSmall, color = Ink.faint, maxLines = 1)
            }
        }
    }
}

/** Horizontal meter. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Ink.accent) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "meter")
    Box(modifier.height(6.dp).clip(Pill).background(Ink.raised)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(Pill).background(color))
    }
}

fun Modifier.screenPadding() = padding(horizontal = 20.dp)

/**
 * The site's hero grid: hairlines on a regular raster, fading out downwards. Drawn behind the top
 * of a screen.
 */
@Composable
fun HeroGrid(modifier: Modifier = Modifier, cell: Dp = 28.dp) {
    val line = Ink.text.copy(alpha = if (Ink.dark) 0.07f else 0.08f)
    androidx.compose.foundation.Canvas(modifier) {
        val step = cell.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(line, Color.Transparent), 0f, size.height),
                Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f,
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            val a = 1f - y / size.height
            drawLine(line.copy(alpha = line.alpha * a), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}
