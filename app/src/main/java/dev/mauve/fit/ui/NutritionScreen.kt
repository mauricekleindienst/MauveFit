package dev.mauve.fit.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Nutrition
import dev.mauve.fit.system.Haptics
import java.time.LocalDate

/** Daily targets, meal-prep recipes with portions for the week, and the merged shopping list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NutritionScreen(data: AppData, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val kg = data.bodyWeights.firstOrNull()?.kg
    val goal = data.profile.goal
    val planned = data.mealPlan.values.sum()
    var askWeight by remember { mutableStateOf(false) }
    if (askWeight) WeightDialog { askWeight = false }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(goal.label, L("Ernährung", "Nutrition")) }
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(Ink.bg).background(Grain).padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().clip(Pill).background(Ink.surface).padding(4.dp)) {
                    listOf(L("Ziele", "Targets"), L("Rezepte", "Recipes"), L("Einkauf", "Shopping")).forEachIndexed { i, t ->
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
            0 -> item {
                if (kg == null) Card(Modifier.fillMaxWidth()) {
                    Text(L("Trag dein Körpergewicht ein", "Enter your body weight"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        L("Die Tagesziele rechnet die App aus deinem Gewicht und deinem Ziel.",
                            "Daily targets are worked out from your weight and goal."),
                        color = Ink.muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(L("Gewicht eintragen", "Enter weight"), Modifier.fillMaxWidth()) { askWeight = true }
                } else {
                    val t = Nutrition.targets(kg, goal)
                    Card(Modifier.fillMaxWidth()) {
                        Eyebrow(L("Pro Tag · bei ${dev.mauve.fit.data.Stats.fmtKg(kg)} kg", "Per day · at ${dev.mauve.fit.data.Stats.fmtKg(kg)} kg"))
                        Spacer(Modifier.height(6.dp))
                        Text("${t.kcal} kcal", style = MaterialTheme.typography.displaySmall, color = Ink.accent)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Macro(L("Protein", "Protein"), "${t.protein} g", Modifier.weight(1f))
                            Macro(L("Kohlenh.", "Carbs"), "${t.carbs} g", Modifier.weight(1f))
                            Macro(L("Fett", "Fat"), "${t.fat} g", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(L("Wasser: ${t.waterMl / 1000.0} l", "Water: ${t.waterMl / 1000.0} l").replace('.', if (dev.mauve.fit.data.I18n.german) ',' else '.'), color = Ink.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        L("Richtwerte für gesunde Erwachsene, keine medizinische Beratung.", "Guide values for healthy adults, not medical advice."),
                        color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Restaurant, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Eyebrow(L("Tipp des Tages", "Tip of the day"))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(Nutrition.tip(goal, LocalDate.now().dayOfYear))
                }
            }

            1 -> {
                item {
                    Text(
                        L("Passend zu „${goal.label}“. Wähle Portionen für die Woche, die Einkaufsliste rechnet mit.",
                            "Matched to \"${goal.label}\". Pick portions for the week; the shopping list adds them up."),
                        color = Ink.muted,
                    )
                }
                items(Nutrition.forGoal(goal) + Nutrition.recipes.filter { goal !in it.goals }, key = { it.id }) { r ->
                    val portions = data.mealPlan[r.id] ?: 0
                    val expanded = open == r.id
                    Card(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = if (expanded) null else r.id }) {
                        // The tag sits on the title's first line, also when the title wraps.
                        Row(verticalAlignment = Alignment.Top) {
                            Text(r.name, Modifier.weight(1f).padding(end = 10.dp), style = MaterialTheme.typography.titleLarge)
                            if (goal in r.goals) Tag(L("passt", "fits"), Ink.accent, Modifier.padding(top = 2.dp))
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
                            Spacer(Modifier.height(14.dp))
                            Eyebrow(L("Zutaten pro Portion", "Ingredients per portion"))
                            r.ingredients.forEach { ing ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(ing.name, Modifier.weight(1f))
                                    Text(Nutrition.fmtAmount(Nutrition.ShoppingItem(ing.name, ing.amount, ing.unit)), color = Ink.muted)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Eyebrow(L("Zubereitung", "Method"))
                            r.steps.forEachIndexed { i, step ->
                                Row(Modifier.padding(vertical = 5.dp)) {
                                    Text("${i + 1}", Modifier.width(22.dp), color = Ink.accent, fontWeight = FontWeight.Bold)
                                    Text(step, Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                L("Pro Portion: ${r.carbs} g Kohlenhydrate · ${r.fat} g Fett", "Per portion: ${r.carbs} g carbs · ${r.fat} g fat"),
                                color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            else -> {
                val list = Nutrition.shoppingList(data.mealPlan)
                item {
                    Text(
                        if (planned == 0) L("Noch keine Portionen geplant. Wähle sie unter Rezepte.", "No portions planned yet. Pick them under Recipes.")
                        else L("$planned ${if (planned == 1) "Portion" else "Portionen"} geplant · ${list.count { it.name in data.shoppingDone }} von ${list.size} im Korb",
                            "$planned ${if (planned == 1) "portion" else "portions"} planned · ${list.count { it.name in data.shoppingDone }} of ${list.size} in the basket"),
                        color = Ink.muted,
                    )
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

@Composable
private fun Macro(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Ink.raised).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Ink.muted, style = MaterialTheme.typography.bodySmall)
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(L("Körpergewicht", "Body weight")) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                kg, { kg = it }, label = { Text("kg") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                kg.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..350.0 }?.let(App.store::addBodyWeight)
                Haptics.success()
                onDone()
            }) { Text(L("Speichern", "Save")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}
