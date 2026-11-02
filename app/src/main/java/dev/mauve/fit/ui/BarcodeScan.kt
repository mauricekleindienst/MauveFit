package dev.mauve.fit.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.mauve.fit.App
import dev.mauve.fit.data.FoodEntry
import dev.mauve.fit.data.FoodLookup
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.L
import dev.mauve.fit.data.ScannedFood
import dev.mauve.fit.system.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Everything a product dialog starts from; null values are asked from the user. */
internal data class Draft(
    val code: String,
    val name: String?,
    val brand: String?,
    val kcal100: Double?,
    val protein100: Double?,
    val carbs100: Double?,
    val fat100: Double?,
    val servingG: Double?,
) {
    val complete get() = name != null && kcal100 != null && protein100 != null && carbs100 != null && fat100 != null
}

internal sealed interface Step {
    /** Typing the code by hand, with [reason] saying why the camera isn't used. */
    data class Manual(val reason: String?) : Step
    data class Looking(val code: String) : Step
    data class Problem(val code: String, val result: FoodLookup.Result) : Step
    data class Amount(val draft: Draft) : Step
}

/**
 * One scan from start to finish: Google's code scanner (Play services draws the camera screen, so
 * the app needs no camera permission), the lookup, and the amount dialog. Products are remembered
 * after the first lookup, so the same code works offline afterwards.
 */
@Stable
class BarcodeFlow internal constructor(private val scope: CoroutineScope) {
    internal var step by mutableStateOf<Step?>(null)
    private var job: Job? = null

    fun scan(context: Context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) step = Step.Manual(L("Der Code war nicht lesbar.", "The code could not be read."))
                else {
                    Haptics.tick()
                    lookup(FoodLookup.normalize(raw, upcE = barcode.format == Barcode.FORMAT_UPC_E))
                }
            }
            .addOnFailureListener { e ->
                when ((e as? MlKitException)?.errorCode) {
                    MlKitException.CANCELLED -> Unit
                    MlKitException.UNAVAILABLE -> {
                        // The scanner is a Play services module; ask for it now so the next try works.
                        runCatching {
                            ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                        }
                        step = Step.Manual(
                            L("Der Scanner wird gerade von Google Play geladen. Versuch es gleich noch einmal oder tipp die Nummer unter dem Strichcode ein.",
                                "Google Play is still downloading the scanner. Try again in a moment, or type the number under the barcode.")
                        )
                    }
                    else -> step = Step.Manual(
                        L("Der Scanner ließ sich nicht starten. Tipp die Nummer unter dem Strichcode ein.",
                            "The scanner could not start. Type the number under the barcode instead.")
                    )
                }
            }
    }

    /** Logs a remembered product again; the amount is still asked. */
    fun open(food: ScannedFood) {
        step = Step.Amount(Draft(food.code, food.name, food.brand.ifBlank { null }, food.kcal100, food.protein100, food.carbs100, food.fat100, food.servingG))
    }

    internal fun lookup(code: String) {
        App.store.data.value.scannedFoods.firstOrNull { it.code == code }?.let { open(it); return }
        step = Step.Looking(code)
        job?.cancel()
        job = scope.launch {
            step = when (val r = FoodLookup.lookup(code)) {
                is FoodLookup.Result.Found -> r.product.let { p ->
                    Step.Amount(Draft(code, p.name, p.brand, p.kcal100, p.protein100, p.carbs100, p.fat100, p.servingG))
                }
                else -> Step.Problem(code, r)
            }
        }
    }

    internal fun close() {
        job?.cancel()
        step = null
    }
}

@Composable
fun rememberBarcodeFlow(): BarcodeFlow {
    val scope = rememberCoroutineScope()
    return remember(scope) { BarcodeFlow(scope) }
}

/** The dialogs of [flow]; whatever it adds goes into the diary for [day]. */
@Composable
fun BarcodeDialogs(flow: BarcodeFlow, day: String) {
    when (val s = flow.step) {
        null -> Unit
        is Step.Manual -> ManualCodeDialog(s.reason, onLookup = flow::lookup, onDismiss = flow::close)
        is Step.Looking -> AlertDialog(
            onDismissRequest = flow::close,
            title = { Text(L("Produkt wird gesucht", "Looking up the product")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Ink.accent, strokeWidth = 3.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(L("Code ${s.code}", "Code ${s.code}"), color = Ink.muted)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = flow::close) { Text(L("Abbrechen", "Cancel")) } },
            containerColor = Ink.raised,
        )
        is Step.Problem -> ProblemDialog(s, flow)
        is Step.Amount -> AmountDialog(s.draft, onAdd = { food, grams ->
            App.store.rememberScan(food)
            val f = grams / 100.0
            App.store.addFood(
                FoodEntry(
                    // Saved to the day it is now, even if the dialog stayed open over midnight.
                    date = maxOf(day, java.time.LocalDate.now().toString()), name = "${food.name}, ${fmt(grams)} g",
                    kcal = (food.kcal100 * f).roundToInt(), protein = (food.protein100 * f).roundToInt(),
                    carbs = (food.carbs100 * f).roundToInt(), fat = (food.fat100 * f).roundToInt(),
                )
            )
            Haptics.success()
            flow.close()
        }, onDismiss = flow::close)
    }
}

