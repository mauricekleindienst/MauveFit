import Foundation

// Quests, XP, levels, the weekly challenge, streak freezes, achievements, coins and the shop:
// the rules of the Android app (data/Quests.kt, Shop.kt, Motivation.kt, Progress.kt and the
// record timeline of Insights.kt), ported one to one so a backup reads the same on both.

/**
 * Kotlin's `kotlin.random.Random(seed)` (XorWow), so a day draws the same quests on iOS as on
 * Android. A unit test checks it against values taken from the Kotlin standard library.
 */
struct KotlinRandom {
    private var x, y, z, w, v, addend: UInt32

    init(seed: Int64) {
        let s1 = UInt32(truncatingIfNeeded: seed), s2 = UInt32(truncatingIfNeeded: seed >> 32)
        x = s1; y = s2; z = 0; w = 0; v = ~s1
        addend = (s1 << 10) ^ (s2 >> 4)
        for _ in 0..<64 { _ = next() }
    }

    private mutating func next() -> UInt32 {
        var t = x
        t ^= t >> 2
        x = y; y = z; z = w
        let v0 = v
        w = v0
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
        v = t
        addend &+= 362_437
        return t &+ addend
    }

    /** A number in 0 ..< [bound], as Kotlin's `nextInt(until)`. */
    mutating func nextInt(_ bound: Int) -> Int {
        precondition(bound > 0)
        let n = UInt32(bound)
        if bound & -bound == bound {
            let bits = bound.trailingZeroBitCount
            let r = next()
            return bits == 0 ? 0 : Int(r >> UInt32(32 - bits))
        }
        while true {
            let b = next() >> 1
            let value = b % n
            if Int32(bitPattern: b &- value &+ (n - 1)) >= 0 { return Int(value) }
        }
    }
}

// MARK: - Records and streaks

enum Records {
    struct PR { let exerciseId: String; let day: Date; let set: SetEntry }

    /**
     * Records as they were broken, newest first: a session's best set beating every earlier one of
     * that exercise. The first session of an exercise only sets the bar.
     */
    static func timeline(_ history: [Workout]) -> [PR] {
        var best: [String: Double] = [:]
        var out: [PR] = []
        for w in history.sorted(by: { $0.startedAt < $1.startedAt }) {
            var order: [String] = []
            var sets: [String: [SetEntry]] = [:]
            for e in w.entries {
                if sets[e.exerciseId] == nil { order.append(e.exerciseId) }
                sets[e.exerciseId, default: []] += e.sets.filter { $0.done && !$0.warmup }
            }
            for id in order {
                guard let top = sets[id]?.max(by: { Stats.recordScore($0) < Stats.recordScore($1) }) else { continue }
                let s = Stats.recordScore(top)
                let prev = best[id]
                if let prev, s > prev + 1e-9 { out.append(PR(exerciseId: id, day: Stats.day(w.startedAt), set: top)) }
                if prev == nil || s > prev! { best[id] = s }
            }
        }
        return out.reversed()
    }

    /** Weeks in a row with the goal met, now and at best; frozen weeks neither count nor break a run. */
    static func streaks(_ history: [Workout], goal: Int, today: Date = Date(), frozen: Set<String> = []) -> (current: Int, best: Int) {
        guard !history.isEmpty else { return (0, 0) }
        var perWeek: [Date: Int] = [:]
        for w in history { perWeek[Stats.weekStart(Stats.day(w.startedAt)), default: 0] += 1 }
        let now = Stats.weekStart(today)
        var week = perWeek.keys.min()!
        var run = 0, best = 0
        while week <= now {
            if perWeek[week, default: 0] >= max(1, goal) {
                run += 1
                best = max(best, run)
            } else if week == now || frozen.contains(Stats.weekKey(week)) {
                // The running week only breaks a run once it is over; a frozen one never.
            } else {
                run = 0
            }
            week = Stats.calendar.date(byAdding: .weekOfYear, value: 1, to: week)!
        }
        return (run, best)
    }
}

// MARK: - Weekly challenge and streak freezes

enum Motivation {
    enum Kind: CaseIterable { case workouts, sets, volume, groups, superset, legs }

    struct Challenge {
        /** ISO week the challenge belongs to, e.g. "2026-W44". */
        let week: String
        let kind: Kind
        let title: String
        let detail: String
        let progress: Int
        let target: Int
        /** "2 von 3", "4,2 von 10 t". */
        let label: String

        var done: Bool { progress >= target }
        var fraction: Double { min(1, max(0, Double(progress) / Double(max(1, target)))) }
    }

    private static let legs: Set<String> = ["quads", "hamstrings", "glutes", "calves", "adductors", "abductors"]

