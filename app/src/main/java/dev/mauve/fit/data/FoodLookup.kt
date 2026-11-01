package dev.mauve.fit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A product scanned once, kept so it can be logged again offline. Values are per 100 g. */
@Serializable
data class ScannedFood(
    val code: String,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double = 0.0,
    val fat100: Double = 0.0,
    /** One serving in grams, when the package states it. */
    val servingG: Double? = null,
    val brand: String = "",
)

/** Barcode lookup on Open Food Facts, a free and open food database. */
object FoodLookup {

    /** What the database knows; any value can be missing and is then asked from the user. */
    data class Product(
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

    sealed interface Result {
        data class Found(val product: Product) : Result
        data object NotFound : Result
        /** No connection, or the request timed out. */
        data object Offline : Result
        /** The server answered with an error or something unreadable. */
        data object Failed : Result
    }

    @Serializable
    private class Response(val status: Int = 0, val product: OffProduct? = null)

    @Serializable
    private class OffProduct(
        @SerialName("product_name") val name: String? = null,
        @SerialName("product_name_de") val nameDe: String? = null,
        val brands: String? = null,
        val nutriments: JsonObject = JsonObject(emptyMap()),
        // Usually a number, in older entries a string; read leniently below.
        @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
        @SerialName("serving_size") val servingSize: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }

    private const val FIELDS = "product_name,product_name_de,brands,nutriments,serving_quantity,serving_size"

    suspend fun lookup(code: String): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS")
            .header("User-Agent", "MauveFit/1.8 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                // An unknown code comes back as 404 with status 0 in the body.
                if (response.code == 404) return@withContext Result.NotFound
                if (!response.isSuccessful) return@withContext Result.Failed
                val body = json.decodeFromString<Response>(response.body?.string().orEmpty())
                val p = body.product
                if (body.status != 1 || p == null) Result.NotFound else Result.Found(p.toProduct(code))
            }
        } catch (_: IOException) {
            Result.Offline
        } catch (_: Exception) {
            Result.Failed
        }
    }

    private fun OffProduct.toProduct(code: String): Product {
        val n = nutriments
        val kcal = n.number("energy-kcal_100g") ?: (n.number("energy-kj_100g") ?: n.number("energy_100g"))?.let { it / 4.184 }
        val de = nameDe?.trim()?.takeIf { it.isNotEmpty() }
        val any = name?.trim()?.takeIf { it.isNotEmpty() }
        return Product(
            code = code,
            name = if (I18n.german) de ?: any else any ?: de,
            brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            kcal100 = kcal?.takeIf { it in 0.0..1000.0 },
            protein100 = n.number("proteins_100g")?.takeIf { it in 0.0..100.0 },
            carbs100 = n.number("carbohydrates_100g")?.takeIf { it in 0.0..100.0 },
            fat100 = n.number("fat_100g")?.takeIf { it in 0.0..100.0 },
            servingG = (servingQuantity as? JsonPrimitive).number()?.takeIf { it in 1.0..2000.0 },
        )
    }

    private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive).number()

    private fun JsonPrimitive?.number(): Double? = this?.content?.trim()?.replace(',', '.')?.toDoubleOrNull()

    /**
     * The code as the database stores it: UPC-A gets the leading zero of EAN-13, and the short
     * UPC-E is expanded to its full UPC-A form first.
     */
    fun normalize(raw: String, upcE: Boolean = false): String {
        val digits = raw.filter(Char::isDigit)
        val full = if (upcE) expandUpcE(digits) ?: digits else digits
        return if (full.length == 12) "0$full" else full
    }

    /** UPC-E "s d1..d6 c" to UPC-A, following the zero-suppression rules for the last digit. */
    private fun expandUpcE(code: String): String? {
        val c = when (code.length) {
            8 -> code
            7 -> "0$code"
            else -> return null
        }
        val s = c[0]
        val d = c.substring(1, 7)
        val check = c[7]
        val body = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        return "$s$body$check"
    }

    /** A plausible EAN-8, UPC-A or EAN-13 typed by hand. */
    fun validManual(code: String) = code.length in setOf(8, 12, 13) && code.all(Char::isDigit)
}
