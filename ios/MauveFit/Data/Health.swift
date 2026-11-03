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

    /** Body weights outside 20 to 400 kg are typos, never written to Health. */
    static func validKg(_ kg: Double) -> Bool { kg.isFinite && kg >= 20 && kg <= 400 }

    /** Metadata on each workout, so the same workout is not written twice. */
    static func metadata(_ w: Workout) -> [String: Any] {
        [HKMetadataKeyExternalUUID: w.id, HKMetadataKeyWasUserEntered: false]
    }
}

/**
 * Apple Health, only when the user switches it on in Settings: finished workouts are written as
 * strength training, body weights as body mass, and the latest body mass can be read for the
 * nutrition targets. Everything stays on the device in Apple Health.
 */
@MainActor
final class Health {
    static let shared = Health()

    private lazy var store = HKHealthStore()
    private let bodyMass = HKQuantityType(.bodyMass)

    /** No Health on this device (iPad), or a test or screenshot run, which never asks. */
    var available: Bool {
        HKHealthStore.isHealthDataAvailable() && !Demo.enabled
            && ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil
    }

    /** Asks for access when the setting is switched on; true when the prompt was shown and answered. */
    func requestAccess() async -> Bool {
        guard available else { return false }
        do {
            try await store.requestAuthorization(toShare: [HKObjectType.workoutType(), bodyMass], read: [bodyMass])
            return true
        } catch {
            return false
        }
    }

    /** Writes a finished workout as traditional strength training, once. */
    func save(_ w: Workout) async {
        guard available, let span = HealthMapping.interval(w),
              store.authorizationStatus(for: HKObjectType.workoutType()) == .sharingAuthorized else { return }
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
        }
    }

    func saveWeight(_ kg: Double, at: Date = Date()) async {
        guard available, HealthMapping.validKg(kg), store.authorizationStatus(for: bodyMass) == .sharingAuthorized else { return }
        let sample = HKQuantitySample(type: bodyMass, quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: kg), start: at, end: at)
        try? await store.save(sample)
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