    /**
     * This week's challenge, picked from the week alone so it stays put all week; [slot] 1 and 2
     * are the side quests, always of another kind. Progress counts the week's workouts only.
     */
    static func challenge(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }, today: Date = Date(), slot: Int = 0) -> Challenge {
        let start = Stats.weekStart(today)
        let end = Stats.addDays(start, 7)
        let key = Stats.weekKey(start)
        let week = d.history.filter { let day = Stats.day($0.startedAt); return day >= start && day < end }
        let goal = min(7, max(1, d.settings.weeklyGoal))
        let level = d.profile.level
        // Stride 5 over six kinds: neighbouring weeks never repeat, every kind comes round in six weeks.
        let c = Stats.calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: start)
        let n = (c.yearForWeekOfYear ?? 0) * 53 + (c.weekOfYear ?? 0)
        let kinds = Kind.allCases
        let m = (n * 5 + slot * 2) % kinds.count
        let kind = kinds[m < 0 ? m + kinds.count : m]
        func of(_ p: Int, _ t: Int) -> String { L("\(min(p, t)) von \(t)", "\(min(p, t)) of \(t)") }

        switch kind {
        case .workouts:
            // Beginners aim for the goal itself, everyone else for one more.
            let t = level == .Beginner ? goal : min(7, goal + 1)
            return Challenge(week: key, kind: kind, title: L("\(t) Trainings diese Woche", "\(t) workouts this week"),
                             detail: t > goal ? L("Eins mehr als dein Wochenziel", "One more than your weekly goal") : L("Dein Wochenziel, ganz erfüllt", "Your weekly goal, met in full"),
                             progress: week.count, target: t, label: of(week.count, t))
        case .sets:
            let per = level == .Beginner ? 12 : level == .Intermediate ? 16 : 20
            let t = max(2, Int((Double(goal * per) / 5).rounded())) * 5
            let p = week.reduce(0) { $0 + $1.doneSets.count }
            return Challenge(week: key, kind: kind, title: L("Insgesamt \(t) Sätze", "\(t) sets in total"),
                             detail: L("Arbeitssätze, Aufwärmen zählt nicht", "Working sets, warm-ups don't count"), progress: p, target: t, label: of(p, t))
        case .volume:
            if d.profile.setup == .Home {
                // Without weights there is no tonnage; reps take its place.
                let per = level == .Beginner ? 120 : level == .Intermediate ? 180 : 250
                let t = max(2, Int((Double(goal * per) / 50).rounded())) * 50
                let p = week.reduce(0) { $0 + Stats.reps($1) }
                return Challenge(week: key, kind: kind, title: L("Insgesamt \(t) Wiederholungen", "\(t) reps in total"),
                                 detail: L("Über alle Trainings der Woche", "Across all workouts this week"), progress: p, target: t, label: of(p, t))
            }
            let per = level == .Beginner ? 2.0 : level == .Intermediate ? 3.5 : 5.0
            let tons = max(3, Int((Double(goal) * per).rounded()))
            let kg = week.reduce(0) { $0 + $1.volume }
            // Rounded down: 2,950 kg is "2.9 of 3 t", not a "3 of 3" that is not done yet.
            let shown = Fmt.kg(min((kg / 100).rounded(.down) / 10, Double(tons)))
            return Challenge(week: key, kind: kind, title: L("\(tons) Tonnen Volumen", "\(tons) tons of volume"),
                             detail: L("Gewicht mal Wiederholungen, über alle Trainings", "Weight times reps, across all workouts"),
                             progress: Int(kg.rounded(.down)), target: tons * 1000, label: L("\(shown) von \(tons) t", "\(shown) of \(tons) t"))
        case .groups:
            let groups = Achievements.groups
            let hit = groups.filter { g in
                week.contains { w in w.entries.contains { e in targetOf(e.exerciseId).map { g.muscles.contains($0) } == true && e.sets.contains { !$0.warmup } } }
            }.count
            return Challenge(week: key, kind: kind, title: L("Jede große Muskelgruppe einmal", "Every major muscle group once"),
                             detail: groups.map(\.name).joined(separator: ", "), progress: hit, target: groups.count, label: of(hit, groups.count))
        case .superset:
            let p = week.filter { w in
                Dictionary(grouping: w.entries, by: \.superset).contains { group, es in group != nil && es.count >= 2 }
            }.count
            return Challenge(week: key, kind: kind, title: L("Ein Training mit Supersatz", "A workout with a superset"),
                             detail: L("Im Plan zwei Übungen als Supersatz verknüpfen", "Link two exercises as a superset in a plan"),
                             progress: p, target: 1, label: of(p, 1))
        case .legs:
            let t = goal <= 1 ? 1 : 2
            let p = week.filter { w in w.entries.contains { e in targetOf(e.exerciseId).map { legs.contains($0) } == true && e.sets.contains { !$0.warmup } } }.count
            return Challenge(week: key, kind: kind, title: t == 1 ? L("Einmal Beine", "Legs once") : L("Zwei Mal Beine", "Legs twice"),
                             detail: L("Trainings mit mindestens einer Beinübung", "Workouts with at least one leg exercise"),
                             progress: p, target: t, label: of(p, t))
        }
    }

    /** Freezes earned per run of goal weeks, and the most kept in stock. */
    static let freezeEvery = 4
    static let freezeMax = 2

    struct FreezeState: Equatable { var freezes: Int; var frozen: Set<String>; var settled: String; var run: Int }

    /**
     * Walks every finished week after [s].settled up to last week: a week that met [goal] extends
     * the run and every fourth one earns a freeze; a missed week during a run spends a freeze and is
     * remembered as frozen, without one the run ends. The first call starts at the oldest workout.
     */
    static func settle(_ history: [Workout], goal: Int, _ s: FreezeState, today: Date = Date()) -> FreezeState {
        let cal = Stats.calendar
        let last = cal.date(byAdding: .weekOfYear, value: -1, to: Stats.weekStart(today))!
        let first: Date
        if let w = Stats.weekOf(s.settled) {
            first = cal.date(byAdding: .weekOfYear, value: 1, to: w)!
        } else if let oldest = history.map(\.startedAt).min() {
            first = Stats.weekStart(Stats.day(oldest))
        } else {
            var out = s
            out.settled = Stats.weekKey(last)
            return out
        }
        if first > last { return s }
        var perWeek: [Date: Int] = [:]
        for w in history { perWeek[Stats.weekStart(Stats.day(w.startedAt)), default: 0] += 1 }
        var freezes = min(freezeMax, max(0, s.freezes))
        var run = s.run
        var frozen = s.frozen
        // Ten years at most, should the clock ever jump.
        var week = max(first, cal.date(byAdding: .weekOfYear, value: -520, to: last)!)
        while week <= last {
            if perWeek[week, default: 0] >= max(1, goal) {
                run += 1
                if run % freezeEvery == 0 { freezes = min(freezeMax, freezes + 1) }
            } else if run > 0 && freezes > 0 {
                freezes -= 1
                frozen.insert(Stats.weekKey(week))
            } else {
                run = 0
            }
            week = cal.date(byAdding: .weekOfYear, value: 1, to: week)!
        }
        return FreezeState(freezes: freezes, frozen: frozen, settled: Stats.weekKey(last), run: run)
    }

    /** "1 Joker", "2 freezes". */
    static func freezeLabel(_ n: Int) -> String { L("\(n) Joker", n == 1 ? "1 freeze" : "\(n) freezes") }
}

