import Foundation

enum Stats {
    static let dayMs: Int64 = 24 * 3600 * 1000

    /** Monday-first calendar, as in the Android app. */
    static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.firstWeekday = 2
        c.minimumDaysInFirstWeek = 4
        c.timeZone = .current
        return c
    }

    static func day(_ ms: Int64) -> Date { calendar.startOfDay(for: Date(ms: ms)) }

    static func weekStart(_ date: Date) -> Date {
        calendar.dateInterval(of: .weekOfYear, for: date)?.start ?? calendar.startOfDay(for: date)
    }

    /** ISO date (yyyy-MM-dd) of [date] in the local time zone, the key of the food diary. */
    static func isoDay(_ date: Date = Date()) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /** Epley; a single rep is the max itself, not 3 % more. */
    static func e1rm(_ s: SetEntry) -> Double {
        if s.reps <= 0 { return 0 }
        if s.reps == 1 { return s.weight }
        return s.weight * (1 + Double(s.reps) / 30)
    }

    /** Working sets of the most recent workout that contained this exercise. */
    static func lastSets(_ history: [Workout], _ exerciseId: String) -> [SetEntry] {
        for w in history {
            if let e = w.entries.first(where: { $0.exerciseId == exerciseId }) { return e.sets.filter { !$0.warmup } }
        }
        return []
    }

    /**
     * Progressive overload: if every set last time hit the target reps, add one weight step;
     * otherwise repeat the weight.
     */
    static func suggestWeight(_ last: [SetEntry], targetReps: Int, step: Double) -> Double {
        guard let top = last.map(\.weight).max() else { return 0 }
        let allHit = last.allSatisfy { $0.reps >= targetReps }
        return allHit && top > 0 ? top + step : top
    }

    static func roundTo(_ v: Double, _ step: Double) -> Double { step <= 0 ? v : (v / step).rounded() * step }

    static func workoutsThisWeek(_ history: [Workout], now: Date = Date()) -> Int {
        let start = weekStart(now)
        return history.filter { day($0.startedAt) >= start }.count
    }

    /** Consecutive weeks (ending with this one, or last if this one is still open) that met the goal. */
    static func weekStreak(_ history: [Workout], goal: Int, now: Date = Date()) -> Int {
        guard goal > 0 else { return 0 }
        var perWeek: [Date: Int] = [:]
        for w in history { perWeek[weekStart(day(w.startedAt)), default: 0] += 1 }
        var week = weekStart(now)
        if perWeek[week, default: 0] < goal { week = calendar.date(byAdding: .weekOfYear, value: -1, to: week)! }
        var streak = 0
        while perWeek[week, default: 0] >= goal {
            streak += 1
            week = calendar.date(byAdding: .weekOfYear, value: -1, to: week)!
        }
        return streak
    }

    /** Volume (kg × reps) for each of the last [weeks] weeks, oldest first. */
    static func weeklyVolume(_ history: [Workout], weeks: Int = 8, now: Date = Date()) -> [(Date, Double)] {
        let current = weekStart(now)
        var byWeek: [Date: Double] = [:]
        for w in history { byWeek[weekStart(day(w.startedAt)), default: 0] += w.volume }
        return (0..<weeks).reversed().map { i in
            let wk = calendar.date(byAdding: .weekOfYear, value: -i, to: current)!
            return (wk, byWeek[wk, default: 0])
        }
    }

    /** Days of the current week on which a workout was logged. */
    static func weekDays(_ history: [Workout], now: Date = Date()) -> Set<Date> {
        let start = weekStart(now)
        return Set(history.map { day($0.startedAt) }.filter { $0 >= start })
    }

    /**
     * The plan whose muscles have rested longest, so the suggestion follows recovery rather than
     * a fixed order. Plans never trained come first.
     */
    static func nextRoutine(_ d: AppData) -> Routine? {
        let routines = d.routines.filter { !$0.exercises.isEmpty }
        guard !routines.isEmpty else { return nil }
        var last: [String: Int64] = [:]
        for w in d.history { if let id = w.routineId, last[id] == nil { last[id] = w.startedAt } }
        return routines.min { (last[$0.id] ?? 0) < (last[$1.id] ?? 0) }
    }

    /** Best estimated one-rep max per exercise over all workouts. */
    static func records(_ history: [Workout]) -> [(String, Double)] {
        var best: [String: Double] = [:]
        for w in history { for (id, s) in w.doneSets where s.weight > 0 { best[id] = max(best[id] ?? 0, e1rm(s)) } }
        return best.sorted { $0.value > $1.value }.map { ($0.key, $0.value) }
    }

    static func latestWeight(_ d: AppData) -> Double? { d.bodyWeights.max { $0.at < $1.at }?.kg }
}

/** Number formatting shared by the screens. */
enum Fmt {
    /** 82.5 → "82,5" in German, "82.5" in English; whole numbers without decimals. */
    static func num(_ v: Double, digits: Int = 1) -> String {
        let f = NumberFormatter()
        f.locale = I18n.locale
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = digits
        return f.string(from: NSNumber(value: v)) ?? "\(v)"
    }

    static func int(_ v: Int) -> String {
        let f = NumberFormatter()
        f.locale = I18n.locale
        f.numberStyle = .decimal
        return f.string(from: NSNumber(value: v)) ?? "\(v)"
    }

    /** 754 → "12:34". */
    static func clock(_ seconds: Int) -> String {
        let s = max(0, seconds)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }

    static func minutes(_ ms: Int64) -> String { "\(max(1, Int(ms / 60_000))) min" }

    static func date(_ ms: Int64, style: Date.FormatStyle.DateStyle = .abbreviated) -> String {
        Date(ms: ms).formatted(Date.FormatStyle(date: style, time: .omitted).locale(I18n.locale))
    }

    static func weekday(_ ms: Int64) -> String {
        Date(ms: ms).formatted(Date.FormatStyle().weekday(.wide).locale(I18n.locale))
    }
}
