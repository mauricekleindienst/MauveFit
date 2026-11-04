import Foundation

/**
 * Supersets, as on Android (data/Supersets.kt): consecutive exercises with the same group id are
 * trained alternately, one set of each in turn, and the rest only comes after the round.
 */
enum Supersets {
    /**
     * Keeps groups well-formed after edits: a group needs two or more neighbours, and an id that
     * shows up again further down (after a move) starts a group of its own.
     */
    static func clean(_ groups: [String?]) -> [String?] {
        var out = groups
        var seen: Set<String> = []
        var i = 0
        while i < groups.count {
            let g = groups[i]
            var end = i
            if let g { while end + 1 < groups.count && groups[end + 1] == g { end += 1 } }
            if let g {
                let fixed: String? = end == i ? nil : seen.contains(g) ? newId() : g
                seen.insert(g)
                for j in i...end { out[j] = fixed }
            }
            i = end + 1
        }
        return out
    }

    /** The neighbours trained together with [index], or nil if it is not part of a superset. */
    static func members(_ groups: [String?], _ index: Int) -> ClosedRange<Int>? {
        guard groups.indices.contains(index), let g = groups[index] else { return nil }
        var start = index, end = index
        while start > 0 && groups[start - 1] == g { start -= 1 }
        while end < groups.count - 1 && groups[end + 1] == g { end += 1 }
        return end > start ? start...end : nil
    }

    /** "A1", "A2", "B1"...: the group's letter in this plan or workout and the position within it. */
    static func label(_ groups: [String?], _ index: Int) -> String? {
        guard let run = members(groups, index) else { return nil }
        let letter = (0..<run.lowerBound).filter { j in members(groups, j)?.lowerBound == j }.count
        return "\(Character(UnicodeScalar(65 + letter)!))\(index - run.lowerBound + 1)"
    }

    /** Where to go after a set: the exercise to show next, and whether the round is over and rest is due. */
    struct Step: Equatable { let index: Int; let rest: Bool }

    /**
     * After a set at [index]: the next exercise of the group that still has sets, without rest;
     * once the round is through, rest and start over at the first one with sets left, or move on.
     */
    static func afterSet(_ entries: [ExerciseEntry], _ index: Int) -> Step? {
        guard let run = members(entries.map(\.superset), index) else { return nil }
        func open(_ j: Int) -> Bool { entries[j].sets.contains { !$0.done } }
        if index < run.upperBound, let next = (index + 1...run.upperBound).first(where: open) { return Step(index: next, rest: false) }
        return Step(index: run.first(where: open) ?? run.upperBound + 1, rest: true)
    }

    /** Whether the exercise at [i] is trained as a superset with the one after it. */
    static func linkedWithNext(_ list: [PlanExercise], _ i: Int) -> Bool {
        i + 1 < list.count && list[i].superset != nil && list[i].superset == list[i + 1].superset
    }

    /** Links the exercise at [i] with the next one, or splits them apart if they already are. */
    static func toggleWithNext(_ list: [PlanExercise], _ i: Int) -> [PlanExercise] {
        guard i + 1 < list.count else { return list }
        var out = list
        let g = list[i].superset
        if linkedWithNext(list, i) {
            // Everything after i in the group continues as a group of its own.
            let split = newId()
            var j = i + 1
            while j < list.count && list[j].superset == g { out[j].superset = split; j += 1 }
        } else {
            let group = g ?? newId()
            let next = list[i + 1].superset
            for j in out.indices where j == i || j == i + 1 || (next != nil && list[j].superset == next) { out[j].superset = group }
        }
        let cleaned = clean(out.map(\.superset))
        for j in out.indices { out[j].superset = cleaned[j] }
        return out
    }
}

extension Coach {
    /** Rest after [set]: short after warm-ups, longer after a set that fell clearly short of its target. */
    static func restFor(_ entry: ExerciseEntry, _ set: SetEntry) -> Int {
        if set.warmup { return 45 }
        if set.target > 0 && set.reps <= set.target - 2 { return entry.restSec + 30 }
        return entry.restSec
    }

    /** Ramp-up sets before [workWeight]: 50 %, 70 % and 85 % of it, never below the bar or above the work set. */
    static func warmups(_ workWeight: Double, step: Double, bar: Double) -> [SetEntry] {
        guard workWeight > bar else { return [] }
        var out: [SetEntry] = []
        for (f, reps) in [(0.5, 8), (0.7, 5), (0.85, 3)] {
            let w = max(bar, Stats.roundTo(workWeight * f, step))
            if w < workWeight - 1e-6 && !out.contains(where: { $0.weight == w }) {
                out.append(SetEntry(weight: w, reps: reps, warmup: true))
            }
        }
        return out
    }

    static func usesBarbell(_ e: Exercise?) -> Bool { ["barbell", "olympic barbell", "smith machine"].contains(e?.equipment ?? "") }
}
