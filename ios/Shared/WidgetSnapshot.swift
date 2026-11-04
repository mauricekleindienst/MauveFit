import Foundation

/**
 * What the home screen widget shows, written by the app into the shared App Group container and
 * read by the widget extension. All texts arrive ready to show, in the app's language, so the
 * widget needs none of the app's logic.
 */
struct WidgetSnapshot: Codable, Equatable {
    /** Shared between the app and the widget extension. */
    static let appGroup = "group.dev.mauve.fit"
    private static let fileName = "widget.json"

    /** Accent colours (dark and light tone) of the app's current accent. */
    var accentDark: UInt32 = 0xDB9062
    var accentLight: UInt32 = 0x9E542A

    /** "Als Nächstes" / "Training läuft" and the plan or workout name; nil without any plan. */
    var eyebrow = ""
    var title: String?
    /** "6 Übungen" or, while a workout runs, "Bankdrücken · Satz 2 von 4". */
    var line = ""
    /** Shown when there is no plan at all. */
    var empty = ""
    /** Button label and the link it opens ("mauvefit://start-next", "mauvefit://workout"). */
    var button: String?
    var buttonLink = "mauvefit://open"

    /** The running workout: start, the end of a running rest, sets done of all. */
    var running = false
    var startedAt: Date?
    var restEndsAt: Date?
    var restLabel = ""
    /** "Pause" for the small widget, where the long label does not fit. */
    var restShort = "Pause"
    var setsDone = 0
    var setsTotal = 0

    /** This week: workouts against the goal, the trained weekdays (Monday first) and labels. */
    var week = 0
    var goal = 3
    var trained: [Bool] = Array(repeating: false, count: 7)
    var weekdayLetters: [String] = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]
    var todayIndex = 0
    var weekLabel = ""

    /** This week's challenge. */
    var challengeTitle = ""
    var challengeLabel = ""
    var challengeFraction = 0.0
    var challengeDone = false

    var setsLabel = "Sätze"

    private static var url: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?.appendingPathComponent(fileName)
    }

    static func load() -> WidgetSnapshot? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    /** Writes the snapshot; false when the App Group is not available (unsigned builds). */
    @discardableResult
    func save() -> Bool {
        guard let url = WidgetSnapshot.url, let data = try? JSONEncoder().encode(self) else { return false }
        return (try? data.write(to: url, options: .atomic)) != nil
    }

    /** What the widget gallery shows before the app has written anything. */
    static let placeholder: WidgetSnapshot = {
        var s = WidgetSnapshot()
        s.eyebrow = "Up next"
        s.title = "Push"
        s.line = "5 exercises"
        s.button = "Start"
        s.week = 2
        s.goal = 3
        s.trained = [true, false, true, false, false, false, false]
        s.weekdayLetters = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]
        s.todayIndex = 2
        s.weekLabel = "2 of 3 this week"
        s.challengeTitle = "35 sets in total"
        s.challengeLabel = "16 of 35"
        s.challengeFraction = 0.46
        s.setsLabel = "Sets"
        return s
    }()
}