// MARK: - Quests, XP and levels

enum Quests {
    struct Quest: Identifiable {
        /** Stable key for claiming: "d:2026-11-02:Water", "w:2026-W45:1". */
        let key: String
        let title: String
        let progress: Int
        let target: Int
        let xp: Int
        /** Formats an amount for the label, e.g. millilitres as litres. */
        var unit: ((Int) -> String)?

        var id: String { key }
        var done: Bool { progress >= target }
        var label: String {
            let p = min(progress, target)
            if let unit {
                let shown = unit(p)
                return (shown.hasSuffix(" l") ? String(shown.dropLast(2)) : shown) + "/" + unit(target)
            }
            return "\(p)/\(target)"
        }
    }

    enum Daily: String, CaseIterable {
        case Train, Sets, Record, Water, Protein, Rate, Rpe, NewExercise, Cardio, Weigh, Warmup, Superset

        var xp: Int {
            switch self {
            case .Train: 50
            case .Sets: 30
            case .Record: 60
            case .Water: 20
            case .Protein: 30
            case .Rate: 15
            case .Rpe: 20
            case .NewExercise: 40
            case .Cardio: 30
            case .Weigh: 15
            case .Warmup: 20
            case .Superset: 30
            }
        }

        /**
         * Every quest can be completed on iOS now (cardio #31, RPE and supersets #30), so the
         * draw is exactly the same as on Android.
         */
        var supported: Bool { true }
    }

    static let weeklyXp = 60
    /** Coins per day of the quest streak, growing to seven times that. */
    static let streakCoins = 15
    static let challengeXp = 100
    static let recordXp = 25

    private static func workoutsOn(_ d: AppData, _ day: Date) -> [Workout] { d.history.filter { Stats.day($0.startedAt) == day } }

    private static func targets(_ d: AppData) -> Nutrition.Targets? {
        d.bodyWeights.first.map { Nutrition.targets(kg: $0.kg, goal: d.profile.goal) }
    }

    /** What the days before [day] say about this person; only the past counts, so the day's quests stay put. */
    private struct Context {
        let past: [Workout]
        let trainingDay: Bool
        let workouts: Int
        let usesFood: Bool
        let usesWater: Bool
        let usesCardio: Bool
        let weighs: Bool
        let usesRpe: Bool
        let hasSupersets: Bool
        let typicalSets: Double?
        let typicalWater: Int?
        let typicalProtein: Double?
        let typicalCardio: Int?

        init(_ d: AppData, _ day: Date) {
            past = d.history.filter { Stats.day($0.startedAt) < day }
            let weekStart = Stats.weekStart(day)
            let doneThisWeek = past.filter { Stats.day($0.startedAt) >= weekStart }.count
            let goal = min(7, max(1, d.settings.weeklyGoal))
            let daysLeft = 8 - Stats.isoWeekday(day)
            let yesterday = Stats.addDays(day, -1)
            let trainedYesterday = past.contains { Stats.day($0.startedAt) == yesterday }
            // The goal still needs sessions, and either every day left counts or yesterday was a rest day.
            trainingDay = doneThisWeek < goal && (goal - doneThisWeek >= daysLeft || !trainedYesterday)
            workouts = past.count

            let from14 = Stats.isoDay(Stats.addDays(day, -14))
            let today = Stats.isoDay(day)
            var foodDays: [String: Int] = [:]
            for f in d.food where f.date >= from14 && f.date < today { foodDays[f.date, default: 0] += f.protein }
            let waterDays = d.water.filter { $0.key >= from14 && $0.key < today && $0.value > 0 }
            let recent = d.cardio.filter { let c = Stats.day($0.at); return c < day && c >= Stats.addDays(day, -30) }
            usesCardio = !recent.isEmpty
            let minutes = recent.map(\.minutes).sorted()
            typicalCardio = minutes.isEmpty ? nil : minutes[minutes.count / 2]
            usesFood = !foodDays.isEmpty
            usesWater = !waterDays.isEmpty
            let month = Stats.addDays(day, -30)
            weighs = d.settings.goalWeight > 0 || d.bodyWeights.contains { Stats.day($0.at) >= month }
            usesRpe = past.prefix(10).contains { w in w.entries.contains { e in e.sets.contains { $0.rpe > 0 } } }
            hasSupersets = d.routines.contains { r in r.exercises.contains { $0.superset != nil } }

            // Usual amounts, so a quest asks for a little more than a normal day.
            let sets = past.prefix(5).map { $0.doneSets.count }.filter { $0 > 0 }
            typicalSets = sets.isEmpty ? nil : Double(sets.reduce(0, +)) / Double(sets.count)
            let water = waterDays.values.sorted()
            typicalWater = water.isEmpty ? nil : water[water.count / 2]
            typicalProtein = foodDays.isEmpty ? nil : Double(foodDays.values.reduce(0, +)) / Double(foodDays.count)
        }
    }

