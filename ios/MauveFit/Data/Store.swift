import Foundation
import Observation
import UserNotifications

/** All app state in one JSON file, written shortly after every change. */
@MainActor
@Observable
final class Store {
    private(set) var data: AppData
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
                ExerciseEntry(
                    exerciseId: p.exerciseId, restSec: p.restSec,
                    sets: Array(repeating: SetEntry(weight: startWeight(d, p.exerciseId, reps: p.reps), reps: p.reps, target: p.reps), count: max(1, p.sets)),
                    superset: p.superset
                )
            }
            var w = Workout(name: routine?.name ?? L("Freies Training", "Free workout"), routineId: routine?.id, entries: entries)
            w.lastActivityAt = nowMs()
            d.active = w
        }
    }

    /** Last top weight, one step up when every set reached the target last time. */
    func startWeight(_ d: AppData, _ exerciseId: String, reps: Int) -> Double {
        if repo[exerciseId]?.bodyweight == true { return 0 }
        let last = Stats.lastSets(d.history, exerciseId)
        return Stats.roundTo(Stats.suggestWeight(last, targetReps: reps, step: d.settings.weightStep), d.settings.weightStep)
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
        updateActive { w in
            guard w.entries.indices.contains(entry), w.entries[entry].sets.indices.contains(set) else { return }
            w.entries[entry].sets[set].done.toggle()
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
        updateActive { w in
            let reps = 10
            let weight = startWeight(data, exerciseId, reps: reps)
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

    /** Moves the running rest timer by [seconds]; at or below zero it ends. */
    func shiftRest(_ seconds: Int) {
        updateActive { w in
            guard let end = w.restEndsAt else { return }
            let next = end + Int64(seconds) * 1000
            w.restEndsAt = next > nowMs() ? next : nil
            w.restTotalSec = max(0, w.restTotalSec + seconds)
        }
        if let end = data.active?.restEndsAt { RestAlarm.schedule(seconds: Int((end - nowMs()) / 1000)) } else { RestAlarm.cancel() }
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

    func addFood(_ e: FoodEntry) { update { $0.food.insert(e, at: 0) } }
    func deleteFood(_ id: String) { update { $0.food.removeAll { $0.id == id } } }

    func addWater(_ ml: Int, day: String = Stats.isoDay()) {
        update { d in d.water[day] = max(0, (d.water[day] ?? 0) + ml) }
    }

    func logWeight(_ kg: Double) {
        update { d in
            // One entry per day: a second weigh-in replaces the first.
            let today = Stats.day(nowMs())
            d.bodyWeights.removeAll { Stats.day($0.at) == today }
            d.bodyWeights.insert(BodyWeight(at: nowMs(), kg: kg), at: 0)
        }
    }

    func updateSettings(_ block: (inout Settings) -> Void) { update { block(&$0.settings) } }
    func updateProfile(_ block: (inout Profile) -> Void) { update { block(&$0.profile) } }
}

/** A local notification at the end of a rest, so it is heard with the app in the background. */
enum RestAlarm {
    private static let id = "rest"

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    static func schedule(seconds: Int) {
        cancel()
        guard seconds > 0 else { return }
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
