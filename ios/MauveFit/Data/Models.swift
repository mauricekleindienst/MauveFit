import Foundation

// Same shapes and keys as the Android app's data.json (data/Models.kt), so a backup from one
// can be read by the other. Missing or unknown values fall back to defaults instead of failing.

func newId() -> String { UUID().uuidString.lowercased() }

/** Milliseconds since 1970, the time unit the Android app stores. */
func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

extension Date {
    init(ms: Int64) { self.init(timeIntervalSince1970: Double(ms) / 1000) }
    var ms: Int64 { Int64(timeIntervalSince1970 * 1000) }
}

extension KeyedDecodingContainer {
    /** The value for [key], or [fallback] when it is missing or does not decode. */
    func value<T: Decodable>(_ key: Key, _ fallback: @autoclosure () -> T) -> T {
        if let v = try? decodeIfPresent(T.self, forKey: key) { return v }
        return fallback()
    }

    /** The optional value for [key]; nil when it is missing or does not decode. */
    func opt<T: Decodable>(_ key: Key) -> T? {
        if let v = try? decodeIfPresent(T.self, forKey: key) { return v }
        return nil
    }
}

/** One exercise slot in a plan: what to aim for each time it is trained. */
struct PlanExercise: Codable, Hashable, Identifiable {
    var uid = newId()
    var exerciseId: String
    var sets: Int = 3
    var reps: Int = 10
    var restSec: Int = 90
    /** Consecutive exercises sharing this id form a superset. */
    var superset: String? = nil

    var id: String { uid }

    enum CodingKeys: String, CodingKey { case exerciseId, sets, reps, restSec, superset }

    init(exerciseId: String, sets: Int = 3, reps: Int = 10, restSec: Int = 90, superset: String? = nil) {
        self.exerciseId = exerciseId; self.sets = sets; self.reps = reps; self.restSec = restSec; self.superset = superset
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        exerciseId = try c.decode(String.self, forKey: .exerciseId)
        sets = c.value(.sets, 3); reps = c.value(.reps, 10); restSec = c.value(.restSec, 90)
        superset = c.opt(.superset)
    }
}

struct Routine: Codable, Hashable, Identifiable {
    var id: String = newId()
    var name: String
    var note: String = ""
    var exercises: [PlanExercise] = []

    enum CodingKeys: String, CodingKey { case id, name, note, exercises }

    init(id: String = newId(), name: String, note: String = "", exercises: [PlanExercise] = []) {
        self.id = id; self.name = name; self.note = note; self.exercises = exercises
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.value(.id, newId()); name = c.value(.name, "")
        note = c.value(.note, ""); exercises = c.value(.exercises, [])
    }
}

enum SetType: String, Codable, CaseIterable { case Normal, Drop, Failure }

struct SetEntry: Codable, Hashable {
    var weight: Double = 0
    var reps: Int = 0
    var done = false
    /** Warm-up sets are logged but left out of volume and records. */
    var warmup = false
    /** Reps the plan asked for (0 = unknown). */
    var target: Int = 0
    var type: SetType = .Normal
    /** Rate of perceived exertion, 6 to 10 (0 = not rated). */
    var rpe: Int = 0

    enum CodingKeys: String, CodingKey { case weight, reps, done, warmup, target, type, rpe }

    init(weight: Double = 0, reps: Int = 0, done: Bool = false, warmup: Bool = false, target: Int = 0) {
        self.weight = weight; self.reps = reps; self.done = done; self.warmup = warmup; self.target = target
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        weight = c.value(.weight, 0); reps = c.value(.reps, 0); done = c.value(.done, false)
        warmup = c.value(.warmup, false); target = c.value(.target, 0)
        type = c.value(.type, .Normal); rpe = c.value(.rpe, 0)
    }
}

struct ExerciseEntry: Codable, Hashable, Identifiable {
    var uid = newId()
    var exerciseId: String
    var restSec: Int = 90
    var sets: [SetEntry] = [SetEntry()]
    var superset: String? = nil
    var load: Double = 1
    var fullTop: Double = 0

    var id: String { uid }

    enum CodingKeys: String, CodingKey { case exerciseId, restSec, sets, superset, load, fullTop }

    init(exerciseId: String, restSec: Int = 90, sets: [SetEntry] = [SetEntry()], superset: String? = nil) {
        self.exerciseId = exerciseId; self.restSec = restSec; self.sets = sets; self.superset = superset
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        exerciseId = try c.decode(String.self, forKey: .exerciseId)
        restSec = c.value(.restSec, 90); sets = c.value(.sets, [SetEntry()])
        superset = c.opt(.superset); load = c.value(.load, 1); fullTop = c.value(.fullTop, 0)
    }
}