    /**
     * The three quests of [day], fitted to the person: on a training day the workout, one more
     * about it and one about the body; on a rest day body and nutrition only.
     */
    static func daily(_ d: AppData, day: Date = Stats.day(nowMs())) -> [Quest] {
        // A reroll bought in the shop draws the day's quests anew.
        let rerolls = d.rerollDay == Stats.isoDay(day) ? d.rerollCount : 0
        var rnd = KotlinRandom(seed: Stats.epochDay(day) * 7919 + Int64(rerolls))
        let c = Context(d, day)
        let hasTargets = targets(d) != nil

        var body: [(Daily, Int)] = [(.Water, c.usesWater ? 3 : 1)]
        if c.usesFood && hasTargets { body.append((.Protein, 3)) }
        body.append((.Weigh, c.weighs ? 2 : 1))
        body.append((.Cardio, c.usesCardio ? 2 : 1))

        var picks: [Daily] = []
        if c.trainingDay {
            var training: [(Daily, Int)] = [(.Sets, 3)]
            if c.workouts >= 3 { training.append((.Record, 2)) }
            training.append((.Rate, 1))
            if c.usesRpe { training.append((.Rpe, 2)) } else if c.workouts >= 5 { training.append((.Rpe, 1)) }
            if c.workouts >= 5 { training.append((.NewExercise, 1)) }
            if c.workouts >= 2 { training.append((.Warmup, 1)) }
            if c.hasSupersets { training.append((.Superset, 1)) }
            picks.append(.Train)
            picks += pick(training, &rnd)
            picks += pick(body, &rnd)
        } else {
            picks += pick(body, &rnd, 3)
        }
        // Fill up should a pool run short (a new user on a rest day).
        for spare in [Daily.Water, .Weigh, .Cardio, .Rate] where picks.count < 3 && !picks.contains(spare) && spare.supported {
            picks.append(spare)
        }
        return picks.prefix(3).map { quest(d, day, $0, c) }
    }

    /** [n] distinct picks, each as likely as its weight, in the pool's order as on Android. */
    private static func pick(_ weights: [(Daily, Int)], _ rnd: inout KotlinRandom, _ n: Int = 1) -> [Daily] {
        var left = weights.filter { $0.1 > 0 && $0.0.supported }
        var out: [Daily] = []
        for _ in 0..<min(n, left.count) {
            var r = rnd.nextInt(left.reduce(0) { $0 + $1.1 })
            let i = left.firstIndex { _, w in defer { r -= w }; return r < w }!
            out.append(left[i].0)
            left.remove(at: i)
        }
        return out
    }

    /** 1500 -> "1,5 l" (German) or "1.5 l". */
    static func litres(_ ml: Int) -> String {
        var s = String(format: "%.2f", Double(ml) / 1000)
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return (I18n.german ? s.replacingOccurrences(of: ".", with: ",") : s) + " l"
    }

    private static func quest(_ d: AppData, _ day: Date, _ q: Daily, _ c: Context) -> Quest {
        let done = workoutsOn(d, day) + [d.active].compactMap { $0 }.filter { Stats.day($0.startedAt) == day }
        let sets = done.flatMap { w in w.entries.flatMap(\.sets) }.filter(\.done)
        let key = "d:\(Stats.isoDay(day)):\(q.rawValue)"
        func of(_ title: String, _ p: Int, _ t: Int) -> Quest { Quest(key: key, title: title, progress: p, target: t, xp: q.xp) }
        switch q {
        case .Train:
            return of(L("Ein Training abschließen", "Finish a workout"), workoutsOn(d, day).count, 1)
        case .Sets:
            // About a normal workout's sets, rounded to 5.
            let t = c.typicalSets.map { min(30, max(8, max(1, Int($0 / 5)) * 5)) } ?? (d.profile.level == .Beginner ? 10 : 15)
            return of(L("\(t) Sätze schaffen", "Do \(t) sets"), sets.filter { !$0.warmup }.count, t)
        case .Record:
            return of(L("Einen Rekord brechen", "Break a record"), Records.timeline(d.history).filter { $0.day == day }.count, 1)
        case .Water:
            // A glass more than a usual day, between 1.5 and 2.5 l; 1.5 l to start with.
            let goal = c.typicalWater.map { min(2500, max(1500, ($0 + 250) / 250 * 250)) } ?? 1500
            return Quest(key: key, title: L("\(litres(goal)) trinken", "Drink \(litres(goal))"),
                         progress: d.water[Stats.isoDay(day)] ?? 0, target: goal, xp: q.xp, unit: litres)
        case .Protein:
            // A tenth over a usual day, never more than the target itself.
            let target = targets(d)?.protein ?? 100
            let raw = c.typicalProtein.map { min($0 * 1.1, Double(target)) } ?? Double(target) * 0.75
            let goal = max(40, Int(raw / 10) * 10)
            let iso = Stats.isoDay(day)
            return of(L("\(goal) g Protein essen", "Eat \(goal) g of protein"), d.food.filter { $0.date == iso }.reduce(0) { $0 + $1.protein }, goal)
        case .Rate:
            return of(L("Ein Training bewerten", "Rate a workout"), workoutsOn(d, day).filter { $0.feel > 0 }.count, 1)
        case .Rpe:
            return of(L("3 Sätze mit RPE bewerten", "Rate 3 sets with RPE"), sets.filter { $0.rpe > 0 }.count, 3)
        case .NewExercise:
            let before = Set(c.past.flatMap { w in w.entries.map(\.exerciseId) })
            let new = done.flatMap { w in w.entries.filter { e in e.sets.contains { $0.done && !$0.warmup } }.map(\.exerciseId) }
                .filter { !before.contains($0) }.count
            return of(L("Eine neue Übung ausprobieren", "Try a new exercise"), min(1, new), 1)
        case .Cardio:
            let t = c.typicalCardio.map { min(45, max(15, $0 / 5 * 5)) } ?? 20
            return of(c.usesCardio ? L("\(t) Minuten Cardio", "\(t) minutes of cardio") : L("\(t) Minuten locker bewegen", "\(t) minutes of easy movement"),
                      d.cardio.filter { Stats.day($0.at) == day }.reduce(0) { $0 + $1.minutes }, t)
        case .Weigh:
            return of(L("Körpergewicht eintragen", "Log your body weight"), min(1, d.bodyWeights.filter { Stats.day($0.at) == day }.count), 1)
        case .Warmup:
            return of(L("Mit Aufwärmsätzen starten", "Start with warm-up sets"), min(2, sets.filter(\.warmup).count), 2)
        case .Superset:
            let p = done.filter { w in w.entries.contains { e in e.superset != nil && e.sets.contains(where: \.done) } }.count
            return of(L("Einen Supersatz trainieren", "Train a superset"), min(1, p), 1)
        }
    }

