package dev.mauve.fit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Nutrition

/**
 * "Nutrition today": calories and protein against the daily targets (when a body weight is known,
 * otherwise just the totals) and water in 250 ml glasses with a button for one more. 2x2 stacks
 * everything, 4x2 puts water in its own panel beside the food.
 */
class NutritionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data by App.store.data.collectAsState()
            Content(data)
        }
    }

    private class Today(val kcal: Int, val protein: Int, val waterMl: Int, val targets: Nutrition.Targets?) {
        val glasses get() = waterMl / GLASS_ML
        val glassGoal get() = targets?.waterMl?.div(GLASS_ML)?.takeIf { it > 0 } ?: 8
    }

    private fun today(d: AppData): Today {
        val key = todayKey()
        val food = d.food.filter { it.date == key }
        val targets = d.bodyWeights.firstOrNull()?.kg?.let { Nutrition.targets(it, d.profile.goal) }
        return Today(food.sumOf { it.kcal }, food.sumOf { it.protein }, d.water[key] ?: 0, targets)
    }

    @Composable
    private fun Content(d: AppData) {
        val context = LocalContext.current
        val t = today(d)
        // MainActivity may not know "nutrition" yet; it then just opens the app.
        val open = openApp(context, "nutrition")
        val wide = LocalSize.current.width >= WIDE.width
        if (wide) {
            Row(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(14.dp).clickable(open)) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Eyebrow(L("Ernährung heute", "Nutrition today"))
                    Spacer(GlanceModifier.defaultWeight())
                    Meter(L("Kalorien", "Calories"), t.kcal, t.targets?.kcal, "kcal", AccentInk)
                    Spacer(GlanceModifier.height(8.dp))
                    Meter("Protein", t.protein, t.targets?.protein, "g", Mauve)
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight().background(Surface).cornerRadius(16.dp).padding(12.dp),
                ) {
                    Text(L("Wasser", "Water"), style = style(Muted, 12.sp))
                    Text("${t.glasses}/${t.glassGoal}", style = style(TextColor, 22.sp, FontWeight.Bold), maxLines = 1)
                    Text(L("Gläser à 250 ml", "glasses of 250 ml"), style = style(Muted, 11.sp), maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    Pill(L("+ Glas", "+ glass"), actionRunCallback<AddWaterAction>(), GlanceModifier.fillMaxWidth())
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(12.dp).clickable(open)) {
                Eyebrow(L("Heute", "Today"))
                Spacer(GlanceModifier.height(4.dp))
                Meter(L("Kalorien", "Calories"), t.kcal, t.targets?.kcal, "kcal", AccentInk, compact = true)
                Spacer(GlanceModifier.height(4.dp))
                Meter("Protein", t.protein, t.targets?.protein, "g", Mauve, compact = true)
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(L("Wasser", "Water"), style = style(Muted, 11.sp), maxLines = 1)
                        Text("${t.glasses}/${t.glassGoal}", style = style(TextColor, 14.sp, FontWeight.Bold), maxLines = 1)
                    }
                    Pill(L("+ Glas", "+ glass"), actionRunCallback<AddWaterAction>())
                }
            }
        }
    }

    /** "1450 / 2400 kcal" with a bar, or the plain total without a target. */
    @Composable
    private fun Meter(label: String, value: Int, target: Int?, unit: String, color: Color, compact: Boolean = false) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, GlanceModifier.defaultWeight(), style = style(Muted, if (compact) 11.sp else 12.sp), maxLines = 1)
                Text(
                    if (target != null) "$value/$target $unit" else "$value $unit",
                    style = style(TextColor, if (compact) 12.sp else 14.sp, FontWeight.Bold), maxLines = 1,
                )
            }
            if (target != null && target > 0) {
                Spacer(GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    (value / target.toFloat()).coerceIn(0f, 1f), GlanceModifier.fillMaxWidth(),
                    color = ColorProvider(color), backgroundColor = ColorProvider(Raised),
                )
            }
        }
    }

    companion object {
        val SQUARE = DpSize(100.dp, 100.dp)
        val WIDE = DpSize(230.dp, 100.dp)
    }
}

class NutritionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutritionWidget()
}
