import Foundation

/**
 * German and English. Every user-facing string is written inline as `L("Deutsch", "English")`,
 * like in the Android app, so both versions sit side by side and none can go missing.
 */
enum I18n {
    nonisolated(unsafe) private(set) static var german: Bool = systemGerman

    static var locale: Locale { Locale(identifier: german ? "de_DE" : "en_US") }

    private static var systemGerman: Bool { Locale.preferredLanguages.first?.hasPrefix("de") ?? false }

    /** [setting] is "system", "de" or "en". */
    static func apply(_ setting: String) {
        switch setting {
        case "de": german = true
        case "en": german = false
        default: german = systemGerman
        }
    }
}

/** Picks the German or English text for the current language. */
func L(_ de: String, _ en: String) -> String { I18n.german ? de : en }
