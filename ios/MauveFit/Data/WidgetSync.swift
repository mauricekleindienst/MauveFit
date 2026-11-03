import Foundation
import WidgetKit

/**
 * Keeps the home screen widget in step with the app (#12): after every save the app writes a
 * WidgetSnapshot into the shared App Group and asks WidgetKit to redraw, only when something the
 * widget shows has changed.
 */
@MainActor
enum WidgetSync {
    private static var last: WidgetSnapshot?

    static func snapshot(_ d: AppData, now: Date = Date()) -> WidgetSnapshot {
        var s = WidgetSnapshot()
        let tones = Ink.accentChoice.tones
        s.accentDark = tones.ink
        s.accentLight = tones.paper
        s.setsLabel = L("Sätze", "Sets")
        s.empty = L("Lege einen Plan in der App an", "Create a plan in the app")

        if let w = d.active {
            s.running = true
            s.eyebrow = L("Training läuft", "Workout running")
            s.title = w.name
            s.startedAt = Date(ms: w.startedAt)
            if let end = w.restEndsAt, end > nowMs() { s.restEndsAt = Date(ms: end) }
            s.restLabel = L("Pause · nächster Satz in", "Rest · next set in")
            let working = w.entries.flatMap(\.sets).filter { !$0.warmup }
            s.setsTotal = working.count
            s.setsDone = working.filter(\.done).count
            // The set to do now: the first one not ticked off.
            if let e = w.entries.first(where: { $0.sets.contains { !$0.done } }), let i = e.sets.firstIndex(where: { !$0.done }) {
                let name = ExerciseRepo.shared[e.exerciseId]?.title ?? e.exerciseId
                let set = e.sets[i]
                let number = e.sets.prefix(i + 1).filter { !$0.warmup }.count
                let count = e.sets.filter { !$0.warmup }.count
                s.line = name + " · " + (set.warmup ? L("Aufwärmen", "Warm-up") : L("Satz \(number) von \(count)", "Set \(number) of \(count)"))
            } else {
                s.line = L("Alle Sätze erledigt", "All sets done")
            }
            s.button = L("Öffnen", "Open")
            s.buttonLink = "mauvefit://workout"
        } else if let r = Stats.nextRoutine(d) {
            s.eyebrow = L("Als Nächstes", "Up next")
            s.title = r.name
            s.line = Fmt.exercises(r.exercises.count)
            s.button = L("Start", "Start")
            s.buttonLink = "mauvefit://start-next"
        } else {
            s.eyebrow = L("Als Nächstes", "Up next")
        }

        let goal = max(1, d.settings.weeklyGoal)
        s.week = Stats.workoutsThisWeek(d.history, now: now)
        s.goal = goal
        s.weekLabel = L("\(s.week) von \(goal) diese Woche", "\(s.week) of \(goal) this week")
        let start = Stats.weekStart(now)
        let trained = Stats.weekDays(d.history, now: now)
        let days = (0..<7).map { Stats.addDays(start, $0) }
        s.trained = days.map { trained.contains($0) }
        s.weekdayLetters = days.map { String($0.formatted(Date.FormatStyle().weekday(.abbreviated).locale(I18n.locale)).prefix(2)) }
        s.todayIndex = Stats.isoWeekday(now) - 1

        let c = Motivation.challenge(d, today: now)
        s.challengeTitle = c.title
        s.challengeLabel = c.label
        s.challengeFraction = c.fraction
        s.challengeDone = c.done
        return s
    }

    /** Writes the snapshot and reloads the widget when it changed. */
    static func publish(_ d: AppData) {
        let s = snapshot(d)
        guard s != last else { return }
        last = s
        if s.save() { WidgetCenter.shared.reloadAllTimelines() }
    }
}
