import Foundation

/** Kind of cardio; the raw values are what the backup stores, as on Android. */
enum CardioKind: String, Codable, CaseIterable, Identifiable {
    case run, bike, row, walk, swim, other

    var id: String { rawValue }

    var label: String {
        switch self {
        case .run: L("Laufen", "Run")
        case .bike: L("Rad", "Bike")
        case .row: L("Rudern", "Row")
        case .walk: L("Gehen", "Walk")
        case .swim: L("Schwimmen", "Swim")
        case .other: L("Sonstiges", "Other")
        }
    }

    var symbol: String {
        switch self {
        case .run: "figure.run"
        case .bike: "figure.outdoor.cycle"
        case .row: "figure.rower"
        case .walk: "figure.walk"
        case .swim: "figure.pool.swim"
        case .other: "heart.fill"
        }
    }
}

/**
 * One cardio session starting at [at]. [rpe] is effort on a 1 to 10 scale (nil = not rated);
 * [source] is "manual" or "health" (taken over from Apple Health). Cardio never counts toward the
 * weekly goal, which stays about strength workouts.
 */
struct CardioSession: Codable, Hashable, Identifiable {
    var id: String = newId()
    var at: Int64 = nowMs()
    var kind: CardioKind = .run
    var minutes: Int = 30
    var distanceKm: Double?
    var avgHr: Int?
    var rpe: Int?
    var source = "manual"
    var note = ""

    enum CodingKeys: String, CodingKey { case id, at, kind, minutes, distanceKm, avgHr, rpe, source, note }

    init(at: Int64 = nowMs(), kind: CardioKind = .run, minutes: Int = 30, distanceKm: Double? = nil, avgHr: Int? = nil, rpe: Int? = nil, source: String = "manual") {
        self.at = at; self.kind = kind; self.minutes = minutes; self.distanceKm = distanceKm; self.avgHr = avgHr; self.rpe = rpe; self.source = source
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.value(.id, newId()); at = c.value(.at, nowMs()); kind = c.value(.kind, .other); minutes = c.value(.minutes, 30)
        distanceKm = c.opt(.distanceKm); avgHr = c.opt(.avgHr); rpe = c.opt(.rpe)
        source = c.value(.source, "manual"); note = c.value(.note, "")
    }
}

/** Cardio bookkeeping as on Android (data/Cardio.kt): effort levels, duplicates on import, weekly minutes. */
enum Cardio {
    /** The three effort choices of the log sheet, stored as an RPE on the 1 to 10 scale. */
    enum Effort: Int, CaseIterable, Identifiable {
        case easy = 3, moderate = 5, hard = 8

        var id: Int { rawValue }
        var label: String {
            switch self {
            case .easy: L("Locker", "Easy")
            case .moderate: L("Mittel", "Moderate")
            case .hard: L("Hart", "Hard")
            }
        }

        static func of(_ rpe: Int?) -> Effort? {
            guard let rpe else { return nil }
            return rpe >= 7 ? .hard : rpe >= 5 ? .moderate : .easy
        }
    }

    /** At or above this average pulse an unrated session counts as hard. */
    static let hardHr = 155

    /** Rated hard, or unrated with a high average pulse (typical for watch imports). */
    static func isHard(_ s: CardioSession) -> Bool { (s.rpe ?? 0) >= 7 || (s.rpe == nil && (s.avgHr ?? 0) >= hardHr) }

    /** End of the session, which is when recovery starts. */
    static func end(_ s: CardioSession) -> Int64 { s.at + Int64(s.minutes) * 60_000 }

    /**
     * Sessions from [incoming] that are not already known: none starting within a minute of a known
     * one, and none of the same kind on the same day as one logged by hand, before that one ended.
     */
    static func newSessions(_ existing: [CardioSession], _ incoming: [CardioSession]) -> [CardioSession] {
        var seen = existing
        var out: [CardioSession] = []
        for s in incoming {
            let dup = seen.contains { o in
                abs(o.at - s.at) < 60_000
                    || (o.source == "manual" && o.kind == s.kind && Stats.day(o.at) == Stats.day(s.at) && s.at < end(o) + 60_000)
            }
            if !dup { seen.append(s); out.append(s) }
        }
        return out
    }

    /** Minutes per day of the current week, Monday first. */
    static func weekMinutes(_ cardio: [CardioSession], today: Date = Date()) -> [Int] {
        let start = Stats.weekStart(today)
        var out = Array(repeating: 0, count: 7)
        for s in cardio {
            let i = Stats.calendar.dateComponents([.day], from: start, to: Stats.day(s.at)).day ?? -1
            if (0..<7).contains(i) { out[i] += s.minutes }
        }
        return out
    }

    /** "45 min" or "1:20 h". */
    static func fmtMinutes(_ m: Int) -> String { m < 60 ? "\(m) min" : String(format: "%d:%02d h", m / 60, m % 60) }

    /** "10 km" or "7,5 km". */
    static func fmtKm(_ km: Double) -> String { Fmt.kg((km * 10).rounded() / 10) + " km" }
}
