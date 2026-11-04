import Foundation

/**
 * Sample data for screenshots. `-demoData` starts the app on a throwaway file filled with three
 * weeks of workouts, body weights and today's food; `-demoWorkout` also opens a running workout
 * with the first set ticked off and the rest timer going, `-demoSummary` a just finished one
 * with a new record, and `-demoLight` switches to the light theme. The user's own data is
 * never touched.
 */
enum Demo {
    /** Debug builds only: in the App Store build no launch argument reaches the sample data. */
    private static func has(_ arg: String) -> Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains(arg)
        #else
        false
        #endif
    }
    static var enabled: Bool { has("-demoData") || empty }
    /** A throwaway store with nothing in it, like a fresh install, for the empty-state walk. */
    static var empty: Bool { has("-emptyData") }
    /**
     * The iPhone Duo's screens drawn at their size, scaled to fit: "inner" (626 x 890 pt) or
     * "outer" (466 x 678 pt). No simulator has the Duo yet, so the screenshot test uses this.
     */
    static var duo: String? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-demoDuo"), i + 1 < args.count else { return nil }
        return args[i + 1]
        #else
        return nil
        #endif
    }
    /** Shows the widget layouts in the app, for the screenshot test (WidgetKit cannot be captured there). */
    static var widgets: Bool { has("-demoWidgets") }
    static var workout: Bool { has("-demoWorkout") || has("-demoSummary") }
    /** The workout the summary shows, set once the demo store has made it. */
    nonisolated(unsafe) private(set) static var summaryId: String?

    @MainActor
    static func store() -> Store {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("demo-data.json")
        let dir = file.deletingLastPathComponent()
        for name in ["demo-data.json", "data.prev.json"] { try? FileManager.default.removeItem(at: dir.appendingPathComponent(name)) }
        let store = Store(file: file)
        if empty { return store }
        store.update { seed(&$0) }
        if has("-demoLight") { store.updateSettings { $0.theme = "light" } }
        if has("-demoSummary") {
            // Every set of the first three exercises, the first one 5 kg heavier than ever.
            store.startWorkout(store.data.routines.first { $0.id == "tpl-push" })
            store.updateActive { w in
                w.startedAt = nowMs() - 58 * 60_000
                for i in w.entries.indices.prefix(3) {
                    for j in w.entries[i].sets.indices {
                        if i == 0 { w.entries[i].sets[j].weight += 5 }
                        w.entries[i].sets[j].done = true
                    }
                }
            }
            summaryId = store.finishWorkout()
        } else if workout {
            store.startWorkout(store.data.routines.first { $0.id == "tpl-push" })
            store.toggleDone(0, 0)
        }
        return store
    }

    /** Top weights per exercise for the first demo week; later weeks add a little. */
    private static let base: [String: Double] = [
        "0025": 70, "0314": 24, "0405": 20, "0334": 8, "0201": 25,
        "0652": 0, "0027": 60, "0861": 55, "0203": 20, "0313": 14,
        "0043": 90, "0085": 70, "1463": 140, "0586": 40, "0585": 45, "0605": 60,
    ]

    static func seed(_ d: inout AppData) {
        d.profile.goal = .Muscle
        d.profile.onboarded = true
        d.settings.weeklyGoal = 3
        let cal = Stats.calendar
        let today = cal.startOfDay(for: Date())
        let weekStart = Stats.weekStart(today)
        let plans = ["tpl-push", "tpl-pull", "tpl-legs"]
        var history: [Workout] = []
        // Three sessions in each of the last three weeks, and the ones already done this week.
        var days: [Date] = []
        for back in [3, 2, 1] {
            let start = cal.date(byAdding: .weekOfYear, value: -back, to: weekStart)!
            for offset in [0, 2, 4] { days.append(cal.date(byAdding: .day, value: offset, to: start)!) }
        }
        var day = weekStart
        while day < today, days.count < 11 {
            days.append(day)
            day = cal.date(byAdding: .day, value: 2, to: day)!
        }
        for (i, date) in days.enumerated() {
            guard let plan = d.routines.first(where: { $0.id == plans[i % plans.count] }) else { continue }
            let progress = Double(i / plans.count) * 2.5
            var w = Workout(name: plan.name, routineId: plan.id)
            w.startedAt = cal.date(bySettingHour: 18, minute: 5, second: 0, of: date)!.ms
            w.finishedAt = w.startedAt + Int64(52 + i % 3 * 6) * 60_000
            w.feel = 1 + i % 3
            w.entries = plan.exercises.map { p in
                let weight = (base[p.exerciseId] ?? 20) + (base[p.exerciseId] == 0 ? 0 : progress)
                let sets = (0..<p.sets).map { s in SetEntry(weight: weight, reps: max(1, p.reps - (s == p.sets - 1 ? 1 : 0)), done: true, target: p.reps) }
                return ExerciseEntry(exerciseId: p.exerciseId, restSec: p.restSec, sets: sets)
            }
            history.append(w)
        }
        d.history = history.sorted { $0.startedAt > $1.startedAt }
        d.bodyWeights = (0..<12).map { i in
            BodyWeight(at: cal.date(byAdding: .day, value: -i * 2, to: today)!.ms + 7 * 3_600_000, kg: 81.6 - Double(11 - i) * 0.15 + (i % 2 == 0 ? 0.1 : 0))
        }
        let iso = Stats.isoDay(today)
        d.food = [
            FoodEntry(date: iso, name: L("Haferflocken mit Beeren und Skyr", "Oats with berries and skyr"), kcal: 520, protein: 34, carbs: 72, fat: 10),
            FoodEntry(date: iso, name: L("Hähnchen, Reis und Brokkoli", "Chicken, rice and broccoli"), kcal: 710, protein: 52, carbs: 85, fat: 14),
            FoodEntry(date: iso, name: L("Proteinshake", "Protein shake"), kcal: 180, protein: 30, carbs: 6, fat: 3),
        ]
        d.water = [iso: 1750]
        d.favorites = ["0025", "0043", "0652"]
    }
}
