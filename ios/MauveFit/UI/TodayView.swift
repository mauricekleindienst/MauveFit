import SwiftUI

/** The home tab: the running or next workout, this week and today's food at a glance. */
struct TodayView: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router
    @State private var showSettings = false

    var body: some View {
        let d = store.data
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    TabHeader(title: L("Heute", "Today"), subtitle: Date().formatted(Date.FormatStyle().weekday(.wide).day().month(.wide).locale(I18n.locale))) {
                        GlassIconButton(icon: "gearshape", label: L("Einstellungen", "Settings")) { showSettings = true }
                            .accessibilityIdentifier("settings")
                    }
                    if let w = d.active { activeCard(w) } else if let r = Stats.nextRoutine(d) { nextCard(r) }
                    weekTiles(d)
                    weekCard(d)
                    foodCard(d)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .tabRoot()
            .sheet(isPresented: $showSettings) { SettingsView() }
        }
    }

    private func activeCard(_ w: Workout) -> some View {
        Card(padding: 24, radius: Radius.hero) {
            Label(L("Training läuft", "Workout running"), systemImage: "timer").font(.fit(.titleSmall)).foregroundStyle(Ink.accent)
            Text(w.name).font(.fit(.headline)).foregroundStyle(Ink.text)
            let total = w.entries.reduce(0) { $0 + $1.sets.filter { !$0.warmup }.count }
            Text(L("\(w.doneSets.count) von \(Fmt.sets(total)) erledigt", "\(w.doneSets.count) of \(Fmt.sets(total)) done"))
                .font(.fit(.body)).foregroundStyle(Ink.muted)
            Meter(fraction: total > 0 ? Double(w.doneSets.count) / Double(total) : 0)
            Button(L("Fortsetzen", "Resume")) { router.showWorkout = true }.buttonStyle(.primary).padding(.top, 6)
        }
    }

    private func nextCard(_ r: Routine) -> some View {
        Card(padding: 24, radius: Radius.hero) {
            Text(L("Als Nächstes", "Up next")).font(.fit(.titleSmall)).foregroundStyle(Ink.accent)
            Text(r.name).font(.fit(.headline)).foregroundStyle(Ink.text)
            Text([Fmt.exercises(r.exercises.count), Fmt.sets(r.exercises.reduce(0) { $0 + $1.sets }), r.note].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
            HStack(spacing: 8) {
                ForEach(r.exercises.prefix(5)) { p in
                    ExerciseThumb(exercise: ExerciseRepo.shared[p.exerciseId], size: 48)
                }
                if r.exercises.count > 5 {
                    Text("+\(r.exercises.count - 5)").font(.fit(.label)).foregroundStyle(Ink.muted)
                        .frame(width: 48, height: 48).background(Ink.raised, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            .padding(.vertical, 4)
            Button {
                store.startWorkout(r)
                router.showWorkout = true
            } label: { Label(L("Training starten", "Start workout"), systemImage: "play.fill") }
                .buttonStyle(.primary)
            Button(L("Anderen Plan wählen", "Pick another plan")) { router.tab = .training }.buttonStyle(.ghost)
        }
    }

    private func weekTiles(_ d: AppData) -> some View {
        let goal = d.settings.weeklyGoal
        let count = Stats.workoutsThisWeek(d.history)
        let streak = Stats.weekStreak(d.history, goal: goal)
        return HStack(spacing: 10) {
            StatTile(value: "\(count)/\(goal)", label: L("diese Woche", "this week"), color: count >= goal ? Ink.accent : Ink.text)
            StatTile(value: "\(streak)", label: Fmt.weeks(streak))
            StatTile(value: Fmt.volume(Stats.weeklyVolume(d.history, weeks: 1).first?.1 ?? 0), label: L("Volumen", "Volume"))
        }
    }

    private func weekCard(_ d: AppData) -> some View {
        let goal = d.settings.weeklyGoal
        let count = Stats.workoutsThisWeek(d.history)
        let trained = Stats.weekDays(d.history)
        let start = Stats.weekStart(Date())
        let today = Stats.calendar.startOfDay(for: Date())
        return Card {
            HStack {
                SectionTitle(text: L("Deine Woche", "Your week")).padding(.top, -10)
                Text(count >= goal ? L("Ziel erreicht", "Goal reached") : L("noch \(goal - count)", "\(goal - count) to go"))
                    .font(.fit(.labelSmall)).foregroundStyle(count >= goal ? Ink.accent : Ink.muted)
            }
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    let day = Stats.calendar.date(byAdding: .day, value: i, to: start)!
                    let done = trained.contains(day)
                    VStack(spacing: 6) {
                        Text(day.formatted(Date.FormatStyle().weekday(.abbreviated).locale(I18n.locale)).prefix(2))
                            .font(.fit(.labelSmall)).foregroundStyle(day == today ? Ink.text : Ink.faint)
                        Circle()
                            .fill(done ? Ink.accent : Ink.raised)
                            .overlay(Circle().stroke(day == today && !done ? Ink.accent : Color.clear, lineWidth: 2))
                            .overlay { if done { Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(Ink.bg) } }
                            .frame(width: 32, height: 32)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            Meter(fraction: Double(count) / Double(max(1, goal)))
        }
    }

    private func foodCard(_ d: AppData) -> some View {
        let day = Stats.isoDay()
        let entries = d.food.filter { $0.date == day }
        let t = Nutrition.targets(kg: Stats.latestWeight(d) ?? Nutrition.defaultKg, goal: d.profile.goal)
        let kcal = entries.reduce(0) { $0 + $1.kcal }
        let protein = entries.reduce(0) { $0 + $1.protein }
        let water = d.water[day] ?? 0
        return Button { router.tab = .food } label: {
            Card {
                HStack {
                    SectionTitle(text: L("Essen heute", "Food today")).padding(.top, -10)
                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Ink.faint)
                }
                HStack(alignment: .top) {
                    StatTile(value: "\(kcal)", label: L("von \(t.kcal) kcal", "of \(t.kcal) kcal"), boxed: false)
                    StatTile(value: "\(protein) g", label: L("von \(t.protein) g Protein", "of \(t.protein) g protein"), boxed: false)
                    StatTile(value: Fmt.num(Double(water) / 1000) + " l", label: L("Wasser", "Water"), color: Ink.mauve, boxed: false)
                }
                Meter(fraction: Double(kcal) / Double(max(1, t.kcal)))
            }
        }
        .buttonStyle(.plain)
    }
}
