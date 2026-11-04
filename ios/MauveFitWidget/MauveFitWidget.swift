import SwiftUI
import WidgetKit

/**
 * The home screen widget (#12): the next workout, or the running one with its rest, and this
 * week's progress. The app writes a WidgetSnapshot into the shared App Group after every change
 * and asks WidgetKit to reload; this only reads it.
 */
@main
struct MauveFitWidgets: WidgetBundle {
    var body: some Widget { NextWorkoutWidget() }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: Date(), snapshot: .placeholder) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: Date(), snapshot: context.isPreview ? .placeholder : (WidgetSnapshot.load() ?? .placeholder)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let now = Date()
        let s = WidgetSnapshot.load() ?? .placeholder
        var entries = [SnapshotEntry(date: now, snapshot: s)]
        // When a rest runs out the widget switches to the set to do now.
        if let end = s.restEndsAt, end > now {
            var after = s
            after.restEndsAt = nil
            entries.append(SnapshotEntry(date: end, snapshot: after))
        }
        // A new day starts a new week strip and quests: redraw after midnight at the latest.
        let midnight = Calendar.current.nextDate(after: now, matching: DateComponents(hour: 0, minute: 1), matchingPolicy: .nextTime) ?? now.addingTimeInterval(3600)
        completion(Timeline(entries: entries, policy: .after(midnight)))
    }
}

struct NextWorkoutWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "next-workout", provider: SnapshotProvider()) { entry in
            NextWorkoutEntryView(entry: entry)
        }
        .configurationDisplayName("Mauve Fit")
        .description(Bundle.main.preferredLocalizations.first?.hasPrefix("de") == true
                     ? "Das nächste Training und deine Woche."
                     : "Your next workout and your week.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct NextWorkoutEntryView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        NextWidgetView(s: entry.snapshot, size: family == .systemSmall ? .small : family == .systemMedium ? .medium : .large)
            .containerBackground(for: .widget) { WidgetInk.bg }
            .widgetURL(URL(string: entry.snapshot.running ? "mauvefit://workout" : "mauvefit://open"))
    }
}