@Composable
private fun ProblemDialog(s: Step.Problem, flow: BarcodeFlow) {
    val notFound = s.result == FoodLookup.Result.NotFound
    val selfEntry = { flow.step = Step.Amount(Draft(s.code, null, null, null, null, null, null, null)) }
    AlertDialog(
        onDismissRequest = flow::close,
        title = {
            Text(
                when (s.result) {
                    FoodLookup.Result.NotFound -> L("Produkt nicht gefunden", "Product not found")
                    FoodLookup.Result.Offline -> L("Keine Verbindung", "No connection")
                    else -> L("Suche nicht möglich", "Lookup failed")
                }
            )
        },
        text = {
            Text(
                when (s.result) {
                    FoodLookup.Result.NotFound -> L(
                        "Open Food Facts kennt den Code ${s.code} noch nicht. Trag die Werte von der Packung ein, dann merkt sich die App das Produkt.",
                        "Open Food Facts doesn't know the code ${s.code} yet. Enter the values from the package and the app remembers the product.",
                    )
                    FoodLookup.Result.Offline -> L(
                        "Zum Nachschlagen braucht die App einmal Internet. Schon gescannte Produkte gehen auch offline.",
                        "Looking a product up needs the internet once. Products you scanned before also work offline.",
                    )
                    else -> L(
                        "Open Food Facts antwortet gerade nicht. Versuch es gleich noch einmal oder trag die Werte selbst ein.",
                        "Open Food Facts isn't answering right now. Try again shortly or enter the values yourself.",
                    )
                },
                color = Ink.muted,
            )
        },
        confirmButton = {
            if (notFound) TextButton(onClick = selfEntry) { Text(L("Werte eintragen", "Enter values")) }
            else TextButton(onClick = { flow.lookup(s.code) }) { Text(L("Nochmal", "Retry")) }
        },
        dismissButton = {
            Row {
                if (!notFound) TextButton(onClick = selfEntry) { Text(L("Selbst eintragen", "Enter values")) }
                TextButton(onClick = flow::close) { Text(L("Abbrechen", "Cancel")) }
            }
        },
        containerColor = Ink.raised,
    )
}

