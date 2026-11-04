import Foundation
import HealthKit

/**
 * What goes to Apple Health, kept free of HealthKit so it can be tested: a finished workout's
 * time span, and a plausible body weight.
 */
enum HealthMapping {
    /** Start and end of a finished workout; nil while it runs or when the times make no sense. */
    static func interval(_ w: Workout) -> DateInterval? {
        guard let end = w.finishedAt, end > w.startedAt else { return nil }
        return DateInterval(start: Date(ms: w.startedAt), end: Date(ms: end))
    }

    /** The cardio kind of a Health workout type; nil for strength and other non-cardio kinds. */
    static func cardioKind(_ t: HKWorkoutActivityType) -> CardioKind? {
        switch t {
        case .running: .run
        case .cycling, .handCycling: .bike
        case .rowing, .paddleSports: .row
        case .walking, .hiking: .walk
        case .swimming: .swim
        case .elliptical, .stairs, .stairClimbing, .crossCountrySkiing, .jumpRope, .highIntensityIntervalTraining, .mixedCardio, .cardioDance, .stepTraining: .other
        default: nil
        }
    }

    /** Body weights outside 20 to 400 kg are typos, never written to Health. */
    static func validKg(_ kg: Double) -> Bool { kg.isFinite && kg >= 20 && kg <= 400 }

    /** Our id on every sample, so a changed or deleted entry can be found and replaced or removed. */
    static let idKey = "MauveFitId"

    /** Metadata of a workout: our id, the plan name and a summary (sets, volume or reps, the note). */
    static func metadata(_ w: Workout) -> [String: Any] {
        let sets = Fmt.sets(w.doneSets.count)
        let amount = w.volume > 0 ? L("\(Int(w.volume)) kg Volumen", "\(Int(w.volume)) kg volume") : Stats.headline(w)
        let notes = ["\(sets) · \(amount)", w.note].filter { !$0.isEmpty }.joined(separator: "\n")
        return [HKMetadataKeyExternalUUID: w.id, idKey: HealthPlan.workoutId(w), HKMetadataKeyWasUserEntered: false,
                "MauveFitTitle": w.name, "MauveFitNotes": notes]
    }
}

/**
 * What to write to Apple Health, as Android's HealthExport: finished workouts and body weights of
 * the last 90 days. Each entry has a stable id and a fingerprint of its content, so only new or
 * changed entries are written and entries deleted in the app are removed from Health too.
 */
enum HealthPlan {
    static let windowDays: Int64 = 90

    struct Plan: Equatable {
        var workouts: [Workout]
        var weights: [BodyWeight]
        /** Ids written before that no longer exist in the app (or changed and are written anew). */
        var delete: Set<String>
        /** The full state after the plan has run, remembered for the next time. */
        var after: [String: String]
        var empty: Bool { workouts.isEmpty && weights.isEmpty && delete.isEmpty }
    }

    /** The start time is part of the id, so an id alone tells whether it is still inside the window. */
    static func workoutId(_ w: Workout) -> String { "workout-\(w.startedAt)-\(w.id)" }
    static func weightId(_ b: BodyWeight) -> String { "weight-\(b.at)" }

    /** Name, times, sets, volume and note: anything that shows up in another health app. */
    static func fingerprint(_ w: Workout) -> String {
        [w.name, "\(w.startedAt)", "\(w.finishedAt ?? 0)", "\(w.doneSets.count)", "\(w.volume)", w.note].joined(separator: "|")
    }

    static func fingerprint(_ b: BodyWeight) -> String { "\(b.kg)" }

    static func plan(_ d: AppData, synced: [String: String], now: Int64 = nowMs()) -> Plan {
        let since = now - windowDays * 86_400_000
        let workouts = d.history.filter { w in w.finishedAt.map { $0 > w.startedAt } == true && w.startedAt >= since }
        let weights = d.bodyWeights.filter { $0.at >= since && HealthMapping.validKg($0.kg) }
        var current: [String: String] = [:]
        for w in workouts { current[workoutId(w)] = fingerprint(w) }
        for b in weights { current[weightId(b)] = fingerprint(b) }
        // Only ids inside the window are deleted: older ones simply fell out of it.
        let gone = synced.keys.filter { id in
            guard current[id] == nil else { return false }
            let parts = id.split(separator: "-")
            guard parts.count >= 2, let at = Int64(parts[1]) else { return true }
            return at >= since
        }
        let changedWorkouts = workouts.filter { synced[workoutId($0)] != current[workoutId($0)] }
        let changedWeights = weights.filter { synced[weightId($0)] != current[weightId($0)] }
        // A changed entry that was written before is removed first, then written anew.
        let replaced = changedWorkouts.map(workoutId).filter { synced[$0] != nil } + changedWeights.map(weightId).filter { synced[$0] != nil }
        return Plan(workouts: changedWorkouts, weights: changedWeights, delete: Set(gone + replaced), after: current)
    }
}

/**
 * Apple Health, only when the user switches it on in Settings: finished workouts of the last 90
 * days are written as strength training and body weights as body mass, kept in step with edits
 * and deletions, and the latest body mass is read for the nutrition targets. Everything stays on
 * the device in Apple Health.
 */
@MainActor
@Observable
final class Health {
    static let shared = Health()

    /** What was written (id to fingerprint), when, and the last error; kept in its own small file. */
    struct State: Codable {
        var synced: [String: String] = [:]
        var at: Int64 = 0
        var error: String?
    }

    private(set) var state = State()
    @ObservationIgnored private lazy var store = HKHealthStore()
    @ObservationIgnored private let bodyMass = HKQuantityType(.bodyMass)
    @ObservationIgnored private var running = false
    @ObservationIgnored private var again: AppData?

