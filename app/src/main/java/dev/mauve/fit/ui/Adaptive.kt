package dev.mauve.fit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L

/**
 * Width class of the window, from the Material breakpoints: phones stay [Compact]; tablets,
 * unfolded foldables and landscape phones of 600 dp and more get the rail and centred content.
 */
enum class WindowWidth {
    Compact, Medium, Expanded;

    val wide get() = this != Compact
    val expanded get() = this == Expanded

    companion object {
        fun of(width: Dp) = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/** Set once in AppRoot from the window width; follows resizes, folds and rotation. */
val LocalWidth = compositionLocalOf { WindowWidth.Compact }

/** Content width on wide windows: lists and forms read best narrow, card grids may spread out. */
val ListWidth = 720.dp
val GridWidth = 1100.dp

/**
 * Cards that stack in one column on phones and pack into two on expanded windows. The DSL mirrors
 * the lazy one, so a screen switches by wrapping its cards in [cards].
 */
class CardScope internal constructor() {
    internal val slots = mutableListOf<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) { slots += key to content }
}

/**
 * Emits the cards built in [build]: one lazy item each on compact and medium windows (exactly as
 * before), or a single item with two balanced columns when [twoColumns] is set.
 */
fun LazyListScope.cards(twoColumns: Boolean, spacing: Dp, key: Any, build: CardScope.() -> Unit) {
    val slots = CardScope().apply(build).slots
    if (!twoColumns) slots.forEach { (k, content) -> item(key = k) { content() } }
    else if (slots.isNotEmpty()) item(key = key) { Masonry(spacing) { slots.forEach { (k, content) -> key(k) { content() } } } }
}

/**
 * Two columns; each child goes into the shorter one, so the order still reads top to bottom
 * and neither column runs much longer than the other.
 */
@Composable
fun Masonry(spacing: Dp, modifier: Modifier = Modifier, columns: Int = 2, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val colWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val heights = IntArray(columns)
        val placed = measurables.map { m ->
            val p = m.measure(Constraints(minWidth = colWidth, maxWidth = colWidth))
            val col = heights.indices.minBy { heights[it] }
            val y = heights[col]
            heights[col] += p.height + (if (p.height > 0) gap else 0)
            Triple(p, col, y)
        }
        val height = ((heights.maxOrNull() ?: 0) - gap).coerceAtLeast(0)
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placed.forEach { (p, col, y) -> p.place(col * (colWidth + gap), y) }
        }
    }
}

/**
 * The exercise library. On expanded windows it becomes list and detail side by side: the list
 * stays on the left and the picked exercise opens on the right instead of on its own screen.
 */
@Composable
fun LibraryPanes(data: AppData, nav: NavController) {
    if (!LocalWidth.current.expanded) {
        LibraryScreen(data, nav, pickFor = null)
        return
    }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { LibraryScreen(data, nav, pickFor = null, onOpen = { picked = it }) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val id = picked
            if (id == null) EmptyDetail()
            else key(id) { ExerciseScreen(id, data, nav, onBack = { picked = null }) }
        }
    }
}

/** Right pane before anything is picked. */
@Composable
private fun EmptyDetail() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(Pill).background(Ink.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = Ink.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(L("Übung wählen", "Pick an exercise"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            L("Ausführung, Verlauf und Notizen erscheinen hier.", "How to, history and notes show up here."),
            Modifier.width(280.dp), style = MaterialTheme.typography.bodyMedium, color = Ink.muted, textAlign = TextAlign.Center,
        )
    }
}

/** Fills the width of a wide window up to [max], centred; used for screens outside the nav host. */
@Composable
fun Centered(max: Dp, content: @Composable () -> Unit) {
    if (!LocalWidth.current.wide) { content(); return }
    Box(Modifier.fillMaxSize().background(Ink.bg), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxSize()) { content() }
    }
}
