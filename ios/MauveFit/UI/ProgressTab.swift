import Charts
import SwiftUI

/** The progress tab: totals, weekly volume, body weight, records and the workout history. */
struct ProgressTab: View {
    @Environment(Store.self) private var store
    @State private var weighing = false

    var body: some View {
        let d = store.data
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    TabHeader(title: L("Fortschritt", "Progress"), subtitle: Fmt.workouts(d.history.count)) { EmptyView() }
                    let streak = Stats.weekStreak(d.history, goal: d.settings.weeklyGoal, frozen: d.frozenWeeks)
                    HStack(spacing: 10) {
                        StatTile(value: "\(Stats.workoutsThisWeek(d.history))", label: L("diese Woche", "this week"))
                        StatTile(value: "\(streak)", label: Fmt.weeks(streak))
                        StatTile(value: Fmt.volume(d.history.reduce(0) { $0 + $1.volume }), label: L("insgesamt bewegt", "lifted in total"), color: Ink.accent)
                    }
                    CardGrid {
                        rankCard(d)
                        volumeCard(d)
                        weightCard(d)
                        recordsCard(d)
                        CardioCard()
                    }
                    SectionTitle(text: L("Verlauf", "History"))
                    if d.history.isEmpty && d.cardio.isEmpty {
                        Card { Text(L("Noch keine Trainings. Starte eins im Tab Training.", "No workouts yet. Start one in the Training tab.")).font(.fit(.body)).foregroundStyle(Ink.muted) }
                    }
                    // Strength workouts and cardio in one timeline, newest first.
                    CardGrid {
                        ForEach(timeline(d), id: \.id) { item in
                            switch item {
                            case .workout(let w):
                                NavigationLink { WorkoutDetailView(workoutId: w.id) } label: { historyRow(w) }
                                    .buttonStyle(.plain)
                            case .cardio(let c):
                                CardioRow(session: c)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .tabRoot()
            .sheet(isPresented: $weighing) { WeightEditor(start: Stats.latestWeight(d) ?? Nutrition.defaultKg) }
        }
    }

    /** Level, coins and the streak freezes: what they are and how far the next one is. */
    private func rankCard(_ d: AppData) -> some View {
        let toNext = Motivation.freezeEvery - d.streakRun % Motivation.freezeEvery
        return Card {
            LevelRow(rank: Quests.level(Quests.xp(d)), title: d.title)
            HStack {
                CoinBadge(coins: Shop.balance(d))
                Spacer()
                NavigationLink { ShopView() } label: { Text(L("Zum Shop", "Open the shop")).font(.fit(.label)).foregroundStyle(Ink.accent) }
                    .accessibilityIdentifier("progressShop")
            }
            HStack {
                SectionTitle(text: L("Joker", "Streak freezes"))
                Tag(text: Motivation.freezeLabel(d.freezes), color: d.freezes > 0 ? Ink.accent : Ink.muted).padding(.top, 10)
            }
            Text(L("Alle \(Motivation.freezeEvery) Wochen in Folge mit erreichtem Wochenziel gibt es einen Joker, höchstens \(Motivation.freezeMax) auf Vorrat. Verpasst du eine Woche, springt automatisch ein Joker ein und die Serie bleibt.",
                   "Every \(Motivation.freezeEvery) weeks in a row with your weekly goal met earn a freeze, up to \(Motivation.freezeMax) in stock. Miss a week and a freeze steps in by itself, so the streak lives on."))
                .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
            if d.freezes >= Motivation.freezeMax {
                Text(L("Vorrat voll.", "Stock is full.")).font(.fit(.labelSmall)).foregroundStyle(Ink.accent)
            } else {
                Meter(fraction: Double(Motivation.freezeEvery - toNext) / Double(Motivation.freezeEvery))
                Text(toNext == 1 ? L("Noch 1 Woche bis zum nächsten Joker", "1 more week to the next freeze") : L("Noch \(toNext) Wochen bis zum nächsten Joker", "\(toNext) more weeks to the next freeze"))
                    .font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
            }
        }
    }

    private func volumeCard(_ d: AppData) -> some View {
        let weeks = Stats.weeklyVolume(d.history)
        let current = weeks.last?.0
        return Card {
            HStack {
                SectionTitle(text: L("Volumen pro Woche", "Volume per week")).padding(.top, -10)
                Text(Fmt.volume(weeks.last?.1 ?? 0)).font(.fit(.titleMedium)).foregroundStyle(Ink.accent)
            }
            Chart {
                ForEach(weeks, id: \.0) { week, kg in
                    BarMark(x: .value(L("Woche", "Week"), week, unit: .weekOfYear), y: .value("kg", kg))
                        // The current week in the accent, the ones before it quiet.
                        .foregroundStyle(week == current ? Ink.accent : Ink.raised)
                        .cornerRadius(6)
                }
            }
            .chartXAxis {
                AxisMarks(values: weeks.map(\.0)) { v in
                    AxisValueLabel {
                        if let date = v.as(Date.self) {
                            Text(L("KW", "W") + "\(Stats.calendar.component(.weekOfYear, from: date))").font(.fit(.labelSmall)).foregroundStyle(Ink.faint)
                        }
                    }
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 150)
            .overlay {
                if weeks.allSatisfy({ $0.1 == 0 }) {
                    Text(L("Nach dem ersten Training siehst du hier dein Volumen pro Woche.", "Your volume per week shows here after your first workout."))
                        .font(.fit(.bodyMedium)).foregroundStyle(Ink.muted).multilineTextAlignment(.center).padding(.horizontal, 12)
                }
            }
        }
    }

    private func weightCard(_ d: AppData) -> some View {
        let points = d.bodyWeights.sorted { $0.at < $1.at }.suffix(30)
        return Card {
            HStack {
                SectionTitle(text: L("Körpergewicht", "Body weight")).padding(.top, -10)
                if let kg = Stats.latestWeight(d) {
                    Text("\(Fmt.kg(kg)) kg").font(.fit(.titleMedium)).foregroundStyle(Ink.accent)
                }
            }
            if points.count >= 2 {
                let first = points.first!.kg, last = points.last!.kg
                Text(abs(last - first) < 0.05 ? L("gleich geblieben", "unchanged")
                     : L("\(last > first ? "+" : "−")\(Fmt.kg(abs(last - first))) kg seit \(Fmt.date(points.first!.at))",
                         "\(last > first ? "+" : "−")\(Fmt.kg(abs(last - first))) kg since \(Fmt.date(points.first!.at))"))
                    .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                Chart(Array(points), id: \.at) { p in
                    LineMark(x: .value(L("Tag", "Day"), Date(ms: p.at)), y: .value("kg", p.kg))
                        .foregroundStyle(Ink.accent)
                        .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
                        .interpolationMethod(.monotone)
                    PointMark(x: .value(L("Tag", "Day"), Date(ms: p.at)), y: .value("kg", p.kg))
                        .foregroundStyle(Ink.accent)
                        .symbolSize(24)
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { v in
                        AxisGridLine().foregroundStyle(Ink.line)
                        AxisValueLabel { if let kg = v.as(Double.self) { Text(Fmt.kg(kg)).font(.fit(.labelSmall)).foregroundStyle(Ink.faint) } }
                    }
                }
                .chartXAxis(.hidden)
                .frame(height: 130)
            } else {
                Text(L("Trage dein Gewicht ein, um den Verlauf zu sehen.", "Log your weight to see the trend.")).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
            }
            Button { weighing = true } label: { Label(L("Gewicht eintragen", "Log weight"), systemImage: "scalemass.fill") }.buttonStyle(.ghost)
        }
    }

    private func recordsCard(_ d: AppData) -> some View {
        let records = Stats.records(d.history).prefix(5)
        return Group {
            if !records.isEmpty {
                Card {
                    SectionTitle(text: L("Bestwerte", "Records")).padding(.top, -10)
                    ForEach(Array(records), id: \.exerciseId) { r in
                        let ex = ExerciseRepo.shared[r.exerciseId]
                        HStack(spacing: 12) {
                            ExerciseThumb(exercise: ex, size: 40)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(ex?.title ?? r.exerciseId).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1)
                                Text(r.best.weight > 0 ? L("1RM ca. \(Fmt.kg(r.e1rm.rounded())) kg · \(Fmt.date(r.at))", "1RM about \(Fmt.kg(r.e1rm.rounded())) kg · \(Fmt.date(r.at))")
                                     : Fmt.date(r.at))
                                    .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                            }
                            Spacer()
                            Text(Fmt.set(r.best, bodyweight: ex?.bodyweight == true)).font(.fit(.number)).foregroundStyle(Ink.accent)
                        }
                    }
                }
            }
        }
    }

    private enum Entry {
        case workout(Workout), cardio(CardioSession)
        var id: String { switch self { case .workout(let w): "w" + w.id; case .cardio(let c): "c" + c.id } }
        var at: Int64 { switch self { case .workout(let w): w.startedAt; case .cardio(let c): c.at } }
    }

    private func timeline(_ d: AppData) -> [Entry] {
        (d.history.map(Entry.workout) + d.cardio.map(Entry.cardio)).sorted { $0.at > $1.at }
    }

    private func historyRow(_ w: Workout) -> some View {
        Card(padding: 16) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(w.name).font(.fit(.titleLarge)).foregroundStyle(Ink.text).lineLimit(1)
                    Text("\(Fmt.weekday(w.startedAt)), \(Fmt.date(w.startedAt))").font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                    Text("\(Fmt.duration(w.durationMs)) · \(Fmt.sets(w.doneSets.count))").font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                }
                Spacer()
                Text(Stats.headline(w)).font(.fit(.headlineSmall)).foregroundStyle(Ink.text)
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Ink.faint)
            }
        }
    }
}