/** A workout in progress (AppData.active) or finished (AppData.history). */
struct Workout: Codable, Hashable, Identifiable {
    var id: String = newId()
    var name: String
    var routineId: String? = nil
    var startedAt: Int64 = nowMs()
    var finishedAt: Int64? = nil
    var entries: [ExerciseEntry] = []
    /** Wall-clock time the running rest timer ends, so it survives the app being killed. */
    var restEndsAt: Int64? = nil
    var restTotalSec: Int = 0
    /** How it felt (0 = not rated, 1 easy, 2 good, 3 hard) and a free note. */
    var feel: Int = 0
    var note: String = ""
    var lastActivityAt: Int64 = 0

    /** When the workout was last touched, falling back to its start. */
    var lastActive: Int64 { lastActivityAt > 0 ? lastActivityAt : startedAt }

    enum CodingKeys: String, CodingKey {
        case id, name, routineId, startedAt, finishedAt, entries, restEndsAt, restTotalSec, feel, note, lastActivityAt
    }

    init(name: String, routineId: String? = nil, entries: [ExerciseEntry] = []) {
        self.name = name; self.routineId = routineId; self.entries = entries
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.value(.id, newId()); name = c.value(.name, "")
        routineId = c.opt(.routineId); startedAt = c.value(.startedAt, nowMs())
        finishedAt = c.opt(.finishedAt); entries = c.value(.entries, [])
        restEndsAt = c.opt(.restEndsAt); restTotalSec = c.value(.restTotalSec, 0)
        feel = c.value(.feel, 0); note = c.value(.note, ""); lastActivityAt = c.value(.lastActivityAt, 0)
    }

    /** Completed working sets (warm-ups excluded). */
    var doneSets: [(String, SetEntry)] {
        entries.flatMap { e in e.sets.filter { $0.done && !$0.warmup }.map { (e.exerciseId, $0) } }
    }
    var volume: Double { doneSets.reduce(0) { $0 + $1.1.weight * Double($1.1.reps) } }
    var durationMs: Int64 { (finishedAt ?? nowMs()) - startedAt }
}

enum Goal: String, Codable, CaseIterable, Identifiable {
    case Strength, Muscle, Fitness, FatLoss
    var id: String { rawValue }
    var label: String {
        switch self {
        case .Strength: L("Kraft", "Strength")
        case .Muscle: L("Muskelaufbau", "Build muscle")
        case .Fitness: L("Fitness", "Fitness")
        case .FatLoss: L("Abnehmen", "Lose fat")
        }
    }
}

enum Level: String, Codable, CaseIterable { case Beginner, Intermediate, Advanced }
enum Setup: String, Codable, CaseIterable { case Gym, Dumbbells, Home }

struct Profile: Codable, Hashable {
    var goal: Goal = .Muscle
    var level: Level = .Beginner
    var days: Int = 3
    var setup: Setup = .Gym
    var onboarded = false
    var heightCm: Int = 0
    var birthYear: Int = 0

    enum CodingKeys: String, CodingKey { case goal, level, days, setup, onboarded, heightCm, birthYear }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        goal = c.value(.goal, .Muscle); level = c.value(.level, .Beginner); days = c.value(.days, 3)
        setup = c.value(.setup, .Gym); onboarded = c.value(.onboarded, false)
        heightCm = c.value(.heightCm, 0); birthYear = c.value(.birthYear, 0)
    }
}

struct BodyWeight: Codable, Hashable {
    var at: Int64
    var kg: Double
}

struct Settings: Codable, Hashable {
    var weeklyGoal: Int = 3
    var defaultRest: Int = 90
    var weightStep: Double = 2.5
    var barWeight: Double = 20
    var vibrate = true
    var sound = true
    /** "system", "de" or "en". */
    var language: String = "system"
    /** "system", "dark" or "light". */
    var theme: String = "system"
    /** Accent key, see Accent. */
    var accent: String = "copper"
    /** Write finished workouts and body weights to Apple Health (Android: Health Connect). */
    var healthWrite = false
    /** Target body weight in kg; 0 = none. */
    var goalWeight: Double = 0

    enum CodingKeys: String, CodingKey { case weeklyGoal, defaultRest, weightStep, barWeight, vibrate, sound, language, theme, accent, healthWrite, goalWeight }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        weeklyGoal = c.value(.weeklyGoal, 3); defaultRest = c.value(.defaultRest, 90)
        weightStep = c.value(.weightStep, 2.5); barWeight = c.value(.barWeight, 20)
        vibrate = c.value(.vibrate, true); sound = c.value(.sound, true); language = c.value(.language, "system")
        theme = c.value(.theme, "system"); accent = c.value(.accent, "copper")
        healthWrite = c.value(.healthWrite, false); goalWeight = c.value(.goalWeight, 0)
    }
}

