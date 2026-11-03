import Foundation

/**
 * One entry of exercises-dataset (github.com/hasaneyldrm/exercises-dataset), slimmed to the
 * fields the app uses. The JSON files are copied from the Android assets at build time. The
 * dataset's media are not used: the app draws a muscle map and, where one matches, an
 * Everkinetic drawing, all bundled, so it never goes online.
 */
struct Exercise: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var category: String = ""
    var equipment: String = ""
    var target: String = ""
    var secondary: [String] = []
    var steps: [String] = []

    enum CodingKeys: String, CodingKey { case id, name, category, equipment, target, secondary, steps }

    init(id: String, name: String, category: String = "", equipment: String = "", target: String = "") {
        self.id = id; self.name = name; self.category = category; self.equipment = equipment; self.target = target
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        category = c.value(.category, ""); equipment = c.value(.equipment, ""); target = c.value(.target, "")
        secondary = c.value(.secondary, []); steps = c.value(.steps, [])
    }

    /** Display name in the app's language; [name] stays the English key. */
    var title: String { (I18n.german ? ExerciseRepo.shared.namesDe[id] : nil) ?? titleEn }
    var titleEn: String { name.split(separator: " ").map { $0.prefix(1).uppercased() + $0.dropFirst() }.joined(separator: " ") }
    /** Body-weight exercises are logged by reps only. */
    var bodyweight: Bool { equipment == "body weight" }
    var muscles: [String] { [target] + secondary }

}

/** The bundled database, parsed once. */
final class ExerciseRepo: @unchecked Sendable {
    static let shared = ExerciseRepo(bundle: .main)

    let all: [Exercise]
    let namesDe: [String: String]
    private let stepsDe: [String: [String]]
    private let byId: [String: Exercise]

