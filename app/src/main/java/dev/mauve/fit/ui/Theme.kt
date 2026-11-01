package dev.mauve.fit.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mauve.fit.R

/**
 * Colours after the Kalzit design language: a warm, almost monochrome palette, "ink" (light on a
 * warm near-black) and "paper" (dark on warm stone), with one accent the user can pick.
 */
data class Palette(
    val dark: Boolean,
    val bg: Color, val surface: Color, val raised: Color, val line: Color, val lineStrong: Color,
    val text: Color, val muted: Color, val faint: Color,
    /** What the exercise animations sit on: the recoloured GIF white in ink, the GIF's own white on paper. */
    val stage: Color,
    val coral: Color, val red: Color,
)

private val InkPalette = Palette(
    dark = true,
    bg = Color(0xFF0B0A09), surface = Color(0xFF161312), raised = Color(0xFF1F1C1A), line = Color(0xFF2B2826), lineStrong = Color(0xFF5C5754),
    text = Color(0xFFF0ECE7), muted = Color(0xFFB8B3AD), faint = Color(0xFF99948F),
    stage = Color(0xFF000000),
    coral = Color(0xFFE8A27E), red = Color(0xFFF0857A),
)

private val PaperPalette = Palette(
    dark = false,
    bg = Color(0xFFF8F6F3), surface = Color(0xFFFEFDFB), raised = Color(0xFFF0EDE8), line = Color(0xFFD9D5D0), lineStrong = Color(0xFFA39D98),
    text = Color(0xFF191512), muted = Color(0xFF514C48), faint = Color(0xFF615B57),
    stage = Color(0xFFFFFFFF),
    coral = Color(0xFFB0562C), red = Color(0xFFB3382C),
)

/** Accent choices; each has a tone for ink and one for paper so it keeps contrast in both. */
enum class Accent(val key: String, private val de: String, private val en: String, val onInk: Color, val onPaper: Color) {
    Copper("copper", "Kupfer", "Copper", Color(0xFFDB9062), Color(0xFFAC5C2E)),
    Sand("sand", "Sand", "Sand", Color(0xFFD9B88B), Color(0xFF946233)),
    Sage("sage", "Salbei", "Sage", Color(0xFF9DC3A3), Color(0xFF4B7453)),
    Blue("blue", "Blau", "Blue", Color(0xFF8FB3D2), Color(0xFF3D6180)),
    Mauve("mauve", "Mauve", "Mauve", Color(0xFFC5A9E6), Color(0xFF7955A0)),
    Lime("lime", "Lime", "Lime", Color(0xFFD5F64E), Color(0xFF668800)),
    ;

    val label get() = dev.mauve.fit.data.L(de, en)

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Copper
    }
}

/**
 * The active colours. Set once per activity (theme and accent changes recreate it), so plain
 * reads during composition are safe.
 */
object Ink {
    var palette: Palette = InkPalette
        private set
    var accentChoice: Accent = Accent.Copper
        private set

    fun apply(dark: Boolean, accent: Accent) {
        palette = if (dark) InkPalette else PaperPalette
        accentChoice = accent
    }

    val dark get() = palette.dark
    val bg get() = palette.bg
    val surface get() = palette.surface
    val raised get() = palette.raised
    val line get() = palette.line
    val lineStrong get() = palette.lineStrong
    val text get() = palette.text
    val muted get() = palette.muted
    val faint get() = palette.faint
    val stage get() = palette.stage
    val coral get() = palette.coral
    val red get() = palette.red
    /** The accent; text on an accent fill uses [bg], which contrasts in both palettes. */
    val accent get() = if (palette.dark) accentChoice.onInk else accentChoice.onPaper
    /** Secondary signal colour (rest, recovery). */
    val mauve get() = if (palette.dark) Color(0xFFC5A9E6) else Color(0xFF7955A0)
}

private fun newsreader(weight: Int) = Font(
    R.font.newsreader, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 48f)),
)

private fun instrument(weight: Int) = Font(
    R.font.instrument_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun mono(weight: Int) = Font(
    R.font.jetbrains_mono, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Newsreader for headlines, Instrument Sans for text, JetBrains Mono for labels: as on kalzit. */
val Display = FontFamily(newsreader(340), newsreader(400), newsreader(500))
val Sans = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))
val Mono = FontFamily(mono(400), mono(500), mono(600))

/** Small radii as on the site (4 px buttons, 10 px panels). */
val Pill = RoundedCornerShape(8.dp)

/**
 * The crystal facet that marks panels on kalzit: one corner cut off (top right), the others
 * slightly rounded.
 */
class Facet(private val cut: Dp = 14.dp, private val radius: Dp = 6.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        val p = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

@Composable
fun FitTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val sans = { s: androidx.compose.ui.text.TextStyle -> s.copy(fontFamily = Sans) }
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight(340), letterSpacing = (-1.6).sp),
        displayMedium = base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight(340), letterSpacing = (-1.3).sp),
        displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight(400), letterSpacing = (-1).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight(400), letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight(400), letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight(500), letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = sans(base.titleMedium).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = sans(base.titleSmall).copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = sans(base.bodyLarge),
        bodyMedium = sans(base.bodyMedium),
        bodySmall = sans(base.bodySmall),
        labelLarge = sans(base.labelLarge).copy(fontWeight = FontWeight.SemiBold),
        labelMedium = sans(base.labelMedium).copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
    )
    val p = Ink.palette
    val colors = if (p.dark) darkColorScheme(
        primary = Ink.accent, onPrimary = p.bg, secondary = Ink.mauve, onSecondary = p.bg, tertiary = p.coral,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text, surfaceVariant = p.surface,
        onSurfaceVariant = p.muted, surfaceContainer = p.surface, surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised,
        outline = p.line, outlineVariant = p.line, error = p.red, onError = p.bg,
    ) else lightColorScheme(
        primary = Ink.accent, onPrimary = p.bg, secondary = Ink.mauve, onSecondary = p.bg, tertiary = p.coral,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text, surfaceVariant = p.surface,
        onSurfaceVariant = p.muted, surfaceContainer = p.surface, surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised,
        outline = p.line, outlineVariant = p.line, error = p.red, onError = p.bg,
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
