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

    /**
     * Consecutive weeks (ending with this one, or last if this one is still open) that met the goal.
     * [frozen] weeks (ISO keys) were covered by a streak freeze: they neither count nor break the run.
     */
    static func weekStreak(_ history: [Workout], goal: Int, frozen: Set<String> = [], now: Date = Date()) -> Int {
        let goal = max(1, goal)
        var perWeek: [Date: Int] = [:]
        for w in history { perWeek[weekStart(day(w.startedAt)), default: 0] += 1 }
        var week = weekStart(now)
        if perWeek[week, default: 0] < goal { week = calendar.date(byAdding: .weekOfYear, value: -1, to: week)! }
        var streak = 0
        while true {
            if perWeek[week, default: 0] >= goal {
                streak += 1
            } else if !frozen.contains(weekKey(week)) {
                break
            }
            week = calendar.date(byAdding: .weekOfYear, value: -1, to: week)!
        }
        return streak
    }

    /** ISO week key of the week containing [date], e.g. "2026-W44". */
    static func weekKey(_ date: Date) -> String {
        let c = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return String(format: "%d-W%02d", c.yearForWeekOfYear ?? 0, c.weekOfYear ?? 0)
    }

    /** Monday of the week a [weekKey] names, or nil for anything else. */
    static func weekOf(_ key: String) -> Date? {
        let parts = key.components(separatedBy: "-W")
        guard parts.count == 2, let year = Int(parts[0]), let week = Int(parts[1]) else { return nil }
        var c = DateComponents()
        c.yearForWeekOfYear = year
        c.weekOfYear = week
        c.weekday = 2
        return calendar.date(from: c).map(weekStart)
    }

    /** Days since 1970-01-01 of the local calendar day of [date], like LocalDate.toEpochDay(). */
    static func epochDay(_ date: Date) -> Int64 {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let d = utc.date(from: DateComponents(year: c.year, month: c.month, day: c.day)) ?? date
        return Int64((d.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    /** 1 (Monday) to 7 (Sunday), like DayOfWeek.value. */
    static func isoWeekday(_ date: Date) -> Int {
        (calendar.component(.weekday, from: date) + 5) % 7 + 1
    }

    static func addDays(_ date: Date, _ n: Int) -> Date { calendar.date(byAdding: .day, value: n, to: date)! }

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

    /** When each target muscle was last trained (history is newest first). */
    static func lastTrained(_ history: [Workout], _ repo: ExerciseRepo) -> [String: Int64] {
        var out: [String: Int64] = [:]
        for w in history {
            for e in w.entries { if let t = repo[e.exerciseId]?.target, out[t] == nil { out[t] = w.startedAt } }
        }
        return out
    }

    /**
     * The plan whose muscles have rested longest, so the suggestion follows recovery rather than
     * a fixed order. Ties fall back to plan order after the last one trained.
     */
    static func nextRoutine(_ d: AppData, repo: ExerciseRepo = .shared, now: Int64 = nowMs()) -> Routine? {
        let usable = d.routines.filter { !$0.exercises.isEmpty }
        guard !usable.isEmpty else { return nil }
        let last = lastTrained(d.history, repo)
        // The last workout of a plan still offered: a free workout or a deleted plan must not
        // send the rotation back to plan 1.
        let lastId = d.history.first { w in usable.contains { $0.id == w.routineId } }?.routineId
        let start = (usable.firstIndex { $0.id == lastId } ?? -1) + 1
        let ordered = usable.indices.map { usable[(start + $0) % usable.count] }
        func score(_ i: Int) -> Double {
            var targets: [String] = []
            for p in ordered[i].exercises { if let t = repo[p.exerciseId]?.target, !targets.contains(t) { targets.append(t) } }
            // Average hours of rest across the plan's muscles; untrained muscles count as fully rested.
            let hours = targets.map { t in last[t].map { min(96, Double(now - $0) / 3_600_000) } ?? 96 }
            let avg = hours.isEmpty ? 96 : hours.reduce(0, +) / Double(hours.count)
            return avg - Double(i) * 0.01
        }
        var best = 0
        for i in ordered.indices where score(i) > score(best) { best = i }
        return ordered[best]
    }

    struct Record: Hashable {
        let exerciseId: String
        let best: SetEntry
        let e1rm: Double
        let at: Int64
    }

    /**
     * Weighted sets rank by estimated 1RM, sets without weight by reps, and any weighted set beats
     * one without: 0 kg x 30 is not a better bench press than 20 kg x 8.
     */
    static func recordScore(_ s: SetEntry) -> Double { s.weight > 0 ? 1_000_000 + e1rm(s) : Double(s.reps) }

    /** Best set ever for every exercise, most recently set records first. */
    static func records(_ history: [Workout]) -> [Record] {
        var best: [String: Record] = [:]
        for w in history.reversed() {
            for e in w.entries {
                for s in e.sets where !s.warmup && s.done {
                    if let cur = best[e.exerciseId], recordScore(s) <= recordScore(cur.best) { continue }
                    best[e.exerciseId] = Record(exerciseId: e.exerciseId, best: s, e1rm: e1rm(s), at: w.startedAt)
                }
            }
        }
        return best.values.sorted { $0.at != $1.at ? $0.at > $1.at : $0.exerciseId < $1.exerciseId }
    }

    /** The exercises of [w] whose best set beats every earlier workout; a first time is not a record. */
    static func newRecords(_ w: Workout, history: [Workout]) -> [(String, SetEntry)] {
        let earlier = history.filter { $0.startedAt < w.startedAt }
        var out: [(String, SetEntry)] = []
        for e in w.entries where !out.contains(where: { $0.0 == e.exerciseId }) {
            let sets = w.entries.filter { $0.exerciseId == e.exerciseId }.flatMap(\.sets).filter { !$0.warmup && $0.done }
            guard let best = sets.max(by: { recordScore($0) < recordScore($1) }) else { continue }
            let previous = earlier.flatMap { o in o.entries.filter { $0.exerciseId == e.exerciseId }.flatMap(\.sets) }
                .filter { $0.done && !$0.warmup }.map(recordScore).max()
            if let previous, recordScore(best) > previous + 0.01 { out.append((e.exerciseId, best)) }
        }
        return out
    }

    static func latestWeight(_ d: AppData) -> Double? { d.bodyWeights.max { $0.at < $1.at }?.kg }

    /** Working reps of a workout, the headline of body-weight training. */
    static func reps(_ w: Workout) -> Int { w.doneSets.reduce(0) { $0 + $1.1.reps } }

    /** "12,3 t" or "1340 kg"; for body-weight training where nothing was lifted, "120 Wdh.". */
    static func headline(_ w: Workout) -> String {
        w.volume > 0 ? Fmt.volume(w.volume) : L("\(reps(w)) Wdh.", "\(reps(w)) reps")
    }
}

/** Number formatting shared by the screens. */
enum Fmt {
    /** 82.5 → "82,5" in German, "82.5" in English; whole numbers without decimals. */
    static func num(_ v: Double, digits: Int = 1, minDigits: Int = 0) -> String {
        let f = NumberFormatter()
        f.locale = I18n.locale
        f.usesGroupingSeparator = false
        f.minimumFractionDigits = minDigits
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

    /** "< 1 min", "45 min", "1 h 5 min". */
    static func duration(_ ms: Int64) -> String {
        let m = ms / 60_000
        if m < 1 { return "< 1 min" }
        return m >= 60 ? "\(m / 60) h \(m % 60) min" : "\(m) min"
    }

    /**
     * Kilos without trailing zeros. Quarter kilos keep both decimals (1.25 kg plates read
     * "1,25", not "1,3"); anything else, like an average body weight, stays at one.
     */
    static func kg(_ v: Double) -> String {
        if v.rounded() == v { return String(Int(v)) }
        let quarter = abs(v * 4 - (v * 4).rounded()) < 1e-9
        return num(v, digits: quarter ? 2 : 1)
    }

    /** "12,3 t" from ten tonnes on, "1340 kg" below. */
    static func volume(_ v: Double) -> String {
        v >= 10_000 ? num(v / 1000, digits: 1, minDigits: 1) + " t" : "\(Int(v.rounded())) kg"
    }

    /** "1 Satz", "3 Sätze". */
    static func sets(_ n: Int) -> String { n == 1 ? L("1 Satz", "1 set") : L("\(n) Sätze", "\(n) sets") }
    static func exercises(_ n: Int) -> String { n == 1 ? L("1 Übung", "1 exercise") : L("\(n) Übungen", "\(n) exercises") }
    static func workouts(_ n: Int) -> String { n == 1 ? L("1 Training", "1 workout") : L("\(n) Trainings", "\(n) workouts") }
    static func weeks(_ n: Int) -> String { n == 1 ? L("Woche in Folge", "week in a row") : L("Wochen in Folge", "weeks in a row") }

    /** One set for lists: "60×8" or "8 Wdh.", plus "D" / "V" for drop and failure sets. */
    static func set(_ s: SetEntry, bodyweight: Bool) -> String {
        let base = bodyweight && s.weight <= 0 ? L("\(s.reps) Wdh.", "\(s.reps) reps") : "\(kg(s.weight))×\(s.reps)"
        let type = s.type == .Drop ? " D" : s.type == .Failure ? L(" V", " F") : ""
        return base + type + (s.rpe > 0 ? " @\(s.rpe)" : "")
    }

    static func date(_ ms: Int64, style: Date.FormatStyle.DateStyle = .abbreviated) -> String {
        Date(ms: ms).formatted(Date.FormatStyle(date: style, time: .omitted).locale(I18n.locale))
    }

    static func weekday(_ ms: Int64) -> String {
        Date(ms: ms).formatted(Date.FormatStyle().weekday(.wide).locale(I18n.locale))
    }
}
