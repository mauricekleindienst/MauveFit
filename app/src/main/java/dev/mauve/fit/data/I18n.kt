package dev.mauve.fit.data

import java.util.Locale

/**
 * German and English. Every user-facing string is written inline as `L("Deutsch", "English")`,
 * so both versions sit side by side and none can go missing. The language is fixed for the life
 * of an activity; changing it in the settings recreates the activity.
 */
object I18n {
    @Volatile
    var german: Boolean = Locale.getDefault().language == "de"
        private set

    val locale: Locale get() = if (german) Locale.GERMAN else Locale.ENGLISH

    /** [setting] is "system", "de" or "en". */
    fun apply(setting: String) {
        german = when (setting) {
            "de" -> true
            "en" -> false
            else -> Locale.getDefault().language == "de"
        }
    }
}

/** Picks the German or English text for the current language. */
fun L(de: String, en: String): String = if (I18n.german) de else en
