import Foundation

/**
 * Interval timer maths, as on Android (data/Tools.kt): the screen keeps only a start time and the
 * time banked before a pause; everything shown is derived from the elapsed milliseconds, so the
 * countdown never drifts.
 */
enum Intervals {
    enum Phase { case work, rest, done }

    /** [rounds] rounds of [workSec] work, each but the last followed by [restSec] rest. */
    struct Plan: Equatable {
        let workSec: Int, restSec: Int, rounds: Int
        var totalMs: Int64 { Int64(rounds * workSec) * 1000 + Int64(max(0, rounds - 1) * restSec) * 1000 }
    }

    /** Where the timer stands: the phase, its 1-based round, what is left of it and its length. */
    struct Tick: Equatable { let phase: Phase, round: Int, remainingMs: Int64, phaseMs: Int64 }

    /** Every minute on the minute. */
    static func emom(_ rounds: Int) -> Plan { Plan(workSec: 60, restSec: 0, rounds: rounds) }

    /** 20 s on, 10 s off, eight times. */
    static let tabata = Plan(workSec: 20, restSec: 10, rounds: 8)

    static func at(_ plan: Plan, _ elapsedMs: Int64) -> Tick {
        let work = Int64(plan.workSec) * 1000
        let rest = Int64(max(0, plan.restSec)) * 1000
        if plan.rounds <= 0 || work <= 0 || elapsedMs >= plan.totalMs { return Tick(phase: .done, round: max(0, plan.rounds), remainingMs: 0, phaseMs: 0) }
        let e = max(0, elapsedMs)
        let cycle = work + rest
        let index = e / cycle
        let inCycle = e - index * cycle
        return inCycle < work
            ? Tick(phase: .work, round: Int(index) + 1, remainingMs: work - inCycle, phaseMs: work)
            : Tick(phase: .rest, round: Int(index) + 1, remainingMs: cycle - inCycle, phaseMs: rest)
    }

    /** Whole seconds left, rounded up, so "0:01" shows until the phase really ends. */
    static func secondsLeft(_ t: Tick) -> Int { Int((t.remainingMs + 999) / 1000) }

    static func fmt(_ seconds: Int) -> String { String(format: "%d:%02d", seconds / 60, seconds % 60) }
}

/** One-rep max estimate and the loads that follow from it. */
enum Strength {
    struct Row: Equatable { let percent: Int; let weight: Double; let reps: Int }

    /** Same Epley estimate as the progress charts. */
    static func oneRm(_ weight: Double, _ reps: Int) -> Double { Stats.e1rm(SetEntry(weight: weight, reps: reps)) }

    /** Reps that fit a share of the 1RM: Epley turned round, at least one. */
    static func repsAt(_ percent: Int) -> Int { max(1, Int((30.0 * (100.0 / Double(percent) - 1)).rounded())) }

    /** 95 % down to 50 % in fives, each rounded to the weight step. */
    static func table(_ oneRm: Double, step: Double) -> [Row] {
        stride(from: 95, through: 50, by: -5).map { p in Row(percent: p, weight: Stats.roundTo(oneRm * Double(p) / 100, step), reps: repsAt(p)) }
    }
}

extension Coach {
    private static let plateSizes = [25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25]

    /** Plates per side for a barbell load, to the nearest load the smallest plate allows; nil below the bar. */
    static func plates(_ total: Double, bar: Double) -> [Double]? {
        // Bounded: a typed 100000000 kg would otherwise loop millions of times.
        guard total >= bar, total.isFinite, total <= 1000 else { return nil }
        var side = ((total - bar) / 2 / 1.25).rounded() * 1.25
        var out: [Double] = []
        for p in plateSizes { while side >= p - 1e-6 { out.append(p); side -= p } }
        return out
    }
}
