import Foundation

/** The data file as a backup that moves between iPhone and Android, as on Android (#37). */
enum Backup {
    /** Top-level fields a real backup always has; at least one must be there. */
    static let keys: Set<String> = ["history", "settings", "routines", "profile"]
    /** Anything bigger is not a backup (a video, say). */
    static let maxBytes = 50 * 1024 * 1024

    /**
     * The restored data, or nil when [bytes] is no Mauve Fit backup. Every field has a default, so
     * any JSON object would decode, and "{}" would wipe everything.
     */
    static func decode(_ bytes: Data) -> AppData? {
        guard bytes.count <= maxBytes,
              let object = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
              !keys.isDisjoint(with: object.keys) else { return nil }
        return try? JSONDecoder().decode(AppData.self, from: bytes)
    }

    static func encode(_ d: AppData) -> Data {
        let enc = JSONEncoder()
        enc.outputFormatting = [.sortedKeys]
        return (try? enc.encode(d)) ?? Data()
    }

    /** "mauvefit-2026-11-04" plus the extension, as on Android. */
    static func fileName(_ ext: String, date: Date = Date()) -> String { "mauvefit-\(Stats.isoDay(date)).\(ext)" }
}

/**
 * Training history as CSV, one row per set, for spreadsheets and other apps, with fixed English
 * column names. Separator and decimal mark follow the app language as Excel expects them: comma
 * and point in English, semicolon and comma in German. Same columns as on Android.
 */
enum CsvExport {
    private static let header = [
        "date", "time", "workout", "exercise", "exercise_id", "muscle", "set", "type", "weight_kg", "reps", "rpe", "e1rm_kg", "workout_minutes",
    ]

    /** [exercise] looks up names and muscles by id (the repo in the app). */
    static func sets(_ d: AppData, exercise: (String) -> Exercise? = { ExerciseRepo.shared[$0] }) -> String {
        let sep = I18n.german ? ";" : ","
        // A BOM so Excel reads UTF-8 (umlauts garble otherwise); no "sep=" line, Sheets and Numbers show it as a row.
        var out = "\u{FEFF}" + header.joined(separator: sep) + "\n"
        let date = DateFormatter(), time = DateFormatter()
        for f in [date, time] { f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = .current; f.calendar = Calendar(identifier: .gregorian) }
        date.dateFormat = "yyyy-MM-dd"
        time.dateFormat = "HH:mm"
        // Oldest first reads naturally in a spreadsheet.
        for w in d.history.sorted(by: { $0.startedAt < $1.startedAt }) {
            let start = Date(ms: w.startedAt)
            let minutes = String(w.durationMs / 60_000)
            for e in w.entries {
                let ex = exercise(e.exerciseId)
                var n = 0
                for s in e.sets where s.done {
                    if !s.warmup { n += 1 }
                    let type = s.warmup ? "warmup" : s.type == .Drop ? "drop" : s.type == .Failure ? "failure" : "normal"
                    let row = [
                        date.string(from: start), time.string(from: start), w.name, ex?.name ?? e.exerciseId, e.exerciseId, ex?.target ?? "",
                        s.warmup ? "" : String(n), type, num(s.weight), String(s.reps), s.rpe > 0 ? String(s.rpe) : "",
                        s.warmup || s.weight <= 0 ? "" : num(Stats.e1rm(s)), minutes,
                    ]
                    out += row.map(cell).joined(separator: sep) + "\n"
                }
            }
        }
        return out
    }

    /** Whole numbers without ".0"; quarter kilos keep both decimals (11.25), otherwise one. */
    static func num(_ v: Double) -> String {
        var t: String
        if v.truncatingRemainder(dividingBy: 1) == 0 {
            t = String(Int64(v))
        } else {
            let quarter = abs(v * 4 - (v * 4).rounded()) < 1e-9
            t = String(format: quarter ? "%.2f" : "%.1f", locale: Locale(identifier: "en_US_POSIX"), v)
            while t.hasSuffix("0") { t.removeLast() }
            if t.hasSuffix(".") { t.removeLast() }
        }
        return I18n.german ? t.replacingOccurrences(of: ".", with: ",") : t
    }

    /** Quotes a cell with a separator, quote or line break (RFC 4180). */
    static func cell(_ s: String) -> String {
        // Scalars, not characters: "\r\n" is one character in Swift and would slip through.
        s.unicodeScalars.contains(where: { ",;\"\n\r".unicodeScalars.contains($0) })
            ? "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : s
    }
}