    /** The week's two side quests next to the main challenge, each of another kind. */
    static func weekly(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }, today: Date = Date()) -> [Quest] {
        (1...2).map { slot in
            let c = Motivation.challenge(d, targetOf: targetOf, today: today, slot: slot)
            return Quest(key: "w:\(c.week):\(slot)", title: c.title, progress: c.progress, target: c.target, xp: weeklyXp)
        }
    }

    /** Banks the XP of quests finished today and this week; returns the data unchanged when nothing new. */
    static func claim(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }, today: Date = Stats.day(nowMs()), now: Int64 = nowMs()) -> AppData {
        let todays = daily(d, day: today)
        let fresh = (todays + weekly(d, targetOf: targetOf, today: today)).filter { $0.done && !d.questsClaimed.contains($0.key) }
        guard !fresh.isEmpty else { return d }
        var out = d
        // Only recent keys matter (today and this week); older ones can go.
        out.questsClaimed = Array((d.questsClaimed + fresh.map(\.key)).suffix(120))
        // A running XP booster doubles quest XP; coins are half the base XP.
        let boost = now < d.boostUntil ? 2 : 1
        let xp = fresh.reduce(0) { $0 + $1.xp }
        out.questXp += xp * boost
        out.questsDone += fresh.count
        out.questCoins += xp / 2
        // All three of today done: the quest streak grows, and pays more each day up to a week.
        let iso = Stats.isoDay(today)
        if todays.allSatisfy(\.done) && d.questStreakDay != iso {
            let run = d.questStreakDay == Stats.isoDay(Stats.addDays(today, -1)) ? d.questStreak + 1 : 1
            out.questStreak = run
            out.questStreakDay = iso
            out.questStreakBest = max(d.questStreakBest, run)
            out.questCoins += streakCoins * min(7, run)
        }
        return out
    }

    /** XP a finished workout is worth: a base plus its working sets, capped. */
    static func workoutXp(_ w: Workout) -> Int { 40 + min(60, w.doneSets.count * 3) }

    static func xp(_ d: AppData) -> Int {
        var xp = 0
        for w in d.history { xp += workoutXp(w) }
        xp += Records.timeline(d.history).count * recordXp
        xp += d.challengesDone.count * challengeXp
        return xp + d.questXp
    }

    struct Rank {
        let level: Int, title: String, into: Int, need: Int, total: Int
        var fraction: Double { min(1, max(0, Double(into) / Double(need))) }
    }

    /** XP from one level to the next: 150 at first, 50 more for each level after. */
    private static func need(_ level: Int) -> Int { 100 + 50 * level }

    static func level(_ xp: Int) -> Rank {
        var level = 1
        var left = xp
        while left >= need(level) { left -= need(level); level += 1 }
        return Rank(level: level, title: title(level), into: left, need: need(level), total: xp)
    }

    static func title(_ level: Int) -> String {
        switch level {
        case ..<5: L("Neuling", "Rookie")
        case ..<10: L("Sportler", "Athlete")
        case ..<15: L("Athlet", "Competitor")
        case ..<20: L("Kraftpaket", "Powerhouse")
        case ..<30: L("Profi", "Pro")
        default: L("Legende", "Legend")
        }
    }
}

// MARK: - Achievements

enum Achievements {
    struct Group { let name: String; let muscles: Set<String> }

    /** The six muscle groups of the recovery card, skills and challenges. */
    static var groups: [Group] {
        [
            Group(name: L("Brust", "Chest"), muscles: ["pectorals", "serratus anterior"]),
            Group(name: L("Rücken", "Back"), muscles: ["lats", "upper back", "traps", "spine"]),
            Group(name: L("Schultern", "Shoulders"), muscles: ["delts", "levator scapulae"]),
            Group(name: L("Arme", "Arms"), muscles: ["biceps", "triceps", "forearms"]),
            Group(name: L("Beine", "Legs"), muscles: ["quads", "hamstrings", "glutes", "calves", "adductors", "abductors"]),
            Group(name: L("Bauch", "Core"), muscles: ["abs"]),
        ]
    }

