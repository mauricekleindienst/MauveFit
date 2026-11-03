import Foundation

/** Daily targets from body weight and goal, as in the Android app. */
enum Nutrition {
    struct Targets: Equatable {
        let kcal: Int, protein: Int, carbs: Int, fat: Int, waterMl: Int
    }

    /**
     * Rough daily targets: maintenance about 33 kcal per kg, protein 1.6 to 2.2 g per kg, fat
     * 0.9 g per kg, the rest carbohydrates; 35 ml water per kg.
     */
    static func targets(kg: Double, goal: Goal) -> Targets {
        let maintenance = kg * 33
        let kcal: Double = switch goal {
        case .FatLoss: maintenance - 450
        case .Muscle: maintenance + 250
        case .Strength: maintenance + 150
        case .Fitness: maintenance
        }
        let protein = kg * (goal == .FatLoss ? 2.2 : goal == .Fitness ? 1.6 : 2.0)
        let fat = kg * 0.9
        let carbs = max((kcal - protein * 4 - fat * 9) / 4, kg * 1.5)
        // With very low body weights the carb floor kicks in; the target then has to cover it.
        let total = max(kcal, protein * 4 + fat * 9 + carbs * 4)
        return Targets(
            kcal: Int(total.rounded()), protein: Int(protein.rounded()), carbs: Int(carbs.rounded()),
            fat: Int(fat.rounded()), waterMl: Int((kg * 35 / 250).rounded()) * 250
        )
    }

    /** Body weight the targets assume when none is logged. */
    static let defaultKg = 75.0
}
