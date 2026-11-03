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
import androidx.compose.ui.graphics.asImageBitmap
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
 * Colours of a strength gym: cool graphite like iron and rubber flooring ("iron"), or chalk-white
 * ("chalk"), with one accent the user picks. Plate colours appear only where they encode data.
 */
data class Palette(
    val dark: Boolean,
    val bg: Color, val surface: Color, val raised: Color, val line: Color, val lineStrong: Color,
    val text: Color, val muted: Color, val faint: Color,
    /** What the muscle maps and exercise drawings sit on. */
    val stage: Color,
    val coral: Color, val red: Color,
)

private val InkPalette = Palette(
    dark = true,
    bg = Color(0xFF111315), surface = Color(0xFF1A1D21), raised = Color(0xFF23272C), line = Color(0xFF2E3338), lineStrong = Color(0xFF4A5058),
    text = Color(0xFFEDEFF2), muted = Color(0xFFA6ACB4), faint = Color(0xFF899099),
    stage = Color(0xFF000000),
    coral = Color(0xFFEE8F6A), red = Color(0xFFF0736A),
)

private val PaperPalette = Palette(
    dark = false,
    bg = Color(0xFFF2F3F5), surface = Color(0xFFFFFFFF), raised = Color(0xFFE8EAED), line = Color(0xFFD5D9DE), lineStrong = Color(0xFF9AA1AA),
    text = Color(0xFF121417), muted = Color(0xFF4E555E), faint = Color(0xFF626972),
    stage = Color(0xFFFFFFFF),
    coral = Color(0xFFA84D2A), red = Color(0xFFB3362C),
)

/** Accent choices; each has a tone for ink and one for paper so it keeps contrast in both. */
/** Light-mode accents are tuned to at least 4.5:1 (WCAG AA) on every paper surface, text on the fill included. */
enum class Accent(val key: String, private val de: String, private val en: String, val onInk: Color, val onPaper: Color) {
    Copper("copper", "Kupfer", "Copper", Color(0xFFDB9062), Color(0xFF9E542A)),
    Sand("sand", "Sand", "Sand", Color(0xFFD9B88B), Color(0xFF8C5D30)),
    Sage("sage", "Salbei", "Sage", Color(0xFF9DC3A3), Color(0xFF497151)),
    Blue("blue", "Blau", "Blue", Color(0xFF8FB3D2), Color(0xFF3D6180)),
    Mauve("mauve", "Mauve", "Mauve", Color(0xFFC5A9E6), Color(0xFF7955A0)),
    Lime("lime", "Lime", "Lime", Color(0xFFD5F64E), Color(0xFF557100)),
    // Bought in the shop (Shop.premiumAccents); light variants tuned to 4.5:1 like the rest.
    Gold("gold", "Gold", "Gold", Color(0xFFE8C15A), Color(0xFF826412)),
    Ruby("ruby", "Rubin", "Ruby", Color(0xFFEE7F8E), Color(0xFFCB1B33)),
    Emerald("emerald", "Smaragd", "Emerald", Color(0xFF5FD3A2), Color(0xFF207652)),
    Ocean("ocean", "Ozean", "Ocean", Color(0xFF5FC4E8), Color(0xFF156F8F)),
    Rose("rose", "Rosé", "Rose", Color(0xFFE6A0C4), Color(0xFFB83076)),
    /** Taken from the wallpaper (Android 12+); the colours here are only the swatch fallback. */
    Wallpaper("wallpaper", "Hintergrund", "Wallpaper", Color(0xFFA6ACB4), Color(0xFF4E555E)),
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

    /** The wallpaper's accent tone for the current palette, when [Accent.Wallpaper] is chosen. */
    private var wallpaper: Color? = null

    fun apply(dark: Boolean, accent: Accent, wallpaperTone: Color? = null) {
        palette = if (dark) InkPalette else PaperPalette
        accentChoice = accent
        wallpaper = wallpaperTone.takeIf { accent == Accent.Wallpaper }
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
    val accent get() = wallpaper ?: if (palette.dark) accentChoice.onInk else accentChoice.onPaper
    /** Secondary signal colour (rest, recovery). */
    val mauve get() = if (palette.dark) Color(0xFFC5A9E6) else Color(0xFF7955A0)
}

private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/**
 * One family, Archivo, set at different widths: extra-wide and heavy like a scoreboard for titles
 * and numbers, normal width for reading. The old names stay so call sites keep their roles.
 */
val Display = FontFamily(archivo(700, 125f), archivo(800, 125f))
val Heading = FontFamily(archivo(650, 114f), archivo(700, 114f))
val Sans = FontFamily(archivo(400, 100f), archivo(500, 100f), archivo(600, 100f), archivo(700, 100f))
/** Figures in tables and counters: semi-wide, so digits read at a glance mid-set. */
val Mono = FontFamily(archivo(600, 112f), archivo(700, 112f))

/** Fully rounded controls and soft panels. */
val Pill = RoundedCornerShape(percent = 50)
/** Radii by rank: heroes curve most, panels less, rows least. */
val Hero = RoundedCornerShape(32.dp)
val Panel = RoundedCornerShape(24.dp)
val RowShape = RoundedCornerShape(18.dp)

@Composable
fun FitTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val sans = { s: androidx.compose.ui.text.TextStyle -> s.copy(fontFamily = Sans) }
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp),
        displayMedium = base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp),
        displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight(700), letterSpacing = (-0.6).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = Heading, fontWeight = FontWeight(700), fontSize = 21.sp, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontFamily = Heading, fontWeight = FontWeight(650), fontSize = 19.sp, letterSpacing = (-0.2).sp),
        titleMedium = sans(base.titleMedium).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = sans(base.titleSmall).copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = sans(base.bodyLarge),
        bodyMedium = sans(base.bodyMedium),
        bodySmall = sans(base.bodySmall),
        labelLarge = sans(base.labelLarge).copy(fontWeight = FontWeight.SemiBold),
        labelMedium = sans(base.labelMedium).copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.1.sp),
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
