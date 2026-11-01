package dev.mauve.fit.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
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
    val shape = RoundedCornerShape(corner)
    val filter = remember(Ink.dark) { if (Ink.dark) smartInvert(Ink.stage) else null }
    // Animations are drawn at most at their decoded size (540 px), never stretched beyond it, so they
    // stay sharp; the stage around them fills the rest.
    val nativeSize = with(LocalDensity.current) { MEDIA_PX.toDp() }
    val fit = if (animated) Modifier.widthIn(max = nativeSize).heightIn(max = nativeSize).aspectRatio(1f) else Modifier.fillMaxSize()
    Box(modifier.clip(shape).background(Ink.stage), contentAlignment = Alignment.Center) {
        val placeholder = @Composable { Icon(Icons.Rounded.FitnessCenter, null, tint = Ink.faint, modifier = Modifier.size(28.dp)) }
        if (exercise == null) placeholder()
        else key(retry) {
            SubcomposeAsyncImage(
                model = if (animated) exercise.gifUrl else exercise.imageUrl,
                contentDescription = exercise.title,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = fit,
                // While the animation is fetched and sharpened, the still frame holds its place.
                loading = {
                    if (animated) AsyncImage(
                        exercise.imageUrl, null, Modifier.fillMaxSize().alpha(0.55f),
                        contentScale = ContentScale.Fit, colorFilter = filter,
                    )
                },
                error = {
                    SubcomposeAsyncImage(
                        model = if (animated) exercise.gifFallback else exercise.imageFallback,
                        contentDescription = exercise.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = filter,
                        modifier = fit,
                        error = {
                            Box(Modifier.fillMaxSize().clickable { Haptics.tick(); retry++ }, contentAlignment = Alignment.Center) { placeholder() }
                        },
                    )
                },
            )
        }
    }
}

/** Decoded animation size: 180 px source, upscaled three times. */
const val MEDIA_PX = 540

/** Height for a stage that shows one animation at full sharpness plus a margin. */
@Composable
fun mediaStageHeight(): Dp = with(LocalDensity.current) { MEDIA_PX.toDp() } + 24.dp

@Composable
fun Thumb(exercise: Exercise?, size: Dp = 56.dp) = ExerciseMedia(exercise, Modifier.size(size), animated = false, corner = 16.dp)

/**
 * Tonal panel: set apart by surface colour alone, no outline. Tappable panels give a little under
 * the finger, so a press is felt before the screen changes.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Ink.surface,
    padding: PaddingValues = PaddingValues(20.dp),
    shape: Shape = Panel,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.then(if (onClick != null) Modifier.pressable(onLongClick?.let { l -> { Haptics.heavy(); l() } }) { Haptics.click(); onClick() } else Modifier)
            .clip(shape).background(color)
            .padding(padding),
        content = content,
    )
}

/** Click with a springy press-in; used for every large tappable surface. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ink.muted) =
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = color)

/** Screen title: the wide heading first, one plain line of context under it. */
@Composable
fun ScreenTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (eyebrow.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(eyebrow, style = MaterialTheme.typography.bodyLarge, color = Ink.muted)
        }
    }
}

/** Big number with a caption, used in stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Ink.text) {
    Card(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        // The wide face runs out of room fast in a third of the screen; long values step down a size.
        val style = if (value.length > 5) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium
        Text(value, style = style, color = accent, maxLines = 1, softWrap = false)
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
fun Tag(text: String, color: Color = Ink.muted, modifier: Modifier = Modifier) {
    Box(modifier.clip(Pill).background(Ink.raised).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Primary action: the accent at full strength. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.pressable { Haptics.click(); onClick() }.clip(Pill).background(Ink.accent)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Ink.bg); Spacer(Modifier.width(8.dp)) }
        // One line always: a button label that wraps reads as two buttons.
        Text(text, color = Ink.bg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: a quiet tonal fill, same shape as the primary one. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.pressable { Haptics.click(); onClick() }.clip(Pill).background(Ink.raised)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Ink.text); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Ink.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RoundButton(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.size(size).then(if (enabled) Modifier.pressable { Haptics.click(); onClick() } else Modifier).clip(Pill).background(Ink.raised),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = if (enabled) Ink.text else Ink.faint, modifier = Modifier.size(size * 0.5f)) }
}

/** "− value +" with a caption; used for plan targets. */
@Composable
fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    // Label and value stacked, the two buttons below: nothing crowds the number, even "2:00".
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Ink.raised).padding(top = 10.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepButton(Icons.Rounded.Remove, L("Weniger", "Less"), Modifier.weight(1f), onMinus)
            StepButton(Icons.Rounded.Add, L("Mehr", "More"), Modifier.weight(1f), onPlus)
        }
    }
}

@Composable
fun StepButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(34.dp).clip(Pill).background(Ink.line).clickable { Haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Ink.text, modifier = Modifier.size(18.dp)) }
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


/** Square-ish shortcut: icon in an accent disc, title and one line below. */
@Composable
fun ShortcutTile(title: String, sub: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        Box(Modifier.size(40.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 1)
    }
}

/**
 * Which equipment the coach may use, e.g. machines only or no body-weight moves like pull-ups.
 * Only groups the setup offers are shown; all on is stored as "no preference".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitPicker(setup: dev.mauve.fit.data.Setup, kit: Set<String>, onChange: (Set<String>) -> Unit) {
    val offered = dev.mauve.fit.data.Kit.entries.filter { k -> k.equipment.any(setup::allows) }
    if (offered.size < 2) return
    val on = kit.ifEmpty { offered.map { it.key }.toSet() }
    Text(L("Welche Geräte willst du nutzen?", "Which equipment do you want to use?"), style = MaterialTheme.typography.titleMedium)
    Text(L("Abwählen, was du nicht magst, zum Beispiel Körpergewicht für keine Klimmzüge.", "Switch off what you don't want, e.g. body weight for no pull-ups."),
        color = Ink.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        offered.forEach { k ->
            val selected = k.key in on
            Chip(k.label, selected) {
                // At least one group stays on, or the coach would have nothing to pick from.
                val next = if (selected) on - k.key else on + k.key
                if (next.isNotEmpty()) onChange(if (next.size == offered.size) emptySet() else next)
            }
        }
    }
}

/**
 * Backdrop of a pinned header: the page colour, fading out at the bottom so content slides under
 * it softly. Drawn wider than the header itself, so the list's side padding never shows a hard
 * edge; the list's own bounds clip it at the screen edges.
 */
fun Modifier.stickyScrim(): Modifier = drawBehind {
    val out = 24.dp.toPx()
    drawRect(
        Brush.verticalGradient(0f to Ink.bg, 0.72f to Ink.bg, 1f to Ink.bg.copy(alpha = 0f)),
        topLeft = Offset(-out, 0f), size = Size(size.width + 2 * out, size.height),
    )
}