/** One thing eaten on [date] (ISO yyyy-MM-dd). */
struct FoodEntry: Codable, Hashable, Identifiable {
    var id: String = newId()
    var date: String
    var name: String
    var kcal: Int
    var protein: Int
    var carbs: Int = 0
    var fat: Int = 0
    var recipeId: String? = nil

    enum CodingKeys: String, CodingKey { case id, date, name, kcal, protein, carbs, fat, recipeId }

    init(date: String, name: String, kcal: Int, protein: Int, carbs: Int = 0, fat: Int = 0) {
        self.date = date; self.name = name; self.kcal = kcal; self.protein = protein; self.carbs = carbs; self.fat = fat
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.value(.id, newId()); date = c.value(.date, ""); name = c.value(.name, "")
        kcal = c.value(.kcal, 0); protein = c.value(.protein, 0)
        carbs = c.value(.carbs, 0); fat = c.value(.fat, 0); recipeId = c.opt(.recipeId)
    }
}

struct AppData: Codable, Hashable {
    var routines: [Routine] = Templates.routines
    var history: [Workout] = []
    var active: Workout? = nil
    var favorites: Set<String> = []
    var settings = Settings()
    var profile = Profile()
    var bodyWeights: [BodyWeight] = []
    /** Food diary and water per day (ISO date to ml). */
    var food: [FoodEntry] = []
    var water: [String: Int] = [:]
    /** Cardio sessions, logged by hand or taken over from Apple Health, newest first. */
    var cardio: [CardioSession] = []
    /** Start times of imported sessions deleted in the app, so an import skips them. */
    var cardioDismissed: [Int64] = []
    /** ISO weeks ("2026-W44") whose weekly challenge was completed; its size is the count shown. */
    var challengesDone: Set<String> = []
    /** Streak freezes in stock (0 to 2) and the missed weeks they covered, as ISO week keys. */
    var freezes = 0
    var frozenWeeks: Set<String> = []
    /** Last week the freeze bookkeeping settled ("" = never) and the goal weeks in a row up to it. */
    var streakSettled = ""
    var streakRun = 0
    /** Quest keys already paid out (recent ones only), XP banked from quests, quests done in all. */
    var questsClaimed: [String] = []
    var questXp = 0
    var questsDone = 0
    /** Shop: coins banked from quests, coins spent, things bought ("accent:gold", "title:iron"). */
    var questCoins = 0
    var coinsSpent = 0
    var owned: Set<String> = []
    /** Title bought in the shop and shown next to the level, by key; "" for the level's own. */
    var title = ""
    /** Rerolls of today's quests and the day they belong to; end of a bought XP booster. */
    var rerollDay = ""
    var rerollCount = 0
    var boostUntil: Int64 = 0
    /** Days in a row with all three daily quests done: the current run, its last day and the best. */
    var questStreak = 0
    var questStreakDay = ""
    var questStreakBest = 0

    enum CodingKeys: String, CodingKey {
        case routines, history, active, favorites, settings, profile, bodyWeights, food, water
        case cardio, cardioDismissed
        case challengesDone, freezes, frozenWeeks, streakSettled, streakRun, questsClaimed, questXp, questsDone
        case questCoins, coinsSpent, owned, title, rerollDay, rerollCount, boostUntil, questStreak, questStreakDay, questStreakBest
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        routines = c.value(.routines, Templates.routines); history = c.value(.history, [])
        active = c.opt(.active); favorites = c.value(.favorites, [])
        settings = c.value(.settings, Settings()); profile = c.value(.profile, Profile())
        bodyWeights = c.value(.bodyWeights, []); food = c.value(.food, []); water = c.value(.water, [:])
        cardio = c.value(.cardio, []); cardioDismissed = c.value(.cardioDismissed, [])
        challengesDone = c.value(.challengesDone, []); freezes = c.value(.freezes, 0); frozenWeeks = c.value(.frozenWeeks, [])
        streakSettled = c.value(.streakSettled, ""); streakRun = c.value(.streakRun, 0)
        questsClaimed = c.value(.questsClaimed, []); questXp = c.value(.questXp, 0); questsDone = c.value(.questsDone, 0)
        questCoins = c.value(.questCoins, 0); coinsSpent = c.value(.coinsSpent, 0); owned = c.value(.owned, []); title = c.value(.title, "")
        rerollDay = c.value(.rerollDay, ""); rerollCount = c.value(.rerollCount, 0); boostUntil = c.value(.boostUntil, 0)
        questStreak = c.value(.questStreak, 0); questStreakDay = c.value(.questStreakDay, ""); questStreakBest = c.value(.questStreakBest, 0)
    }
}