    /** Bronze to diamond; tier 0 for one-off achievements. */
    struct Achievement: Identifiable {
        let title: String, detail: String, progress: Double, tier: Int
        var id: String { title }
        var unlocked: Bool { progress >= 1 }
        /** Coins it pays: 50 for a one-off, more for each tier. */
        var coins: Int { tier == 0 ? 50 : 25 * tier * tier }
    }

    static func tierName(_ tier: Int) -> String {
        switch tier {
        case 1: L("Bronze", "Bronze")
        case 2: L("Silber", "Silver")
        case 3: L("Gold", "Gold")
        case 4: L("Platin", "Platinum")
        case 5: L("Diamant", "Diamond")
        default: ""
        }
    }

    static func all(_ d: AppData, today: Date = Date()) -> [Achievement] {
        let h = d.history
        let sets = h.reduce(0) { $0 + $1.doneSets.count }
        let volume = h.reduce(0) { $0 + $1.volume }
        let streak = Stats.weekStreak(h, goal: d.settings.weeklyGoal, frozen: d.frozenWeeks, now: today)
        let bestStreak = max(streak, Records.streaks(h, goal: d.settings.weeklyGoal, today: today, frozen: d.frozenWeeks).best)
        let hours = h.map { Stats.calendar.component(.hour, from: Date(ms: $0.startedAt)) }
        let distinct = Set(h.flatMap { w in w.entries.map(\.exerciseId) }).count
        let prs = Records.timeline(h)
        let records = Set(prs.map(\.exerciseId)).count
        let level = Quests.level(Quests.xp(d)).level
        let cardioMin = d.cardio.reduce(0) { $0 + $1.minutes }
        let maxCollection = Shop.collections(d).filter { $0.tier >= 5 }.count
        let weekend = h.filter { Stats.isoWeekday(Stats.day($0.startedAt)) >= 6 }.count
        func p(_ v: Double, _ goal: Double) -> Double { min(1, max(0, v / goal)) }

        func ladder(_ de: String, _ en: String, _ unitDe: (Int) -> String, _ unitEn: (Int) -> String, _ value: Double, _ steps: [Int]) -> [Achievement] {
            steps.enumerated().map { k, t in
                Achievement(title: "\(L(de, en)) · \(tierName(k + 1))", detail: L(unitDe(t), unitEn(t)), progress: p(value, Double(t)), tier: k + 1)
            }
        }

        var out = [
            Achievement(title: L("Erster Schritt", "First step"), detail: L("Erstes Training abgeschlossen", "First workout completed"), progress: p(Double(h.count), 1), tier: 0),
            Achievement(title: L("Frühaufsteher", "Early bird"), detail: L("Training vor 8 Uhr", "Workout before 8 am"), progress: p(Double(hours.filter { $0 < 8 }.count), 1), tier: 0),
            Achievement(title: L("Nachteule", "Night owl"), detail: L("Training nach 21 Uhr", "Workout after 9 pm"), progress: p(Double(hours.filter { $0 >= 21 }.count), 1), tier: 0),
            Achievement(title: L("Ausdauer", "Endurance"), detail: L("Ein Training über 90 Minuten", "A workout over 90 minutes"),
                        progress: p(Double(h.map(\.durationMs).max() ?? 0), 90 * 60_000), tier: 0),
            Achievement(title: L("Erster Rekord", "First record"), detail: L("Einen eigenen Bestwert verbessert", "Beat one of your own bests"), progress: p(Double(records), 1), tier: 0),
        ]
        out += ladder("Trainings", "Workouts", { "\($0) Trainings" }, { "\($0) workouts" }, Double(h.count), [10, 50, 150, 500, 1000])
        out += ladder("Sätze", "Sets", { "\($0) Sätze" }, { "\($0) sets" }, Double(sets), [250, 1000, 5000, 15000, 40000])
        out += ladder("Tonnen", "Tons", { "\($0) Tonnen Volumen" }, { "\($0) tons of volume" }, volume / 1000, [10, 100, 500, 2000, 10000])
        out += ladder("Serie", "Streak", { "\($0) Wochen in Folge das Wochenziel" }, { "Weekly goal \($0) weeks in a row" }, Double(bestStreak), [4, 12, 26, 52, 104])
        out += ladder("Rekordjäger", "Record hunter", { "In \($0) Übungen den Bestwert verbessert" }, { "Beat your best in \($0) exercises" }, Double(records), [5, 15, 40, 80, 150])
        out += ladder("Rekordflut", "Record streak", { "\($0) Rekorde gebrochen" }, { "\($0) records broken" }, Double(prs.count), [10, 50, 150, 400, 1000])
        out += ladder("Vielseitig", "Versatile", { "\($0) verschiedene Übungen" }, { "\($0) different exercises" }, Double(distinct), [15, 30, 60, 120, 250])
        out += ladder("Questjäger", "Quest hunter", { "\($0) Quests erledigt" }, { "\($0) quests done" }, Double(d.questsDone), [25, 100, 300, 1000, 2500])
        out += ladder("Herausforderer", "Challenger", { "\($0) Wochen-Challenges" }, { "\($0) weekly challenges" }, Double(d.challengesDone.count), [5, 15, 40, 100, 200])
        out += ladder("Aufsteiger", "Climber", { "Level \($0) erreicht" }, { "Reach level \($0)" }, Double(level), [5, 15, 30, 50, 75])
        out += ladder("Cardio", "Cardio", { "\($0) Minuten Cardio" }, { "\($0) minutes of cardio" }, Double(cardioMin), [300, 1500, 5000, 15000, 40000])
        out += ladder("Wochenendkrieger", "Weekend warrior", { "\($0) Trainings am Wochenende" }, { "\($0) weekend workouts" }, Double(weekend), [10, 30, 75, 150, 300])
        out += ladder("Sammler", "Collector", { "\($0) Sammlungen auf Stufe V" }, { "\($0) collections at tier V" }, Double(maxCollection), [1, 5, 15, 30, 60])
        out += ladder("Quest-Serie", "Quest streak", { "\($0) Tage in Folge alle Tagesquests" }, { "All daily quests \($0) days in a row" }, Double(d.questStreakBest), [3, 7, 30, 100, 365])
        return out
    }
}

