package dev.mauve.fit.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.FoodEntry
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Nutrition
import dev.mauve.fit.data.Nutrition.RecipeTag
import dev.mauve.fit.data.Stats
import dev.mauve.fit.system.Haptics
import java.time.LocalDate
import java.time.format.TextStyle

/** Today's food diary and water, meal-prep recipes with portions for the week, and the merged shopping list. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NutritionScreen(data: AppData, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(emptySet<RecipeTag>()) }
    val kg = data.bodyWeights.firstOrNull()?.kg
    val goal = data.profile.goal
    val targets = kg?.let { Nutrition.targets(it, goal) }
    val planned = data.mealPlan.values.sum()
    val today = LocalDate.now()
    val todayKey = today.toString()
    val context = LocalContext.current

    var askWeight by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var customEntry by remember { mutableStateOf(false) }
    // A new recipe is edited as an empty draft; null means the editor is closed.
    var editing by remember { mutableStateOf<Nutrition.Recipe?>(null) }
    var newRecipe by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Nutrition.Recipe?>(null) }
    if (askWeight) WeightDialog { askWeight = false }
    if (picking) RecipePickerDialog(data, onPick = { r ->
        App.store.addFood(FoodEntry(date = todayKey, name = r.name, kcal = r.kcal, protein = r.protein, carbs = r.carbs, fat = r.fat, recipeId = r.id))
        Haptics.success()
        picking = false
    }) { picking = false }
    if (customEntry) FoodEntryDialog(onSave = { name, kcal, protein, carbs, fat ->
        App.store.addFood(FoodEntry(date = todayKey, name = name, kcal = kcal, protein = protein, carbs = carbs, fat = fat))
        Haptics.success()
        customEntry = false
    }) { customEntry = false }
    if (newRecipe || editing != null) RecipeEditor(editing, onSave = { r ->
        App.store.saveOwnRecipe(r)
        Haptics.success()
        open = r.id
        newRecipe = false; editing = null
    }) { newRecipe = false; editing = null }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(L("Rezept löschen?", "Delete recipe?")) },
            text = { Text(L("„${r.name}“ wird entfernt, auch aus dem Wochenplan.", "\"${r.name}\" will be removed, also from the week plan.")) },
            confirmButton = {
                TextButton(onClick = { Haptics.warn(); App.store.deleteOwnRecipe(r.id); confirmDelete = null }) { Text(L("Löschen", "Delete"), color = Ink.red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
    }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(goal.label, L("Ernährung", "Nutrition")) }
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().clip(Pill).background(Ink.surface).padding(4.dp)) {
                    listOf(L("Heute", "Today"), L("Rezepte", "Recipes"), L("Einkauf", "Shopping")).forEachIndexed { i, t ->
                        Box(
                            Modifier.weight(1f).clip(Pill).background(if (tab == i) Ink.accent else Ink.surface)
                                .clickable { Haptics.tick(); tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t, color = if (tab == i) Ink.bg else Ink.muted, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    }
                }
            }
        }
        when (tab) {
            0 -> {
                val eaten = data.food.filter { it.date == todayKey }
                if (kg == null) item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(L("Trag dein Körpergewicht ein", "Enter your body weight"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            L("Die Tagesziele rechnet die App aus deinem Gewicht und deinem Ziel.",
                                "Daily targets are worked out from your weight and goal."),
                            color = Ink.muted,
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(L("Gewicht eintragen", "Enter weight"), Modifier.fillMaxWidth()) { askWeight = true }
                    }
                }
                item { DayTotals(eaten, targets, kg) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(L("Aus Rezept", "From recipe"), Modifier.weight(1f)) { picking = true }
                        GhostButton(L("Freier Eintrag", "Custom entry"), Modifier.weight(1f)) { customEntry = true }
                    }
                }
                if (eaten.isEmpty()) item {
                    Text(
                        L("Noch nichts eingetragen. Eine Rezept-Portion ist ein Tipp entfernt.", "Nothing logged yet. A recipe portion is one tap away."),
                        color = Ink.muted, modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    item { Eyebrow(L("Heute gegessen", "Eaten today"), Modifier.padding(top = 4.dp)) }
                    items(eaten.reversed(), key = { "f" + it.id }) { e -> FoodRow(e) }
                }
                item { WaterCard(data.water[todayKey] ?: 0, targets?.waterMl, todayKey) }
                item { WeekCard(data.food, targets?.kcal, today) }
                if (targets != null) item {
                    Text(
                        L("Richtwerte für gesunde Erwachsene, keine medizinische Beratung.", "Guide values for healthy adults, not medical advice."),
                        color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Restaurant, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Eyebrow(L("Tipp des Tages", "Tip of the day"))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(Nutrition.tip(goal, today.dayOfYear))
                    }
                }
            }

            1 -> {
                val shown = Nutrition.listFor(goal, data.ownRecipes).filter { r -> filters.all { r.has(it) } }
                item {
                    Text(
                        L("Passend zu „${goal.label}“. Wähle Portionen für die Woche, die Einkaufsliste rechnet mit.",
                            "Matched to \"${goal.label}\". Pick portions for the week; the shopping list adds them up."),
                        color = Ink.muted,
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeTag.entries.forEach { t ->
                            Chip(t.label, t in filters) { filters = if (t in filters) filters - t else filters + t }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L("${shown.size} ${if (shown.size == 1) "Rezept" else "Rezepte"}", "${shown.size} ${if (shown.size == 1) "recipe" else "recipes"}"),
                            Modifier.weight(1f), color = Ink.muted,
                        )
                        Row(
                            Modifier.pressable { Haptics.click(); newRecipe = true }.clip(Pill).background(Ink.raised)
                                .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Ink.text, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(L("Eigenes Rezept", "Own recipe"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (shown.isEmpty()) item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(L("Kein Rezept passt", "No recipe matches"), style = MaterialTheme.typography.titleLarge)
                        Text(L("Diese Filter zusammen ergeben nichts. Nimm einen davon weg.", "These filters together match nothing. Remove one of them."), color = Ink.muted)
                        Spacer(Modifier.height(14.dp))
                        GhostButton(L("Filter zurücksetzen", "Clear filters"), Modifier.fillMaxWidth()) { filters = emptySet() }
                    }
                }
                items(shown, key = { it.id }) { r ->
                    val portions = data.mealPlan[r.id] ?: 0
                    val expanded = open == r.id
                    Card(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = if (expanded) null else r.id }) {
                        // The tag sits on the title's first line, also when the title wraps.
                        Row(verticalAlignment = Alignment.Top) {
                            Text(r.name, Modifier.weight(1f).padding(end = 10.dp), style = MaterialTheme.typography.titleLarge)
                            if (r.own) Tag(L("eigenes", "own"), Ink.mauve, Modifier.padding(top = 2.dp))
                            else if (goal in r.goals) Tag(L("passt", "fits"), Ink.accent, Modifier.padding(top = 2.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            L("${r.kcal} kcal · ${r.protein} g Protein · ${r.minutes} min · hält ${r.keepsDays} Tage",
                                "${r.kcal} kcal · ${r.protein} g protein · ${r.minutes} min · keeps ${r.keepsDays} days"),
                            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                L("Portionen diese Woche", "Portions this week"),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink.text,
                            )
                            PortionCounter(portions, { App.store.setPortions(r.id, portions - 1) }) { App.store.setPortions(r.id, portions + 1) }
                        }
                        if (expanded) {
                            val tags = RecipeTag.entries.filter { r.has(it) }
                            if (tags.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tags.forEach { Tag(it.label) }
                                }
                            }
                            if (r.ingredients.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Eyebrow(L("Zutaten pro Portion", "Ingredients per portion"))
                                r.ingredients.forEach { ing ->
                                    Row(Modifier.padding(vertical = 3.dp)) {
                                        Text(ing.name, Modifier.weight(1f))
                                        Text(Nutrition.fmtAmount(Nutrition.ShoppingItem(ing.name, ing.amount, ing.unit)), color = Ink.muted)
                                    }
                                }
                            }
                            if (r.steps.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Eyebrow(L("Zubereitung", "Method"))
                                r.steps.forEachIndexed { i, step ->
                                    Row(Modifier.padding(vertical = 5.dp)) {
                                        Text("${i + 1}", Modifier.width(22.dp), color = Ink.accent, fontWeight = FontWeight.Bold)
                                        Text(step, Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                L("Pro Portion: ${r.carbs} g Kohlenhydrate · ${r.fat} g Fett", "Per portion: ${r.carbs} g carbs · ${r.fat} g fat"),
                                color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                            )
                            if (r.own) {
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GhostButton(L("Bearbeiten", "Edit"), Modifier.weight(1f)) { editing = r }
                                    GhostButton(L("Löschen", "Delete"), Modifier.weight(1f)) { confirmDelete = r }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val list = Nutrition.shoppingList(data.mealPlan, data.ownRecipes)
                val missing = list.filter { it.name !in data.shoppingDone }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (planned == 0) L("Noch keine Portionen geplant. Wähle sie unter Rezepte.", "No portions planned yet. Pick them under Recipes.")
                            else L("$planned ${if (planned == 1) "Portion" else "Portionen"} geplant · ${list.size - missing.size} von ${list.size} im Korb",
                                "$planned ${if (planned == 1) "portion" else "portions"} planned · ${list.size - missing.size} of ${list.size} in the basket"),
                            Modifier.weight(1f), color = Ink.muted,
                        )
                        if (missing.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            RoundButton(Icons.Rounded.Share, L("Liste teilen", "Share list"), size = 44.dp) {
                                val text = buildString {
                                    append(L("Einkaufsliste", "Shopping list"))
                                    missing.forEach { append("\n- ${it.name}: ${Nutrition.fmtAmount(it)}") }
                                }
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, L("Einkaufsliste teilen", "Share shopping list")))
                            }
                        }
                    }
                }
                items(list, key = { "s" + it.name + it.unit }) { item ->
                    val done = item.name in data.shoppingDone
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.surface)
                            .clickable { Haptics.tick(); App.store.toggleShopping(item.name) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(Pill).background(if (done) Ink.accent else Ink.surface)
                                .border(2.dp, if (done) Ink.accent else Ink.line, Pill),
                            contentAlignment = Alignment.Center,
                        ) { if (done) Icon(Icons.Rounded.Check, null, tint = Ink.bg, modifier = Modifier.size(16.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, Modifier.weight(1f), color = if (done) Ink.faint else Ink.text)
                        Text(Nutrition.fmtAmount(item), color = if (done) Ink.faint else Ink.muted)
                    }
                }
                if (planned > 0) item {
                    GhostButton(L("Neue Woche planen", "Plan a new week"), Modifier.fillMaxWidth()) { Haptics.warn(); App.store.clearMealPlan() }
                }
            }
        }
    }
}

/** Today's totals against the daily targets; without a body weight only the sums are shown. */
@Composable
private fun DayTotals(eaten: List<FoodEntry>, t: Nutrition.Targets?, kg: Double?) {
    val kcal = eaten.sumOf { it.kcal }
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(if (kg != null) L("Heute · Ziel bei ${Stats.fmtKg(kg)} kg", "Today · target at ${Stats.fmtKg(kg)} kg") else L("Heute", "Today"))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$kcal", style = MaterialTheme.typography.displaySmall, color = Ink.accent)
            Text(if (t != null) " / ${t.kcal} kcal" else " kcal", Modifier.padding(bottom = 5.dp), style = MaterialTheme.typography.titleMedium, color = Ink.muted)
        }
        if (t != null) {
            Spacer(Modifier.height(10.dp))
            Meter(kcal.toFloat() / t.kcal, Modifier.fillMaxWidth(), color = if (kcal > t.kcal * 1.1) Ink.coral else Ink.accent)
            Spacer(Modifier.height(6.dp))
            Text(
                if (kcal <= t.kcal) L("Noch ${t.kcal - kcal} kcal offen", "${t.kcal - kcal} kcal left")
                else L("${kcal - t.kcal} kcal über dem Ziel", "${kcal - t.kcal} kcal over target"),
                color = Ink.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroMeter(L("Protein", "Protein"), eaten.sumOf { it.protein }, t?.protein, Modifier.weight(1f))
            MacroMeter(L("Kohlenh.", "Carbs"), eaten.sumOf { it.carbs }, t?.carbs, Modifier.weight(1f))
            MacroMeter(L("Fett", "Fat"), eaten.sumOf { it.fat }, t?.fat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroMeter(label: String, grams: Int, target: Int?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Ink.raised).padding(12.dp)) {
        Text("$grams g", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(if (target != null) L("$label · von $target", "$label · of $target") else label, color = Ink.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (target != null) {
            Spacer(Modifier.height(8.dp))
            Meter(grams.toFloat() / target, Modifier.fillMaxWidth(), color = Ink.accent)
        }
    }
}

@Composable
private fun FoodRow(e: FoodEntry) {
    Row(
        Modifier.fillMaxWidth().clip(RowShape).background(Ink.surface).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                L("${e.kcal} kcal · ${e.protein} g Protein · ${e.carbs} g KH · ${e.fat} g Fett", "${e.kcal} kcal · ${e.protein} g protein · ${e.carbs} g carbs · ${e.fat} g fat"),
                color = Ink.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
        RoundButton(Icons.Rounded.DeleteOutline, L("Eintrag löschen", "Delete entry"), size = 38.dp) { Haptics.warn(); App.store.deleteFood(e.id) }
    }
}

/** "1,25 l" or "1.25 l", trailing zeros dropped. */
private fun fmtLitres(ml: Int) =
    String.format(java.util.Locale.ROOT, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.').let { if (I18n.german) it.replace('.', ',') else it } + " l"

/** Glasses of 250 ml for today; tapping an empty glass drinks one, the minus takes one back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterCard(ml: Int, target: Int?, day: String) {
    val glasses = ml / 250
    val slots = maxOf(target?.div(250) ?: 8, glasses + 1)
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Eyebrow(L("Wasser", "Water"), Modifier.weight(1f))
            Text(if (target != null) "${fmtLitres(ml)} / ${fmtLitres(target)}" else fmtLitres(ml), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(slots) { i ->
                val full = i < glasses
                Box(
                    Modifier.size(34.dp).clip(Pill).background(if (full) Ink.accent.copy(alpha = 0.16f) else Ink.raised)
                        .clickable(enabled = !full) { Haptics.tick(); App.store.addWater(day, 250) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.LocalDrink, null, tint = if (full) Ink.accent else Ink.faint, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(Icons.Rounded.Remove, L("Ein Glas weniger", "One glass less"), size = 52.dp, enabled = glasses > 0) { App.store.addWater(day, -250) }
            PrimaryButton(L("Ein Glas, 250 ml", "One glass, 250 ml"), Modifier.weight(1f), icon = Icons.Rounded.Add) { App.store.addWater(day, 250) }
        }
    }
}

/** Calories of the last seven days as bars, with the daily target as a dashed line. */
@Composable
private fun WeekCard(food: List<FoodEntry>, target: Int?, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = food.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.kcal } }
    val values = days.map { byDay[it.toString()] ?: 0 }
    val logged = values.filter { it > 0 }
    val scale = maxOf(values.max(), target ?: 0, 1) * 1.15f
    Card(Modifier.fillMaxWidth()) {
        Eyebrow(L("Letzte 7 Tage", "Last 7 days"))
        Spacer(Modifier.height(4.dp))
        Text(
            if (logged.isEmpty()) L("Noch keine Einträge in dieser Woche.", "No entries this week yet.")
            else L("Im Schnitt ${logged.sum() / logged.size} kcal an ${logged.size} ${if (logged.size == 1) "Tag" else "Tagen"} mit Einträgen",
                "Average ${logged.sum() / logged.size} kcal over ${logged.size} logged ${if (logged.size == 1) "day" else "days"}"),
            color = Ink.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    val h by animateFloatAsState(v / scale, tween(600), label = "kcal")
                    val over = target != null && v > target * 1.1
                    Box(
                        Modifier.weight(1f).fillMaxHeight(h.coerceAtLeast(0.03f)).clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    v == 0 -> Ink.raised
                                    i == values.lastIndex -> Ink.accent
                                    over -> Ink.coral.copy(alpha = 0.7f)
                                    else -> Ink.lineStrong
                                }
                            )
                    )
                }
            }
            if (target != null) {
                val color = Ink.muted
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height * (1f - target / scale)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { d ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, I18n.locale).removeSuffix(".").take(2),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = if (d == today) Ink.text else Ink.faint, maxLines = 1,
                )
            }
        }
        if (target != null) {
            Spacer(Modifier.height(8.dp))
            Text(L("Gestrichelt: Ziel von $target kcal", "Dashed: target of $target kcal"), color = Ink.faint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One capsule: minus, count, plus. Outlined in the accent once something is planned. */
@Composable
private fun PortionCounter(count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(Pill).background(Ink.raised)
            .border(1.dp, if (count > 0) Ink.accent else Ink.line, Pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(Pill).clickable(enabled = count > 0) { Haptics.tick(); onMinus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, L("Weniger", "Less"), tint = if (count > 0) Ink.text else Ink.faint, modifier = Modifier.size(18.dp))
        }
        Text("$count", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = if (count > 0) Ink.accent else Ink.text)
        Box(Modifier.size(40.dp).clip(Pill).clickable { Haptics.tick(); onPlus() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, L("Mehr", "More"), tint = Ink.text, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WeightDialog(onDone: () -> Unit) {
    var kg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(L("Körpergewicht", "Body weight")) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                kg, { kg = it }, label = { Text("kg") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..350.0 }?.let(App.store::addBodyWeight)
                Haptics.success()
                onDone()
            }) { Text(L("Speichern", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}