/** One finished workout: every exercise with its sets, the feel and a note. */
struct WorkoutDetailView: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    let workoutId: String
    @State private var confirmDelete = false

    var body: some View {
        if let w = store.data.history.first(where: { $0.id == workoutId }) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("\(Fmt.weekday(w.startedAt)), \(Fmt.date(w.startedAt, style: .long))").font(.fit(.body)).foregroundStyle(Ink.muted)
                        .padding(.top, 8)
                    HStack(spacing: 10) {
                        StatTile(value: Fmt.duration(w.durationMs), label: L("Dauer", "Duration"))
                        StatTile(value: "\(w.doneSets.count)", label: w.doneSets.count == 1 ? L("Satz", "Set") : L("Sätze", "Sets"))
                        StatTile(value: Stats.headline(w), label: w.volume > 0 ? L("Volumen", "Volume") : L("Gesamt", "Total"), color: Ink.accent)
                    }
                    ForEach(w.entries) { e in
                        let ex = ExerciseRepo.shared[e.exerciseId]
                        Card(padding: 16) {
                            HStack(spacing: 12) {
                                ExerciseThumb(exercise: ex, size: 44)
                                Text(ex?.title ?? e.exerciseId).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                            }
                            ForEach(Array(e.sets.enumerated()), id: \.offset) { i, s in
                                HStack {
                                    Text(s.warmup ? "W" : "\(e.sets.prefix(i + 1).filter { !$0.warmup }.count)")
                                        .font(.fit(.number)).foregroundStyle(s.warmup ? Ink.coral : Ink.faint).frame(width: 28, alignment: .leading)
                                    Text(s.weight > 0 ? "\(Fmt.kg(s.weight)) kg × \(s.reps)" : L("\(s.reps) Wdh.", "\(s.reps) reps"))
                                        .font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                                    Spacer()
                                }
                            }
                        }
                    }
                    FeelCard(workout: w)
                    Button(L("Training löschen", "Delete workout"), role: .destructive) { confirmDelete = true }
                        .buttonStyle(PillButtonStyle(filled: false, color: Ink.red, wide: true))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .screen()
            .navigationTitle(w.name)
            .navigationBarTitleDisplayMode(.inline)
            .alert(L("Training löschen?", "Delete workout?"), isPresented: $confirmDelete) {
                Button(L("Löschen", "Delete"), role: .destructive) {
                    dismiss()
                    store.deleteWorkout(w.id)
                }
                Button(L("Abbrechen", "Cancel"), role: .cancel) {}
            }
        } else {
            Color.clear.screen()
        }
    }
}

/** Logs today's body weight with a wheel in 0.1 kg steps. */
private struct WeightEditor: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var tenths: Int

    init(start: Double) { _tenths = State(initialValue: Int((start * 10).rounded())) }

    var body: some View {
        NavigationStack {
            Picker("kg", selection: $tenths) {
                ForEach(300...2500, id: \.self) { t in Text("\(Fmt.num(Double(t) / 10)) kg").tag(t) }
            }
            .pickerStyle(.wheel)
            .screen()
            .navigationTitle(L("Gewicht", "Weight"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Speichern", "Save")) {
                        store.logWeight(Double(tenths) / 10)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
