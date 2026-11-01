package dev.mauve.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Nutrition
import dev.mauve.fit.data.Nutrition.RecipeTag
import dev.mauve.fit.data.newId
import dev.mauve.fit.system.Haptics

/** Whole grams or kcal from a text field; blank counts as 0, anything unreadable as null. */
private fun parseInt(s: String): Int? = if (s.isBlank()) 0 else s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { Math.round(it).toInt() }

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val textKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** One portion of a recipe for today's diary; planned recipes first, with a search field for the rest. */
@Composable
fun RecipePickerDialog(data: AppData, onPick: (Nutrition.Recipe) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember(data.ownRecipes, data.profile.goal) { Nutrition.listFor(data.profile.goal, data.ownRecipes) }
    val shown = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.id in data.mealPlan }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Rezept-Portion eintragen", "Log a recipe portion")) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(L("Suchen", "Search")) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    if (shown.isEmpty()) item { Text(L("Nichts gefunden.", "Nothing found."), color = Ink.muted, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(shown, key = { it.id }) { r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RowShape).clickable { onPick(r) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(L("${r.kcal} kcal · ${r.protein} g Protein", "${r.kcal} kcal · ${r.protein} g protein"),
                                    color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (r.id in data.mealPlan) Tag(L("geplant", "planned"), Ink.accent, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** Anything without a recipe: name, calories and protein, carbs and fat optional. */
@Composable
fun FoodEntryDialog(onSave: (name: String, kcal: Int, protein: Int, carbs: Int, fat: Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val values = listOf(kcal, protein, carbs, fat).map(::parseInt)
    val valid = name.isNotBlank() && kcal.isNotBlank() && protein.isNotBlank() && values.all { it != null } && (values[0] ?: 0) in 1..5000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Freier Eintrag", "Custom entry")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(L("Was?", "What?")) }, singleLine = true, keyboardOptions = textKeys)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(kcal, { kcal = it }, Modifier.weight(1f), label = { Text("kcal") }, singleLine = true, keyboardOptions = numberKeys)
                    OutlinedTextField(protein, { protein = it }, Modifier.weight(1f), label = { Text(L("Protein g", "Protein g")) }, singleLine = true, keyboardOptions = numberKeys)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(carbs, { carbs = it }, Modifier.weight(1f), label = { Text(L("KH g", "Carbs g")) }, singleLine = true, keyboardOptions = numberKeys)
                    OutlinedTextField(fat, { fat = it }, Modifier.weight(1f), label = { Text(L("Fett g", "Fat g")) }, singleLine = true, keyboardOptions = numberKeys)
                }
                Text(L("Kohlenhydrate und Fett sind optional.", "Carbs and fat are optional."), color = Ink.faint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), values[0]!!, values[1]!!, values[2]!!, values[3]!!) }) { Text(L("Eintragen", "Log it")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** Editable ingredient line; amount stays text so half-typed numbers survive. */
private class IngredientDraft(name: String, amount: String, unit: String) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var unit by mutableStateOf(unit)
    val key = newId()
}

private class StepDraft(text: String) {
    var text by mutableStateOf(text)
    val key = newId()
}

/** Labels the user sets; Quick and HighProtein come from the minutes and protein by themselves. */
private val settableTags = listOf(RecipeTag.Vegetarian, RecipeTag.Vegan, RecipeTag.Breakfast, RecipeTag.Snack)

private fun fmtNumber(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** Full-screen editor for an own recipe; [initial] null creates a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeEditor(initial: Nutrition.Recipe?, onSave: (Nutrition.Recipe) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.de.orEmpty()) }
    var kcal by remember { mutableStateOf(initial?.kcal?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(initial?.protein?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(initial?.carbs?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(initial?.fat?.toString().orEmpty()) }
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 20) }
    var keeps by remember { mutableIntStateOf(initial?.keepsDays ?: 3) }
    var tags by remember { mutableStateOf(initial?.tags.orEmpty()) }
    val ingredients = remember {
        mutableStateListOf<IngredientDraft>().apply {
            initial?.ingredients?.forEach { add(IngredientDraft(it.de, fmtNumber(it.amount), it.unit)) }
            if (isEmpty()) add(IngredientDraft("", "", "g"))
        }
    }
    val steps = remember {
        mutableStateListOf<StepDraft>().apply {
            initial?.stepsDe?.forEach { add(StepDraft(it)) }
            if (isEmpty()) add(StepDraft(""))
        }
    }
    var tried by remember { mutableStateOf(false) }
    val macros = listOf(kcal, protein, carbs, fat).map(::parseInt)
    val problem = when {
        name.isBlank() -> L("Gib dem Rezept einen Namen.", "Give the recipe a name.")
        macros.any { it == null } || (macros[0] ?: 0) !in 1..5000 -> L("Trag die Kalorien pro Portion ein.", "Enter the calories per portion.")
        else -> null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.bg).systemBarsPadding().imePadding()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (initial == null) L("Neues Rezept", "New recipe") else L("Rezept bearbeiten", "Edit recipe"),
                            Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                        )
                        RoundButton(Icons.Rounded.Close, L("Schließen", "Close"), size = 44.dp, onClick = onDismiss)
                    }
                }
                item {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(L("Name", "Name")) }, singleLine = true, keyboardOptions = textKeys)
                }
                item {
                    Eyebrow(L("Pro Portion", "Per portion"), Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(kcal, { kcal = it }, Modifier.weight(1f), label = { Text("kcal") }, singleLine = true, keyboardOptions = numberKeys)
                        OutlinedTextField(protein, { protein = it }, Modifier.weight(1f), label = { Text(L("Protein g", "Protein g")) }, singleLine = true, keyboardOptions = numberKeys)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(carbs, { carbs = it }, Modifier.weight(1f), label = { Text(L("KH g", "Carbs g")) }, singleLine = true, keyboardOptions = numberKeys)
                        OutlinedTextField(fat, { fat = it }, Modifier.weight(1f), label = { Text(L("Fett g", "Fat g")) }, singleLine = true, keyboardOptions = numberKeys)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper(L("Minuten", "Minutes"), "$minutes", Modifier.weight(1f),
                            onMinus = { minutes = (minutes - 5).coerceAtLeast(5) }, onPlus = { minutes = (minutes + 5).coerceAtMost(240) })
                        Stepper(L("Hält Tage", "Keeps days"), "$keeps", Modifier.weight(1f),
                            onMinus = { keeps = (keeps - 1).coerceAtLeast(1) }, onPlus = { keeps = (keeps + 1).coerceAtMost(14) })
                    }
                }
                item {
                    Eyebrow(L("Eigenschaften", "Labels"), Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        settableTags.forEach { t -> Chip(t.label, t in tags) { tags = if (t in tags) tags - t else tags + t } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L("Schnell und Viel Protein ergeben sich aus Zeit und Protein.", "Quick and High protein follow from the time and protein."),
                        color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                    )
                }
                item { Eyebrow(L("Zutaten pro Portion", "Ingredients per portion"), Modifier.padding(top = 4.dp)) }
                items(ingredients, key = { it.key }) { ing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(ing.name, { ing.name = it }, Modifier.weight(1.7f), label = { Text(L("Zutat", "Ingredient")) },
                            singleLine = true, keyboardOptions = textKeys)
                        OutlinedTextField(ing.amount, { ing.amount = it }, Modifier.weight(1f), label = { Text(L("Menge", "Amount")) },
                            singleLine = true, keyboardOptions = numberKeys)
                        // Tapping the unit steps through g, ml, pieces and teaspoons.
                        Box(
                            Modifier.padding(top = 6.dp).width(48.dp).height(44.dp).clip(Pill).background(Ink.surface)
                                .clickable { Haptics.tick(); ing.unit = Nutrition.units[(Nutrition.units.indexOf(ing.unit) + 1) % Nutrition.units.size] },
                            contentAlignment = Alignment.Center,
                        ) { Text(Nutrition.unitLabel(ing.unit), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                        RoundButton(Icons.Rounded.Close, L("Zutat entfernen", "Remove ingredient"), Modifier.padding(top = 6.dp), size = 36.dp) {
                            ingredients.remove(ing)
                        }
                    }
                }
                item {
                    GhostButton(L("Zutat hinzufügen", "Add ingredient"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
                        ingredients.add(IngredientDraft("", "", "g"))
                    }
                }
                item { Eyebrow(L("Zubereitung", "Method"), Modifier.padding(top = 4.dp)) }
                itemsIndexed(steps, key = { _, s -> s.key }) { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${i + 1}", Modifier.width(18.dp), color = Ink.accent, fontWeight = FontWeight.Bold)
                        OutlinedTextField(step.text, { step.text = it }, Modifier.weight(1f), label = { Text(L("Schritt", "Step")) }, keyboardOptions = textKeys)
                        RoundButton(Icons.Rounded.Close, L("Schritt entfernen", "Remove step"), size = 36.dp) { steps.remove(step) }
                    }
                }
                item {
                    GhostButton(L("Schritt hinzufügen", "Add step"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) { steps.add(StepDraft("")) }
                }
                item {
                    if (tried && problem != null) {
                        Text(problem, color = Ink.red, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    PrimaryButton(L("Rezept speichern", "Save recipe"), Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        tried = true
                        if (problem != null) { Haptics.warn(); return@PrimaryButton }
                        val title = name.trim()
                        val stepTexts = steps.map { it.text.trim() }.filter { it.isNotEmpty() }
                        onSave(
                            Nutrition.Recipe(
                                id = initial?.id ?: "own-${newId()}",
                                de = title, en = title,
                                goals = Nutrition.allGoals,
                                minutes = minutes, kcal = macros[0]!!, protein = macros[1]!!, carbs = macros[2]!!, fat = macros[3]!!,
                                keepsDays = keeps,
                                tags = tags,
                                ingredients = ingredients.mapNotNull { d ->
                                    val n = d.name.trim()
                                    val amount = d.amount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
                                    if (n.isEmpty() || amount == null) null else Nutrition.Ingredient(n, n, amount, d.unit)
                                },
                                stepsDe = stepTexts, stepsEn = stepTexts,
                                own = true,
                            )
                        )
                    }
                    if (!tried) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            L("Zutaten ohne Menge werden nicht gespeichert.", "Ingredients without an amount are not saved."),
                            color = Ink.faint, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