    private static var file: URL {
        Store.defaultFile().deletingLastPathComponent().appendingPathComponent("health-sync.json")
    }

    init() {
        if let data = try? Data(contentsOf: Health.file), let s = try? JSONDecoder().decode(State.self, from: data) { state = s }
    }

    /** No Health on this device (iPad), or a test or screenshot run, which never asks. */
    var available: Bool {
        HKHealthStore.isHealthDataAvailable() && !Demo.enabled
            && ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil
    }

    /** Asks for access when the setting is switched on; true when the prompt was shown and answered. */
    func requestAccess() async -> Bool {
        guard available else { return false }
        do {
            try await store.requestAuthorization(toShare: [HKObjectType.workoutType(), bodyMass], read: [bodyMass, HKObjectType.workoutType(), HKQuantityType(.heartRate)])
            return true
        } catch {
            return false
        }
    }

    /** Forgets what was written, e.g. when the switch goes off, so turning it on again writes all. */
    func forget() {
        state = State()
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(state) { try? data.write(to: Health.file, options: .atomic) }
    }

    /** Writes what changed since last time; quietly does nothing without access. */
    func sync(_ d: AppData) async {
        guard available, d.settings.healthWrite else { return }
        // One sync at a time; a change during it is synced right after.
        if running { again = d; return }
        running = true
        defer {
            running = false
            if let next = again { again = nil; Task { await sync(next) } }
        }
        let canWorkouts = store.authorizationStatus(for: HKObjectType.workoutType()) == .sharingAuthorized
        let canWeights = store.authorizationStatus(for: bodyMass) == .sharingAuthorized
        guard canWorkouts || canWeights else { return }
        let plan = HealthPlan.plan(d, synced: state.synced)
        guard !plan.empty else { return }
        do {
            if !plan.delete.isEmpty {
                let ids = Array(plan.delete)
                let predicate = HKQuery.predicateForObjects(withMetadataKey: HealthMapping.idKey, allowedValues: ids)
                if canWorkouts { _ = try await store.deleteObjects(of: HKObjectType.workoutType(), predicate: predicate) }
                if canWeights { _ = try await store.deleteObjects(of: bodyMass, predicate: predicate) }
            }
            if canWorkouts { for w in plan.workouts { try await write(w) } }
            if canWeights {
                let samples = plan.weights.map { b in
                    HKQuantitySample(type: bodyMass, quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: b.kg),
                                     start: Date(ms: b.at), end: Date(ms: b.at), metadata: [HealthMapping.idKey: HealthPlan.weightId(b)])
                }
                if !samples.isEmpty { try await store.save(samples) }
            }
            state = State(synced: plan.after, at: nowMs(), error: nil)
        } catch {
            state.error = error.localizedDescription
        }
        persist()
    }

    private func write(_ w: Workout) async throws {
        guard let span = HealthMapping.interval(w) else { return }
        let config = HKWorkoutConfiguration()
        config.activityType = .traditionalStrengthTraining
        config.locationType = .indoor
        let builder = HKWorkoutBuilder(healthStore: store, configuration: config, device: .local())
        do {
            try await builder.beginCollection(at: span.start)
            try await builder.addMetadata(HealthMapping.metadata(w))
            try await builder.endCollection(at: span.end)
            _ = try await builder.finishWorkout()
        } catch {
            builder.discardWorkout()
            throw error
        }
    }

    /**
     * Cardio sessions of the last [days] days from Apple Health (the watch, other apps), never the
     * strength workouts this app wrote itself; nil without access.
     */
    func recentCardio(days: Int = 14) async -> [CardioSession]? {
        guard available else { return nil }
        let since = Date().addingTimeInterval(-Double(days) * 86_400)
        let query = HKSampleQueryDescriptor(
            predicates: [.workout(HKQuery.predicateForSamples(withStart: since, end: nil))],
            sortDescriptors: [SortDescriptor(\.startDate, order: .reverse)],
            limit: 200
        )
        guard let workouts = try? await query.result(for: store) else { return nil }
        return workouts.compactMap { w -> CardioSession? in
            guard w.metadata?[HealthMapping.idKey] == nil, let kind = HealthMapping.cardioKind(w.workoutActivityType) else { return nil }
            let km = w.statistics(for: HKQuantityType(.distanceWalkingRunning))?.sumQuantity()
                ?? w.statistics(for: HKQuantityType(.distanceCycling))?.sumQuantity()
                ?? w.statistics(for: HKQuantityType(.distanceSwimming))?.sumQuantity()
            let hr = w.statistics(for: HKQuantityType(.heartRate))?.averageQuantity()
            return CardioSession(
                at: w.startDate.ms, kind: kind, minutes: max(1, Int((w.duration / 60).rounded())),
                distanceKm: km.map { $0.doubleValue(for: .meterUnit(with: .kilo)) }.flatMap { $0 >= 0.1 ? ($0 * 10).rounded() / 10 : nil },
                avgHr: hr.map { Int($0.doubleValue(for: HKUnit.count().unitDivided(by: .minute())).rounded()) },
                source: "health"
            )
        }
    }

    /** The newest body mass in Health, in kg. */
    func latestWeight() async -> Double? {
        guard available else { return nil }
        let query = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: bodyMass)],
            sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)],
            limit: 1
        )
        guard let sample = try? await query.result(for: store).first else { return nil }
        let kg = sample.quantity.doubleValue(for: .gramUnit(with: .kilo))
        return HealthMapping.validKg(kg) ? kg : nil
    }
}
