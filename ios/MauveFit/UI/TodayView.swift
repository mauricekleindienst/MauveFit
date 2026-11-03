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
                VStack(spacing: 14) {
                    if let w = d.active { activeCard(w) } else if let r = Stats.nextRoutine(d) { nextCard(r) }
                    weekCard(d)
                    foodCard(d)
                }
                .padding(16)
            }
            .screen()
            .navigationTitle(L("Heute", "Today"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel(L("Einstellungen", "Settings"))
                }
            }
            .sheet(isPresented: $showSettings) { SettingsView() }
        }
    }

    private func activeCard(_ w: Workout) -> some View {
        Card {
            Label(L("Training läuft", "Workout running"), systemImage: "timer").font(.caption.weight(.semibold)).foregroundStyle(Ink.accent)
            Text(w.name).font(.system(.title2, design: .rounded).weight(.bold)).foregroundStyle(Ink.text)
            let total = w.entries.reduce(0) { $0 + $1.sets.filter { !$0.warmup }.count }
            Text(L("\(w.doneSets.count) von \(total) Sätzen", "\(w.doneSets.count) of \(total) sets"))
                .foregroundStyle(Ink.muted)
            Button(L("Fortsetzen", "Resume")) { router.showWorkout = true }.buttonStyle(.pill)
        }
    }

    private func nextCard(_ r: Routine) -> some View {
        Card {
            Text(L("Als Nächstes", "Up next")).font(.caption.weight(.semibold)).foregroundStyle(Ink.accent)
            Text(r.name).font(.system(.title2, design: .rounded).weight(.bold)).foregroundStyle(Ink.text)
            if !r.note.isEmpty { Text(r.note).foregroundStyle(Ink.muted) }
            HStack(spacing: -10) {
                ForEach(r.exercises.prefix(5)) { p in
                    ExerciseThumb(exercise: ExerciseRepo.shared[p.exerciseId], size: 44)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Ink.surface, lineWidth: 2))
                }
                if r.exercises.count > 5 {
                    Text("+\(r.exercises.count - 5)").font(.caption.weight(.semibold)).foregroundStyle(Ink.muted).padding(.leading, 16)
                }
            }
            HStack {
                Button(L("Training starten", "Start workout")) {
                    store.startWorkout(r)
                    router.showWorkout = true
                }
                .buttonStyle(.pill)
                Button(L("Andere Pläne", "Other plans")) { router.tab = .training }.buttonStyle(.pillOutline)
            }
        }
    }

    private func weekCard(_ d: AppData) -> some View {
        let goal = d.settings.weeklyGoal
        let count = Stats.workoutsThisWeek(d.history)
        let streak = Stats.weekStreak(d.history, goal: goal)
        let trained = Stats.weekDays(d.history)
        let start = Stats.weekStart(Date())
        let today = Stats.calendar.startOfDay(for: Date())
        return Card {
            HStack {
                StatTile(value: "\(count)/\(goal)", label: L("Diese Woche", "This week"), color: count >= goal ? Ink.accent : Ink.text)
                StatTile(value: "\(streak)", label: L(streak == 1 ? "Woche in Folge" : "Wochen in Folge", streak == 1 ? "week streak" : "weeks streak"))
            }
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    let day = Stats.calendar.date(byAdding: .day, value: i, to: start)!
                    let done = trained.contains(day)
                    VStack(spacing: 4) {
                        Text(day.formatted(Date.FormatStyle().weekday(.narrow).locale(I18n.locale)))
                            .font(.caption2).foregroundStyle(day == today ? Ink.text : Ink.faint)
                        Circle()
                            .fill(done ? Ink.accent : Ink.raised)
                            .overlay(Circle().stroke(day == today ? Ink.accent : Color.clear, lineWidth: 1.5))
                            .overlay { if done { Image(systemName: "checkmark").font(.caption2.weight(.bold)).foregroundStyle(Ink.bg) } }
                            .frame(width: 28, height: 28)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            Meter(fraction: goal > 0 ? Double(count) / Double(goal) : 0)
        }
    }

    private func foodCard(_ d: AppData) -> some View {
        let day = Stats.isoDay()
        let entries = d.food.filter { $0.date == day }
        let t = Nutrition.targets(kg: Stats.latestWeight(d) ?? Nutrition.defaultKg, goal: d.profile.goal)
        let kcal = entries.reduce(0) { $0 + $1.kcal }
        let protein = entries.reduce(0) { $0 + $1.protein }
        let water = d.water[day] ?? 0
        return Card {
            HStack {
                Text(L("Essen heute", "Food today")).font(.headline).foregroundStyle(Ink.text)
                Spacer()
                Button { router.tab = .food } label: { Image(systemName: "chevron.right") }.foregroundStyle(Ink.muted)
            }
            HStack {
                StatTile(value: "\(kcal)", label: L("von \(t.kcal) kcal", "of \(t.kcal) kcal"))
                StatTile(value: "\(protein) g", label: L("von \(t.protein) g Eiweiß", "of \(t.protein) g protein"))
                StatTile(value: Fmt.num(Double(water) / 1000) + " l", label: L("Wasser", "Water"), color: Ink.mauve)
            }
            Meter(fraction: t.kcal > 0 ? Double(kcal) / Double(t.kcal) : 0)
        }
    }
}
