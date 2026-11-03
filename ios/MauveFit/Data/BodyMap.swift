import SwiftUI

/**
 * The body outline and muscle regions from react-native-body-highlighter (MIT, see
 * THIRD_PARTY_NOTICES.md), converted once into plain move/line/curve commands in bodymap.json.
 * Both sides share one 724 x 1448 coordinate space.
 */
enum BodySide: String { case front, back }

final class BodyMap: @unchecked Sendable {
    static let shared = BodyMap(bundle: .main)

    static let size = CGSize(width: 724, height: 1448)

    struct Side {
        let outline: Path
        let parts: [String: Path]
    }

    let front: Side
    let back: Side

    init(bundle: Bundle) {
        struct Raw: Decodable {
            struct S: Decodable { let outline: String; let parts: [String: [String]] }
            let front: S
            let back: S
        }
        let raw = bundle.url(forResource: "bodymap", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }
            .flatMap { try? JSONDecoder().decode(Raw.self, from: $0) }
        func side(_ s: Raw.S?) -> Side {
            guard let s else { return Side(outline: Path(), parts: [:]) }
            var parts: [String: Path] = [:]
            for (slug, list) in s.parts {
                var p = Path()
                for cmd in list { p.addPath(BodyMap.path(cmd)) }
                parts[slug] = p
            }
            return Side(outline: BodyMap.path(s.outline), parts: parts)
        }
        front = side(raw?.front)
        back = side(raw?.back)
    }

    subscript(side: BodySide) -> Side { side == .front ? front : back }

    /** Reads "M x y L x y C x1 y1 x2 y2 x y Z", the only commands the converter writes. */
    static func path(_ commands: String) -> Path {
        var p = Path()
        let t = commands.split(separator: " ")
        var i = 0
        func num() -> CGFloat {
            defer { i += 1 }
            return i < t.count ? CGFloat(Double(t[i]) ?? 0) : 0
        }
        func point() -> CGPoint { CGPoint(x: num(), y: num()) }
        while i < t.count {
            let c = t[i]
            i += 1
            switch c {
            case "M": p.move(to: point())
            case "L": p.addLine(to: point())
            case "C":
                let c1 = point(), c2 = point(), end = point()
                p.addCurve(to: end, control1: c1, control2: c2)
            case "Z": p.closeSubpath()
            default: break
            }
        }
        return p
    }
}

/** Which map regions a muscle of the exercise data covers. */
enum MuscleRegions {
    /**
     * Every muscle name used in exercises.json, as targets or secondary muscles. An empty list
     * means "nothing to highlight" on purpose (cardio). A unit test keeps this complete.
     */
    static let map: [String: [String]] = [
        "abs": ["abs"], "abdominals": ["abs"], "lower abs": ["abs"], "core": ["abs", "obliques"],
        "obliques": ["obliques"],
        "quads": ["quadriceps"], "quadriceps": ["quadriceps"], "hip flexors": ["quadriceps"],
        "lats": ["upper-back"], "latissimus dorsi": ["upper-back"], "upper back": ["upper-back"],
        "back": ["upper-back"], "rhomboids": ["upper-back"],
        "spine": ["lower-back"], "lower back": ["lower-back"],
        "calves": ["calves"], "soleus": ["calves"], "shins": ["tibialis"],
        "pectorals": ["chest"], "chest": ["chest"], "upper chest": ["chest"], "serratus anterior": ["chest"],
        "glutes": ["gluteal"], "abductors": ["gluteal"],
        "hamstrings": ["hamstring"],
        "adductors": ["adductors"], "inner thighs": ["adductors"], "groin": ["adductors"],
        "triceps": ["triceps"],
        "biceps": ["biceps"], "brachialis": ["biceps"],
        "delts": ["deltoids"], "shoulders": ["deltoids"], "deltoids": ["deltoids"], "rear deltoids": ["deltoids"],
        "rotator cuff": ["deltoids"],
        "forearms": ["forearm"], "wrists": ["forearm"], "wrist flexors": ["forearm"], "wrist extensors": ["forearm"],
        "grip muscles": ["forearm"],
        "traps": ["trapezius"], "trapezius": ["trapezius"],
        "levator scapulae": ["neck"], "sternocleidomastoid": ["neck"],
        "ankles": ["ankles"], "ankle stabilizers": ["ankles"], "feet": ["feet"], "hands": ["hands"],
        "cardiovascular system": [],
    ]

    /** Regions drawn only on the back of the figure. */
    private static let backOnly: Set<String> = ["upper-back", "lower-back", "gluteal", "hamstring"]
    /** Regions on both sides that read better from behind. */
    private static let backFirst: Set<String> = ["triceps", "trapezius", "calves"]

    static func regions(_ muscles: [String]) -> Set<String> {
        Set(muscles.flatMap { map[$0] ?? [] })
    }

    /** The side that shows what the exercise mainly trains; the back for back-only regions. */
    static func side(for e: Exercise) -> BodySide {
        let target = regions([e.target])
        if !target.isDisjoint(with: backOnly) || (!target.isEmpty && target.isSubset(of: backFirst.union(backOnly))) {
            return .back
        }
        return .front
    }
}
