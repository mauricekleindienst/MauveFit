import Foundation

/**
 * The part of the Android coach (data/Coach.kt) that fills in a new workout: starting weights and
 * reps. Blocks, light weeks, readiness and the comeback cut are not ported yet.
 */
enum Coach {
    /** A workout nobody has touched for this long is closed on the next start. */
    static let idleLimitMs: Int64 = 3 * 60 * 60_000

    /** Body-weight multiples of a one-rep max for beginners, intermediates and advanced lifters. */
    private static let strengthRatios: [(String, (Double, Double, Double))] = [
        ("deadlift", (0.8, 1.15, 1.5)),
        ("squat", (0.65, 0.95, 1.3)),
        ("bench press", (0.5, 0.75, 1.0)),
        ("row", (0.45, 0.65, 0.85)),
        ("shoulder press", (0.33, 0.48, 0.65)),
        ("overhead press", (0.33, 0.48, 0.65)),
        ("military press", (0.33, 0.48, 0.65)),
        ("curl", (0.2, 0.3, 0.4)),
    ]

    /**
     * A careful first weight for a barbell or dumbbell lift that has never been logged, from body
     * weight and level: 80 % of the weight for [reps] at a typical max.
     */
    static func firstEstimate(_ e: Exercise, bodyKg: Double?, level: Level, reps: Int, bar: Double = 20) -> Double? {
        guard let bodyKg, bodyKg > 0 else { return nil }
        let perHand: Double
        switch e.equipment {
        case "barbell": perHand = 1
        case "dumbbell": perHand = 0.4
        default: return nil
        }
        guard let r = strengthRatios.first(where: { e.name.contains($0.0) })?.1 else { return nil }
        let ratio = level == .Beginner ? r.0 : level == .Intermediate ? r.1 : r.2
        let w = bodyKg * ratio * perHand / (1 + Double(reps) / 30) * 0.8
        // An empty barbell is the least a barbell lift can start with.
        return e.equipment == "barbell" ? max(w, bar) : w
    }

    /**
     * Last time's top weight, one step up when every set reached the target. Never logged: a
     * similar exercise's record (same muscle and equipment) at 85 %, else a first estimate.
     */
    static func startWeight(_ d: AppData, _ repo: ExerciseRepo, _ exerciseId: String, reps: Int) -> Double {
        let step = d.settings.weightStep
        // Drop and failure sets end a session on purpose; the straight sets say whether to go up.
        let all = Stats.lastSets(d.history, exerciseId)
        let normal = all.filter { $0.type == .Normal }
        let last = normal.isEmpty ? all : normal
        var w: Double
        if !last.isEmpty {
            w = Stats.suggestWeight(last, targetReps: reps, step: step)
        } else {
            let e = repo[exerciseId]
            let similar = Stats.records(d.history).filter { r in
                guard let o = repo[r.exerciseId], let e else { return false }
                return o.target == e.target && o.equipment == e.equipment && r.best.weight > 0
            }.map(\.e1rm).max()
            if let similar {
                w = similar / (1 + Double(reps) / 30) * 0.85
            } else if let e {
                let body = d.bodyWeights.max { $0.at < $1.at }?.kg
                w = firstEstimate(e, bodyKg: body, level: d.profile.level, reps: reps, bar: d.settings.barWeight) ?? 0
            } else {
                w = 0
            }
        }
        return w <= 0 ? 0 : max(step, Stats.roundTo(w, step))
    }

    /**
     * Reps to prefill: the plan's, except for an exercise done without weight where last time
     * every set reached the target: then one more than the best set.
     */
    static func startReps(_ d: AppData, _ exerciseId: String, planned: Int) -> Int {
        let last = Stats.lastSets(d.history, exerciseId)
        guard !last.isEmpty, !last.contains(where: { $0.weight > 0 }) else { return planned }
        let target = last.first { $0.target > 0 }?.target ?? planned
        return last.allSatisfy { $0.reps >= target } ? max(planned, (last.map(\.reps).max() ?? 0) + 1) : planned
    }
}