// MARK: - Coins, skills, collections and the shop

enum Shop {
    struct Skill: Identifiable {
        let name: String, level: Int, into: Int, need: Int, sets: Int
        var id: String { name }
        var fraction: Double { min(1, max(0, Double(into) / Double(need))) }
    }

    /** Sets from one level to the next: 10 at first, 5 more each level. */
    private static func skillNeed(_ level: Int) -> Int { 5 + 5 * level }

    static func skillLevel(_ sets: Int) -> (level: Int, into: Int, need: Int) {
        var level = 0
        var left = sets
        while left >= skillNeed(level + 1) { left -= skillNeed(level + 1); level += 1 }
        return (level, left, skillNeed(level + 1))
    }

    /** One skill per big muscle group, from the working sets done for it. */
    static func skills(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }) -> [Skill] {
        Achievements.groups.map { g in
            let sets = d.history.reduce(0) { sum, w in
                sum + w.entries.filter { targetOf($0.exerciseId).map { g.muscles.contains($0) } == true }
                    .reduce(0) { $0 + $1.sets.filter { $0.done && !$0.warmup }.count }
            }
            let l = skillLevel(sets)
            return Skill(name: g.name, level: l.level, into: l.into, need: l.need, sets: sets)
        }
    }

    /** Working sets needed for tier I to X of an exercise's collection. */
    static let tiers = [10, 25, 50, 100, 250, 500, 750, 1000, 1500, 2000]

    struct Collection: Identifiable {
        let exerciseId: String, sets: Int, tier: Int
        var id: String { exerciseId }
        /** Sets for the next tier, or nil at the top. */
        var next: Int? { tier < tiers.count ? tiers[tier] : nil }
        var fraction: Double {
            guard let n = next else { return 1 }
            let from = tier > 0 ? tiers[tier - 1] : 0
            return min(1, max(0, Double(sets - from) / Double(n - from)))
        }
    }

    static func collections(_ d: AppData) -> [Collection] {
        var order: [String] = []
        var sets: [String: Int] = [:]
        for w in d.history {
            for e in w.entries {
                if sets[e.exerciseId] == nil { order.append(e.exerciseId) }
                sets[e.exerciseId, default: 0] += e.sets.filter { $0.done && !$0.warmup }.count
            }
        }
        var list: [Collection] = []
        for id in order {
            let n = sets[id] ?? 0
            if n > 0 { list.append(Collection(exerciseId: id, sets: n, tier: tiers.filter { n >= $0 }.count)) }
        }
        // Highest tier first, then most sets; ties keep the order of first appearance (stable, as on Android).
        let indexed = Array(list.enumerated())
        let sorted = indexed.sorted { (a: (offset: Int, element: Collection), b: (offset: Int, element: Collection)) -> Bool in
            if a.element.tier != b.element.tier { return a.element.tier > b.element.tier }
            if a.element.sets != b.element.sets { return a.element.sets > b.element.sets }
            return a.offset < b.offset
        }
        return sorted.map { $0.element }
    }

    static func roman(_ tier: Int) -> String {
        let r = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
        return tier < r.count ? r[tier] : "\(tier)"
    }

    /** Coins for one workout: 20, plus one per set up to 30. */
    static func workoutCoins(_ w: Workout) -> Int { 20 + min(30, w.doneSets.count) }
    static let recordCoins = 15

    /** Coins earned in all: training, records, challenges, collection tiers, skill levels, achievements, quests. */
    static func earned(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }) -> Int {
        var coins = 0
        for w in d.history { coins += workoutCoins(w) }
        coins += Records.timeline(d.history).count * recordCoins
        coins += d.challengesDone.count * 50
        for c in collections(d) { coins += c.tier * (c.tier + 1) / 2 * 25 }
        for s in skills(d, targetOf: targetOf) { coins += s.level * 10 }
        for a in Achievements.all(d) where a.unlocked { coins += a.coins }
        coins += d.questCoins
        return coins
    }

    static func balance(_ d: AppData, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }) -> Int {
        earned(d, targetOf: targetOf) - d.coinsSpent
    }

    enum Kind { case freeze, reroll, boost, accent, title }

    /** Rarity, as in an RPG: sets the frame colour of an item and orders the shop. */
    enum Rarity: Int, Comparable {
        case common, rare, epic, legendary, mythic
        static func < (a: Rarity, b: Rarity) -> Bool { a.rawValue < b.rawValue }
        var label: String {
            switch self {
            case .common: L("Gewöhnlich", "Common")
            case .rare: L("Selten", "Rare")
            case .epic: L("Episch", "Epic")
            case .legendary: L("Legendär", "Legendary")
            case .mythic: L("Mythisch", "Mythic")
            }
        }
    }

    struct Item: Identifiable {
        let key: String, kind: Kind, title: String, detail: String, price: Int
        var rarity: Rarity = .common
        var level = 1
        var id: String { key }
    }

    /** Premium accent keys, sold in the shop; the plain ones are free. */
    static let premiumAccents = ["gold", "ruby", "emerald", "ocean", "rose"]

    struct TitleDef { let key, de, en: String; let price, level: Int; let rarity: Rarity }

    /** Titles: the better ones need a level first, so there is always one more to work towards. */
    static let titles = [
        TitleDef(key: "rookie", de: "Frischling", en: "Fresh blood", price: 150, level: 1, rarity: .common),
        TitleDef(key: "iron", de: "Eisenbieger", en: "Iron bender", price: 250, level: 3, rarity: .common),
        TitleDef(key: "rat", de: "Gym-Ratte", en: "Gym rat", price: 300, level: 5, rarity: .common),
        TitleDef(key: "bar", de: "Hantelheld", en: "Barbell hero", price: 450, level: 8, rarity: .rare),
        TitleDef(key: "grinder", de: "Satzmaschine", en: "Set grinder", price: 600, level: 10, rarity: .rare),
        TitleDef(key: "beast", de: "Biest", en: "Beast", price: 800, level: 14, rarity: .rare),
        TitleDef(key: "monk", de: "Mönch der Disziplin", en: "Monk of discipline", price: 1000, level: 18, rarity: .epic),
        TitleDef(key: "spartan", de: "Spartaner", en: "Spartan", price: 1300, level: 22, rarity: .epic),
        TitleDef(key: "forge", de: "Schmied des Körpers", en: "Forger of steel", price: 1600, level: 26, rarity: .epic),
        TitleDef(key: "titan", de: "Titan", en: "Titan", price: 2000, level: 30, rarity: .legendary),
        TitleDef(key: "colossus", de: "Koloss", en: "Colossus", price: 2500, level: 36, rarity: .legendary),
        TitleDef(key: "warlord", de: "Kriegsherr", en: "Warlord", price: 3000, level: 42, rarity: .legendary),
        TitleDef(key: "legend", de: "Lebende Legende", en: "Living legend", price: 3600, level: 50, rarity: .mythic),
        TitleDef(key: "olymp", de: "Bewohner des Olymp", en: "Of Olympus", price: 4200, level: 60, rarity: .mythic),
        TitleDef(key: "god", de: "Halbgott", en: "Demigod", price: 4600, level: 68, rarity: .mythic),
        TitleDef(key: "immortal", de: "Unsterblich", en: "Immortal", price: 5000, level: 75, rarity: .mythic),
    ]

    static func titleLabel(_ key: String) -> String? { titles.first { $0.key == key }.map { L($0.de, $0.en) } }

    static let freezeMaxBought = 3
    static let boostHours = 24

    static func items() -> [Item] {
        var out = [
            Item(key: "reroll", kind: .reroll, title: L("Quests neu würfeln", "Reroll quests"), detail: L("Drei neue Tagesquests", "Three new daily quests"), price: 40),
            Item(key: "boost", kind: .boost, title: L("XP-Booster", "XP booster"), detail: L("Doppelte Quest-XP für 24 Stunden", "Double quest XP for 24 hours"), price: 150, rarity: .rare),
            Item(key: "freeze", kind: .freeze, title: L("Joker", "Streak freeze"), detail: L("Rettet die Serie in einer verpassten Woche", "Saves your streak in a missed week"), price: 300, rarity: .epic),
        ]
        for (i, k) in premiumAccents.enumerated() {
            let name = Accent.of(k).label
            out.append(Item(key: "accent:\(k)", kind: .accent, title: L("Farbe \(name)", "\(name) colour"), detail: L("Neue Akzentfarbe für die App", "A new accent colour for the app"),
                            price: 400 + i * 150, rarity: i < 2 ? .rare : .epic, level: 2 + i * 4))
        }
        for t in titles {
            out.append(Item(key: "title:\(t.key)", kind: .title, title: L("Titel „\(t.de)“", "Title \"\(t.en)\""), detail: L("Steht neben deinem Level", "Shown next to your level"),
                            price: t.price, rarity: t.rarity, level: t.level))
        }
        return out
    }

    /** The level an item asks for is reached (titles and colours unlock step by step). */
    static func unlocked(_ d: AppData, _ item: Item) -> Bool { Quests.level(Quests.xp(d)).level >= item.level }

    /** Whether [item] can be bought now (one-off things only once, the freeze up to a cap). */
    static func available(_ d: AppData, _ item: Item, now: Int64 = nowMs()) -> Bool {
        guard unlocked(d, item) else { return false }
        switch item.kind {
        case .freeze: return d.freezes < freezeMaxBought
        case .accent, .title: return !d.owned.contains(item.key)
        case .boost: return now >= d.boostUntil
        case .reroll: return true
        }
    }

    /** Applies a purchase; returns nil when it cannot be bought. */
    static func buy(_ d: AppData, _ item: Item, targetOf: (String) -> String? = { ExerciseRepo.shared[$0]?.target }, today: Date = Stats.day(nowMs()), now: Int64 = nowMs()) -> AppData? {
        guard available(d, item, now: now), balance(d, targetOf: targetOf) >= item.price else { return nil }
        var out = d
        out.coinsSpent += item.price
        switch item.kind {
        case .freeze: out.freezes += 1
        case .reroll:
            let iso = Stats.isoDay(today)
            out.rerollCount = d.rerollDay == iso ? d.rerollCount + 1 : 1
            out.rerollDay = iso
        case .boost: out.boostUntil = now + Int64(boostHours) * 3_600_000
        case .accent, .title: out.owned.insert(item.key)
        }
        return out
    }
}