    init(bundle: Bundle) {
        func load<T: Decodable>(_ name: String, _ type: T.Type) -> T? {
            guard let url = bundle.url(forResource: name, withExtension: "json"),
                  let data = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(T.self, from: data)
        }
        all = (load("exercises", [Exercise].self) ?? []).sorted { $0.name < $1.name }
        namesDe = load("exercise_names_de", [String: String].self) ?? [:]
        stepsDe = load("exercises_de", [String: [String]].self) ?? [:]
        byId = Dictionary(all.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    }

    subscript(id: String) -> Exercise? { byId[id] }

    /** The how-to in the app's language, falling back to English. */
    func steps(_ e: Exercise) -> [String] {
        if I18n.german, let de = stepsDe[e.id], de.count == e.steps.count { return de }
        return e.steps
    }

    var categories: [String] { Array(Set(all.map(\.category))).sorted { Labels.category($0) < Labels.category($1) } }

    /** Every exercise sorted by its name in the app's language, made once per language. */
    private var sortedCache: [Bool: [Exercise]] = [:]
    private let lock = NSLock()

    func sortedByTitle() -> [Exercise] {
        let german = I18n.german
        lock.lock(); defer { lock.unlock() }
        if let s = sortedCache[german] { return s }
        let locale = I18n.locale
        let s = all.map { ($0, $0.title) }
            .sorted { $0.1.compare($1.1, options: [.caseInsensitive, .diacriticInsensitive], locale: locale) == .orderedAscending }
            .map(\.0)
        sortedCache[german] = s
        return s
    }

    /**
     * Exercises matching every word of [query] in either name, a muscle or the equipment, sorted
     * by name in the app's language with favourites first, as in the Android library.
     */
    func search(_ query: String, category: String? = nil, favorites: Set<String> = [], favoritesOnly: Bool = false) -> [Exercise] {
        let terms = query.lowercased().split(separator: " ").map(String.init)
        let found = sortedByTitle().filter { e in
            guard category == nil || e.category == category, !favoritesOnly || favorites.contains(e.id) else { return false }
            if terms.isEmpty { return true }
            let haystack = ([e.name, e.title.lowercased(), Labels.equipment(e.equipment).lowercased()]
                + e.muscles.flatMap { [$0, Labels.muscle($0).lowercased()] }).joined(separator: " ")
            return terms.allSatisfy { haystack.contains($0) }
        }
        return found.filter { favorites.contains($0.id) } + found.filter { !favorites.contains($0.id) }
    }
}

/** Display names for the dataset's English vocabulary, in German or English. */
enum Labels {
    private static let musclesDe: [String: String] = [
        "abductors": "Abduktoren", "abs": "Bauch", "adductors": "Adduktoren", "biceps": "Bizeps",
        "calves": "Waden", "cardiovascular system": "Ausdauer", "delts": "Schultern", "forearms": "Unterarme",
        "glutes": "Gesäß", "hamstrings": "Beinbeuger", "lats": "Latissimus", "levator scapulae": "Nacken",
        "pectorals": "Brust", "quads": "Quadrizeps", "serratus anterior": "Sägezahnmuskel", "spine": "Rückenstrecker",
        "traps": "Trapez", "triceps": "Trizeps", "upper back": "Oberer Rücken", "lower back": "Unterer Rücken",
        "hip flexors": "Hüftbeuger", "obliques": "Seitl. Bauch", "core": "Rumpf", "shoulders": "Schultern",
        "chest": "Brust", "quadriceps": "Quadrizeps", "rhomboids": "Rhomboiden", "rotator cuff": "Rotatoren",
        "deltoids": "Schultern", "trapezius": "Trapez", "wrists": "Handgelenke", "ankles": "Sprunggelenke",
        "soleus": "Waden", "latissimus dorsi": "Latissimus", "rear deltoids": "Hintere Schulter",
    ]
    private static let categoriesDe: [String: String] = [
        "back": "Rücken", "cardio": "Cardio", "chest": "Brust", "lower arms": "Unterarme",
        "lower legs": "Unterschenkel", "neck": "Nacken", "shoulders": "Schultern", "upper arms": "Arme",
        "upper legs": "Beine", "waist": "Bauch",
    ]
    private static let equipmentDe: [String: String] = [
        "body weight": "Körpergewicht", "dumbbell": "Kurzhantel", "barbell": "Langhantel", "cable": "Kabelzug",
        "leverage machine": "Maschine", "band": "Band", "resistance band": "Widerstandsband", "smith machine": "Multipresse",
        "kettlebell": "Kettlebell", "weighted": "Zusatzgewicht", "stability ball": "Gymnastikball", "ez barbell": "SZ-Stange",
        "sled machine": "Beinpresse", "assisted": "Assistiert", "medicine ball": "Medizinball", "olympic barbell": "Olympiastange",
        "trap bar": "Trap Bar", "rope": "Seil", "roller": "Faszienrolle", "bosu ball": "Bosu", "wheel roller": "Bauchroller",
        "upper body ergometer": "Armergometer", "skierg machine": "Ski-Ergometer", "hammer": "Hammer", "stationary bike": "Ergometer",
        "tire": "Reifen", "elliptical machine": "Crosstrainer", "stepmill machine": "Treppensteiger",
    ]

    // English: the dataset's own words, with a few made friendlier.
    private static let musclesEn: [String: String] = [
        "abs": "Abs", "cardiovascular system": "Cardio", "delts": "Shoulders", "pectorals": "Chest",
        "quads": "Quads", "lats": "Lats", "spine": "Lower back", "levator scapulae": "Neck",
        "serratus anterior": "Serratus", "upper back": "Upper back",
    ]
    private static let categoriesEn: [String: String] = [
        "upper arms": "Arms", "upper legs": "Legs", "lower arms": "Forearms", "lower legs": "Calves", "waist": "Core",
    ]
    private static let equipmentEn: [String: String] = [
        "leverage machine": "Machine", "sled machine": "Leg press", "ez barbell": "EZ bar", "wheel roller": "Ab wheel",
        "roller": "Foam roller", "skierg machine": "SkiErg",
    ]

    private static func cap(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }

    static func muscle(_ m: String) -> String { (I18n.german ? musclesDe[m] : musclesEn[m]) ?? cap(m) }
    static func category(_ c: String) -> String { (I18n.german ? categoriesDe[c] : categoriesEn[c]) ?? cap(c) }
    static func equipment(_ e: String) -> String { (I18n.german ? equipmentDe[e] : equipmentEn[e]) ?? cap(e) }
}