@Composable
private fun ManualCodeDialog(reason: String?, onLookup: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val digits = code.filter(Char::isDigit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Code eintippen", "Type the code")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reason != null) Text(reason, color = Ink.muted)
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit).take(13) }, Modifier.fillMaxWidth(),
                    label = { Text(L("Nummer unter dem Strichcode", "Number under the barcode")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(L("8, 12 oder 13 Ziffern.", "8, 12 or 13 digits."), color = Ink.faint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = FoodLookup.validManual(digits), onClick = { onLookup(FoodLookup.normalize(digits)) }) { Text(L("Nachschlagen", "Look up")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** One decimal at most, trailing zero dropped, comma in German. */
private fun fmt(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    val s = if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    return if (I18n.german) s.replace('.', ',') else s
}

private fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/**
 * Product name and values per 100 g, an amount in grams and what that amount comes to. Missing
 * values (or a product the database doesn't know) are filled in here from the package.
 */
@Composable
private fun AmountDialog(draft: Draft, onAdd: (ScannedFood, Double) -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf(!draft.complete) }
    var name by remember { mutableStateOf(draft.name.orEmpty()) }
    var kcal by remember { mutableStateOf(draft.kcal100?.let(::fmt).orEmpty()) }
    var protein by remember { mutableStateOf(draft.protein100?.let(::fmt).orEmpty()) }
    var carbs by remember { mutableStateOf(draft.carbs100?.let(::fmt).orEmpty()) }
    var fat by remember { mutableStateOf(draft.fat100?.let(::fmt).orEmpty()) }
    var grams by remember { mutableStateOf(fmt(draft.servingG ?: 100.0)) }

    val k = parse(kcal)?.takeIf { it <= 1000 }
    val p = parse(protein)?.takeIf { it <= 100 }
    // Carbs and fat may stay blank, like in a custom entry.
    val c = if (carbs.isBlank()) 0.0 else parse(carbs)?.takeIf { it <= 100 }
    val f = if (fat.isBlank()) 0.0 else parse(fat)?.takeIf { it <= 100 }
    val g = parse(grams)?.takeIf { it in 1.0..5000.0 }
    val valid = name.isNotBlank() && k != null && p != null && c != null && f != null && g != null
    val factor = (g ?: 0.0) / 100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.name ?: L("Neues Produkt", "New product"), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (draft.brand != null) Text(draft.brand, color = Ink.muted)
                if (editing) {
                    if (!draft.complete) Text(
                        if (draft.name == null && draft.kcal100 == null) L("Trag die Werte von der Packung ein, pro 100 g.", "Enter the values from the package, per 100 g.")
                        else L("Open Food Facts kennt nicht alle Werte. Ergänze sie von der Packung, pro 100 g.",
                            "Open Food Facts is missing some values. Fill them in from the package, per 100 g."),
                        color = Ink.muted, style = MaterialTheme.typography.bodySmall,
                    )
                    if (draft.name == null) OutlinedTextField(
                        name, { name = it }, Modifier.fillMaxWidth(), label = { Text(L("Name", "Name")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(kcal, { kcal = it }, Modifier.weight(1f), label = { Text(L("kcal / 100 g", "kcal / 100 g")) }, singleLine = true, keyboardOptions = numbers)
                        OutlinedTextField(protein, { protein = it }, Modifier.weight(1f), label = { Text(L("Protein g", "Protein g")) }, singleLine = true, keyboardOptions = numbers)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(carbs, { carbs = it }, Modifier.weight(1f), label = { Text(L("KH g", "Carbs g")) }, singleLine = true, keyboardOptions = numbers)
                        OutlinedTextField(fat, { fat = it }, Modifier.weight(1f), label = { Text(L("Fett g", "Fat g")) }, singleLine = true, keyboardOptions = numbers)
                    }
                } else {
                    Column {
                        Eyebrow(L("Pro 100 g", "Per 100 g"))
                        Text(
                            L("${fmt(k ?: 0.0)} kcal · ${fmt(p ?: 0.0)} g Protein · ${fmt(c ?: 0.0)} g KH · ${fmt(f ?: 0.0)} g Fett",
                                "${fmt(k ?: 0.0)} kcal · ${fmt(p ?: 0.0)} g protein · ${fmt(c ?: 0.0)} g carbs · ${fmt(f ?: 0.0)} g fat"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            L("Werte ändern", "Edit values"), color = Ink.accent, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp).clip(Pill).pressable { Haptics.tick(); editing = true }.padding(vertical = 4.dp),
                        )
                    }
                }

                Eyebrow(L("Menge", "Amount"), Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundButton(Icons.Rounded.Remove, L("10 g weniger", "10 g less"), size = 44.dp, enabled = (g ?: 0.0) > 10) {
                        grams = fmt(((g ?: 100.0) - 10).coerceAtLeast(1.0))
                    }
                    OutlinedTextField(
                        grams, { grams = it }, Modifier.weight(1f), singleLine = true,
                        suffix = { Text("g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    RoundButton(Icons.Rounded.Add, L("10 g mehr", "10 g more"), size = 44.dp) {
                        grams = fmt(((g ?: 0.0) + 10).coerceAtMost(5000.0))
                    }
                }
                draft.servingG?.let { serving ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(L("1 Portion, ${fmt(serving)} g", "1 serving, ${fmt(serving)} g"), g == serving) { grams = fmt(serving) }
                        if (serving != 100.0) Chip("100 g", g == 100.0) { grams = "100" }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Amount("${((k ?: 0.0) * factor).roundToInt()}", "kcal", Modifier.weight(1f))
                    Amount("${fmt((p ?: 0.0) * factor)} g", L("Protein", "Protein"), Modifier.weight(1f))
                    Amount("${fmt((c ?: 0.0) * factor)} g", L("KH", "Carbs"), Modifier.weight(1f))
                    Amount("${fmt((f ?: 0.0) * factor)} g", L("Fett", "Fat"), Modifier.weight(1f))
                }
                Text(L("Daten: Open Food Facts", "Data: Open Food Facts"), color = Ink.faint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onAdd(ScannedFood(draft.code, name.trim(), k!!, p!!, c!!, f!!, draft.servingG, draft.brand.orEmpty()), g!!)
            }) { Text(L("Hinzufügen", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Abbrechen", "Cancel")) } },
        containerColor = Ink.raised,
    )
}

/** One computed value for the chosen amount. */
@Composable
private fun Amount(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Ink.surface).padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, color = Ink.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Recently scanned products; a tap opens the amount dialog again, no network needed. */
@Composable
fun RecentScans(foods: List<ScannedFood>, onPick: (ScannedFood) -> Unit) {
    Column {
        Eyebrow(L("Zuletzt gescannt", "Recently scanned"))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            foods.take(12).forEach { food ->
                Column(
                    Modifier.widthIn(max = 200.dp).pressable { Haptics.click(); onPick(food) }.clip(RowShape).background(Ink.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(L("${food.kcal100.roundToInt()} kcal pro 100 g", "${food.kcal100.roundToInt()} kcal per 100 g"),
                        color = Ink.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
