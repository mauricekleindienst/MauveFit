package dev.mauve.fit.ui

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

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader {
            Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp)) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(Pill).background(Ink.surface).padding(4.dp)) {
                    listOf(L("Ziele", "Targets"), L("Rezepte", "Recipes"), L("Einkauf", "Shopping")).forEachIndexed { i, t ->
                        Box(
                            Modifier.weight(1f).clip(Pill).background(if (tab == i) Ink.accent else Ink.surface)
                                .clickable { Haptics.tick(); tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(t, color = if (tab == i) Ink.bg else Ink.muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        item { ScreenTitle(goal.label, L("Ernährung", "Nutrition")) }

        when (tab) {
            0 -> item {
                if (kg == null) Card(Modifier.fillMaxWidth()) {
                    Text(L("Trag dein Körpergewicht ein", "Enter your body weight"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        L("Die Tagesziele rechnet die App aus deinem Gewicht und deinem Ziel. Eintragen unter Verlauf, Körper.",
                            "Daily targets are worked out from your weight and goal. Add it under History, Body."),
                        color = Ink.muted,
                    )
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "${r.kcal} kcal · ${r.protein} g Protein · ${r.minutes} min",
                                    color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (goal in r.goals) Tag(L("passt", "fits"), Ink.accent)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                L("Hält ${r.keepsDays} Tage im Kühlschrank", "Keeps ${r.keepsDays} days in the fridge"),
                                Modifier.weight(1f), color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                            )
                            Stepper(L("Portionen", "Portions"), "$portions", Modifier.width(150.dp),
                                { App.store.setPortions(r.id, portions - 1) }) { App.store.setPortions(r.id, portions + 1) }
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
                        else L("$planned Portionen geplant · ${list.count { it.name in data.shoppingDone }} von ${list.size} im Korb",
                            "$planned portions planned · ${list.count { it.name in data.shoppingDone }} of ${list.size} in the basket"),
                        color = Ink.muted,
                    )
                }
                items(list, key = { "s" + it.name + it.unit }) { item ->
                    val done = item.name in data.shoppingDone
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink.surface)
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
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Ink.raised).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Ink.muted, style = MaterialTheme.typography.bodySmall)
    }
}
