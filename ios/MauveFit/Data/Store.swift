import Foundation
import Observation
import UserNotifications

/** All app state in one JSON file, written shortly after every change. */
@MainActor
@Observable
final class Store {
    private(set) var data: AppData
    /** The newest body weight in Apple Health, when syncing is on; never saved to the file. */
    private(set) var healthKg: Double?
    @ObservationIgnored private let file: URL
    @ObservationIgnored private let repo: ExerciseRepo
    @ObservationIgnored private var saveTask: Task<Void, Never>?

    nonisolated static func defaultFile() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("data.json")
    }

    init(file: URL = Store.defaultFile(), repo: ExerciseRepo = .shared) {
        self.file = file
        self.repo = repo
        data = Store.load(file)
        I18n.apply(data.settings.language)
    }

    /**
     * Reads the data file, falling back to the copy from the last start. A file that cannot be
     * read is moved aside, never overwritten, so nothing is lost for good.
     */
    private static func load(_ file: URL) -> AppData {
        let fm = FileManager.default
        let prev = file.deletingLastPathComponent().appendingPathComponent("data.prev.json")
        func read(_ url: URL) -> AppData? {
            guard let d = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(AppData.self, from: d)
        }
        if let d = read(file) {
            // A known-good copy for the next start, in case this file breaks later.
            try? fm.removeItem(at: prev)
            try? fm.copyItem(at: file, to: prev)
            return d
        }
        if fm.fileExists(atPath: file.path) {
            let broken = file.deletingLastPathComponent().appendingPathComponent("data.broken-\(nowMs()).json")
            try? fm.copyItem(at: file, to: broken)
        }
        return read(prev) ?? AppData()
    }

    func update(_ block: (inout AppData) -> Void) {
        var d = data
        block(&d)
        guard d != data else { return }
        if d.settings.language != data.settings.language { I18n.apply(d.settings.language) }
        if d.settings.accent != data.settings.accent { Ink.accentChoice = Accent.of(d.settings.accent) }
        data = d
        scheduleSave()
    }

    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            self?.flush()
        }
    }

    /** Writes immediately; called when the app goes to the background so a quick kill loses nothing. */
    func flush() {
        saveTask?.cancel()
        let enc = JSONEncoder()
        enc.outputFormatting = [.sortedKeys]
        // Written beside and swapped in, so a crash mid-write never leaves a truncated file.
        if let bytes = try? enc.encode(data) { try? bytes.write(to: file, options: .atomic) }
    }

    // MARK: - Workout flow

    /** Starts a workout from a plan (or an empty one); does nothing while another workout runs. */
    func startWorkout(_ routine: Routine?) {
        update { d in
            guard d.active == nil else { return }
            let entries = (routine?.exercises ?? []).map { p in
                let weight = Coach.startWeight(d, repo, p.exerciseId, reps: p.reps)
                let reps = Coach.startReps(d, p.exerciseId, planned: p.reps)
                return ExerciseEntry(
                    exerciseId: p.exerciseId, restSec: p.restSec,
                    sets: Array(repeating: SetEntry(weight: weight, reps: reps, target: p.reps), count: max(1, p.sets)),
                    superset: p.superset
                )
            }
            var w = Workout(name: routine?.name ?? L("Freies Training", "Free workout"), routineId: routine?.id, entries: entries)
            w.lastActivityAt = nowMs()
            d.active = w
        }
    }

    /**
     * Ends a workout nobody has touched for [Coach.idleLimitMs]: done sets are saved with the
     * last activity as the end, an empty one is dropped. Returns true when it did something.
     */
    @discardableResult
    func closeIdleWorkout(now: Int64 = nowMs()) -> Bool {
        guard let w = data.active, now - w.lastActive >= Coach.idleLimitMs else { return false }
        finishWorkout(endAt: w.lastActive + 5 * 60_000)
        return true
    }

    func updateActive(_ block: (inout Workout) -> Void) {
        update { d in
            guard var w = d.active else { return }
            block(&w)
            w.lastActivityAt = nowMs()
            d.active = w
        }
    }

    func setSet(_ entry: Int, _ set: Int, _ block: (inout SetEntry) -> Void) {
        updateActive { w in
            guard w.entries.indices.contains(entry), w.entries[entry].sets.indices.contains(set) else { return }
            block(&w.entries[entry].sets[set])
        }
    }

    /** Ticks a set off or back on; ticking starts the rest timer of that exercise. */
    func toggleDone(_ entry: Int, _ set: Int) {
        var startRest: Int?
        var stopRest = false
        updateActive { w in
            guard w.entries.indices.contains(entry), w.entries[entry].sets.indices.contains(set) else { return }
            w.entries[entry].sets[set].done.toggle()
            // Unticking the last ticked set again (a mis-tap) takes back the rest it started.
            if !w.entries[entry].sets[set].done, w.restEndsAt != nil, !w.entries.contains(where: { $0.sets.contains(where: \.done) }) {
                w.restEndsAt = nil
                stopRest = true
            }
            if w.entries[entry].sets[set].done {
                let rest = w.entries[entry].restSec
                if rest > 0 {
                    w.restEndsAt = nowMs() + Int64(rest) * 1000
                    w.restTotalSec = rest
                    startRest = rest
                }
            }
        }
        if let s = startRest { RestAlarm.schedule(seconds: s) }
        if stopRest { RestAlarm.cancel() }
    }

    func addSet(_ entry: Int) {
        updateActive { w in
            guard w.entries.indices.contains(entry) else { return }
            var s = w.entries[entry].sets.last ?? SetEntry(reps: 10)
            s.done = false; s.warmup = false
            w.entries[entry].sets.append(s)
        }
    }

    func removeSet(_ entry: Int, _ set: Int) {
        updateActive { w in
            guard w.entries.indices.contains(entry), w.entries[entry].sets.indices.contains(set), w.entries[entry].sets.count > 1 else { return }
            w.entries[entry].sets.remove(at: set)
        }
    }

    func addExercise(_ exerciseId: String) {
        let d = data
        updateActive { w in
            // Last time's reps (or what was planned then), else ten.
            let reps = Stats.lastSets(d.history, exerciseId).first.map { $0.target > 0 ? $0.target : $0.reps } ?? 10
            let weight = Coach.startWeight(d, repo, exerciseId, reps: reps)
            w.entries.append(ExerciseEntry(
                exerciseId: exerciseId, restSec: data.settings.defaultRest,
                sets: Array(repeating: SetEntry(weight: weight, reps: reps, target: reps), count: 3)
            ))
        }
    }

    func removeEntry(_ entry: Int) {
        updateActive { w in if w.entries.indices.contains(entry) { w.entries.remove(at: entry) } }
    }

    func setRest(_ entry: Int, seconds: Int) {
        updateActive { w in if w.entries.indices.contains(entry) { w.entries[entry].restSec = max(0, seconds) } }
    }

    /**
     * Moves the running rest timer by [seconds]; at or below zero it ends. More rest after the
     * timer already ran out counts from now.
     */
    func shiftRest(_ seconds: Int) {
        updateActive { w in
            guard let end = w.restEndsAt else { return }
            let now = nowMs()
            let next = (seconds > 0 ? max(end, now) : end) + Int64(seconds) * 1000
            w.restEndsAt = next > nowMs() ? next : nil
            w.restTotalSec = max(0, w.restTotalSec + seconds)
        }
        if let end = data.active?.restEndsAt { RestAlarm.schedule(seconds: Int((end - nowMs()) / 1000)) } else { RestAlarm.cancel() }
    }

    /** Ends the rest that ends at [end], unless it was changed meanwhile; not counted as activity. */
    func endRest(_ end: Int64) {
        update { d in if d.active?.restEndsAt == end { d.active?.restEndsAt = nil } }
    }

    func skipRest() {
        updateActive { $0.restEndsAt = nil }
        RestAlarm.cancel()
    }

    /** Saves the active workout; returns its id, or nil if nothing was ticked off (then it is just closed). */
    @discardableResult
    func finishWorkout(endAt: Int64? = nil) -> String? {
        var saved: String?
        update { d in
            guard var w = d.active else { return }
            // Unticked sets are dropped; an exercise with nothing done is not logged.
            w.entries = w.entries.map { e in var e = e; e.sets = e.sets.filter(\.done); return e }
                .filter { e in e.sets.contains { !$0.warmup } }
            d.active = nil
            if !w.entries.isEmpty {
                w.finishedAt = endAt ?? nowMs()
                w.restEndsAt = nil
                d.history.insert(w, at: 0)
                saved = w.id
            }
        }
        RestAlarm.cancel()
        if data.settings.healthWrite, let w = data.history.first, w.id == saved {
            Task { await Health.shared.save(w) }
        }
        return saved
    }

    func discardWorkout() {
        update { $0.active = nil }
        RestAlarm.cancel()
    }

    func updateWorkout(_ id: String, _ block: (inout Workout) -> Void) {
        update { d in if let i = d.history.firstIndex(where: { $0.id == id }) { block(&d.history[i]) } }
    }

    func deleteWorkout(_ id: String) { update { $0.history.removeAll { $0.id == id } } }

    // MARK: - Plans

    func saveRoutine(_ r: Routine) {
        update { d in
            if let i = d.routines.firstIndex(where: { $0.id == r.id }) { d.routines[i] = r } else { d.routines.append(r) }
        }
    }

    func deleteRoutine(_ id: String) { update { $0.routines.removeAll { $0.id == id } } }

    func moveRoutines(from: IndexSet, to: Int) { update { $0.routines.move(fromOffsets: from, toOffset: to) } }

    func toggleFavorite(_ exerciseId: String) {
        update { d in
            if d.favorites.contains(exerciseId) { d.favorites.remove(exerciseId) } else { d.favorites.insert(exerciseId) }
        }
    }

    // MARK: - Food, water, body

    /** Oldest diary day kept; anything before it is dropped on the next write, as on Android. */
    private static func keepFrom() -> String { Stats.isoDay(Stats.calendar.date(byAdding: .day, value: -60, to: Date())!) }

    func addFood(_ e: FoodEntry) {
        let from = Store.keepFrom()
        update { d in
            d.food.insert(e, at: 0)
            d.food.removeAll { $0.date < from }
        }
    }

    func deleteFood(_ id: String) { update { $0.food.removeAll { $0.id == id } } }

    /** Adds (or with a negative [ml] removes) water for [day], kept between 0 and 10 l. */
    func addWater(_ ml: Int, day: String = Stats.isoDay()) {
        let from = Store.keepFrom()
        update { d in
            d.water[day] = min(10_000, max(0, (d.water[day] ?? 0) + ml))
            d.water = d.water.filter { $0.key >= from && $0.value > 0 }
        }
    }

    func logWeight(_ kg: Double) {
        update { d in
            // One entry per day: a second weigh-in replaces the first.
            let today = Stats.day(nowMs())
            d.bodyWeights.removeAll { Stats.day($0.at) == today }
            d.bodyWeights.insert(BodyWeight(at: nowMs(), kg: kg), at: 0)
        }
        if data.settings.healthWrite { Task { await Health.shared.saveWeight(kg) } }
    }

    /** The body weight the targets use: the app's own log first, else Apple Health's. */
    var bodyWeight: Double? { Stats.latestWeight(data) ?? healthKg }

    /** Reads the latest body mass from Apple Health when syncing is on. */
    func refreshHealthWeight() {
        guard data.settings.healthWrite else { healthKg = nil; return }
        Task { healthKg = await Health.shared.latestWeight() }
    }

    /**
     * Switches Apple Health on (asking for access first, in context) or off. Stays off when the
     * device has no Health or the prompt fails.
     */
    func setHealth(_ on: Bool) async {
        if on {
            guard await Health.shared.requestAccess() else { return }
            updateSettings { $0.healthWrite = true }
            refreshHealthWeight()
        } else {
            updateSettings { $0.healthWrite = false }
            healthKg = nil
        }
    }

    func updateSettings(_ block: (inout Settings) -> Void) { update { block(&$0.settings) } }

    // MARK: - Quests, coins and the shop

    /** Banks the XP of quests just finished; true when something new was claimed. */
    @discardableResult
    func claimQuests() -> Bool {
        let next = Quests.claim(data)
        guard next.questsClaimed != data.questsClaimed else { return false }
        update { $0 = next }
        return true
    }

    /** Buys [item] when it is available and affordable; true when it went through. */
    @discardableResult
    func buy(_ item: Shop.Item) -> Bool {
        guard let next = Shop.buy(data, item) else { return false }
        update { $0 = next }
        flush()
        return true
    }

    /** The bought title to wear next to the level, or "" for the level's own. */
    func setTitle(_ key: String) { update { $0.title = key } }

    /** Books a completed weekly challenge once; true when it is new. */
    @discardableResult
    func completeChallenge(_ week: String) -> Bool {
        guard !data.challengesDone.contains(week) else { return false }
        update { $0.challengesDone.insert(week) }
        return true
    }

    /** Books every finished week since the last call: earns freezes, spends one on a missed week. */
    func settleStreak() {
        let d = data
        let s = Motivation.settle(d.history, goal: d.settings.weeklyGoal,
                                  Motivation.FreezeState(freezes: d.freezes, frozen: d.frozenWeeks, settled: d.streakSettled, run: d.streakRun))
        update { $0.freezes = s.freezes; $0.frozenWeeks = s.frozen; $0.streakSettled = s.settled; $0.streakRun = s.run }
    }

    /** Deletes everything and starts over with the starter plans, saved right away. */
    func reset() {
        RestAlarm.cancel()
        data = AppData()
        I18n.apply(data.settings.language)
        Ink.accentChoice = Accent.of(data.settings.accent)
        flush()
    }
    func updateProfile(_ block: (inout Profile) -> Void) { update { block(&$0.profile) } }
}

/** A local notification at the end of a rest, so it is heard with the app in the background. */
enum RestAlarm {
    private static let id = "rest"

    /** Test and screenshot runs never show the permission prompt. */
    private static var quiet: Bool { Demo.enabled || ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

    /**
     * Asked the first time a rest starts, where the reason is obvious, not at launch. iOS shows
     * the prompt only once; later calls just return the answer.
     */
    static func requestPermission() {
        guard !quiet else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    static func schedule(seconds: Int) {
        cancel()
        guard seconds > 0 else { return }
        requestPermission()
        let c = UNMutableNotificationContent()
        c.title = L("Pause vorbei", "Rest is over")
        c.body = L("Weiter mit dem nächsten Satz.", "On to the next set.")
        c.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(seconds), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: c, trigger: trigger))
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
    }
}
