package dev.mauve.fit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mauve.fit.data.Shop
import kotlin.math.cos
import kotlin.math.sin

/** Frame colour per rarity, as RPGs colour their loot. */
fun rarityColor(r: Shop.Rarity): Color = when (r) {
    Shop.Rarity.Common -> Color(0xFF9AA1AA)
    Shop.Rarity.Rare -> Color(0xFF4F9FE0)
    Shop.Rarity.Epic -> Color(0xFFA56FE0)
    Shop.Rarity.Legendary -> Color(0xFFE5A23A)
    Shop.Rarity.Mythic -> Color(0xFFE0568C)
}

/**
 * The shop's own drawn icons: a rarity frame with a die, a bolt, a snowflake shield, a gem in the
 * colour on sale, or a medal (crown from legendary up) for titles.
 */
@Composable
fun ShopIcon(item: Shop.Item, size: Dp = 48.dp, locked: Boolean = false) {
    val frame = rarityColor(item.rarity).let { if (locked) it.copy(alpha = 0.45f) else it }
    val accent = if (item.kind == Shop.Kind.Accent) gemColor(item.key.removePrefix("accent:")) else frame
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(frame.copy(alpha = 0.16f), cornerRadius = CornerRadius(s * 0.28f))
        drawRoundRect(frame, cornerRadius = CornerRadius(s * 0.28f), style = Stroke(s * 0.05f))
        when (item.kind) {
            Shop.Kind.Reroll -> die(s, frame)
            Shop.Kind.Boost -> bolt(s, frame)
            Shop.Kind.Freeze -> snowShield(s, frame)
            Shop.Kind.Accent -> gem(s, accent, frame)
            Shop.Kind.Title -> if (item.rarity >= Shop.Rarity.Legendary) crown(s, frame) else medal(s, frame)
        }
    }
}

private fun gemColor(key: String) = when (key) {
    "gold" -> Color(0xFFE8C15A)
    "ruby" -> Color(0xFFEE7F8E)
    "emerald" -> Color(0xFF5FD3A2)
    "ocean" -> Color(0xFF5FC4E8)
    else -> Color(0xFFE6A0C4)
}

private fun DrawScope.die(s: Float, c: Color) {
    val side = s * 0.46f
    val tl = Offset((s - side) / 2, (s - side) / 2)
    drawRoundRect(c, tl, Size(side, side), CornerRadius(side * 0.22f), style = Stroke(s * 0.05f))
    val r = s * 0.04f
    listOf(0.28f to 0.28f, 0.72f to 0.72f, 0.5f to 0.5f, 0.72f to 0.28f, 0.28f to 0.72f).forEach { (x, y) ->
        drawCircle(c, r, Offset(tl.x + side * x, tl.y + side * y))
    }
}

private fun DrawScope.bolt(s: Float, c: Color) {
    val p = Path().apply {
        moveTo(s * 0.56f, s * 0.2f); lineTo(s * 0.32f, s * 0.54f); lineTo(s * 0.48f, s * 0.54f)
        lineTo(s * 0.42f, s * 0.8f); lineTo(s * 0.68f, s * 0.44f); lineTo(s * 0.52f, s * 0.44f); close()
    }
    drawPath(p, c)
}

private fun DrawScope.snowShield(s: Float, c: Color) {
    val shield = Path().apply {
        moveTo(s * 0.5f, s * 0.18f); lineTo(s * 0.76f, s * 0.28f); lineTo(s * 0.74f, s * 0.54f)
        quadraticTo(s * 0.7f, s * 0.74f, s * 0.5f, s * 0.84f)
        quadraticTo(s * 0.3f, s * 0.74f, s * 0.26f, s * 0.54f); lineTo(s * 0.24f, s * 0.28f); close()
    }
    drawPath(shield, c, style = Stroke(s * 0.045f, join = StrokeJoin.Round))
    val center = Offset(s * 0.5f, s * 0.5f)
    val len = s * 0.17f
    for (k in 0 until 3) {
        val a = Math.toRadians(90.0 + k * 60.0)
        val d = Offset((cos(a) * len).toFloat(), (sin(a) * len).toFloat())
        drawLine(c, center - d, center + d, s * 0.04f, StrokeCap.Round)
    }
}

private fun DrawScope.gem(s: Float, fill: Color, edge: Color) {
    val p = Path().apply {
        moveTo(s * 0.32f, s * 0.36f); lineTo(s * 0.42f, s * 0.26f); lineTo(s * 0.58f, s * 0.26f)
        lineTo(s * 0.68f, s * 0.36f); lineTo(s * 0.5f, s * 0.76f); close()
    }
    drawPath(p, fill)
    drawPath(p, edge, style = Stroke(s * 0.035f, join = StrokeJoin.Round))
    drawLine(edge, Offset(s * 0.32f, s * 0.36f), Offset(s * 0.68f, s * 0.36f), s * 0.03f)
    drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 0.44f, s * 0.3f), Offset(s * 0.4f, s * 0.36f), s * 0.03f, StrokeCap.Round)
}

private fun DrawScope.medal(s: Float, c: Color) {
    val ribbon = Path().apply {
        moveTo(s * 0.38f, s * 0.18f); lineTo(s * 0.46f, s * 0.42f); lineTo(s * 0.54f, s * 0.42f); lineTo(s * 0.62f, s * 0.18f)
    }
    drawPath(ribbon, c, style = Stroke(s * 0.05f, join = StrokeJoin.Round))
    drawCircle(c, s * 0.17f, Offset(s * 0.5f, s * 0.6f))
    drawCircle(Color.White.copy(alpha = 0.55f), s * 0.08f, Offset(s * 0.5f, s * 0.6f), style = Stroke(s * 0.03f))
}

private fun DrawScope.crown(s: Float, c: Color) {
    val p = Path().apply {
        moveTo(s * 0.24f, s * 0.68f); lineTo(s * 0.26f, s * 0.34f); lineTo(s * 0.38f, s * 0.48f)
        lineTo(s * 0.5f, s * 0.26f); lineTo(s * 0.62f, s * 0.48f); lineTo(s * 0.74f, s * 0.34f); lineTo(s * 0.76f, s * 0.68f); close()
    }
    drawPath(p, c)
    drawRect(c, Offset(s * 0.24f, s * 0.7f), Size(s * 0.52f, s * 0.07f))
    listOf(0.26f, 0.5f, 0.74f).forEachIndexed { i, x -> drawCircle(Color.White.copy(alpha = 0.7f), s * 0.03f, Offset(s * x, if (i == 1) s * 0.26f else s * 0.34f)) }
}
