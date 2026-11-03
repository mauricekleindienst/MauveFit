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
                    Card {
                        HStack {
                            StatTile(value: "\(d.history.count)", label: L("Trainings", "Workouts"))
                            StatTile(value: "\(Stats.weekStreak(d.history, goal: d.settings.weeklyGoal))", label: L("Wochen in Folge", "Week streak"))
                            StatTile(value: Fmt.num(d.history.reduce(0) { $0 + $1.volume } / 1000) + " t", label: L("Bewegt", "Lifted"))
                        }
                    }
                    volumeCard(d)
                    weightCard(d)
                    recordsCard(d)
                    SectionTitle(text: L("Verlauf", "History"))
                    if d.history.isEmpty {
                        Card { Text(L("Noch keine Trainings. Starte eins im Tab Training.", "No workouts yet. Start one in the Training tab.")).foregroundStyle(Ink.muted) }
                    }
                    ForEach(d.history) { w in
                        NavigationLink { WorkoutDetailView(workoutId: w.id) } label: { historyRow(w) }
                            .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .screen()
            .navigationTitle(L("Fortschritt", "Progress"))
            .sheet(isPresented: $weighing) { WeightEditor(start: Stats.latestWeight(d) ?? Nutrition.defaultKg) }
        }
    }

    private func volumeCard(_ d: AppData) -> some View {
        let weeks = Stats.weeklyVolume(d.history)
        return Card {
            Text(L("Volumen pro Woche", "Volume per week")).font(.headline).foregroundStyle(Ink.text)
            Chart {
                ForEach(weeks, id: \.0) { week, kg in
                    BarMark(x: .value(L("Woche", "Week"), week, unit: .weekOfYear), y: .value("kg", kg))
                        .foregroundStyle(Ink.accent)
                        .cornerRadius(4)
                }
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: .weekOfYear, count: 2)) { _ in
                    AxisValueLabel(format: .dateTime.day().month(.defaultDigits)).foregroundStyle(Ink.faint)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine().foregroundStyle(Ink.line)
                    AxisValueLabel().foregroundStyle(Ink.faint)
                }
            }
            .frame(height: 160)
        }
    }

    private func weightCard(_ d: AppData) -> some View {
        let points = d.bodyWeights.sorted { $0.at < $1.at }.suffix(30)
        return Card {
            HStack {
                Text(L("Körpergewicht", "Body weight")).font(.headline).foregroundStyle(Ink.text)
                Spacer()
                if let kg = Stats.latestWeight(d) {
                    Text("\(Fmt.num(kg)) kg").font(.system(.headline, design: .rounded)).foregroundStyle(Ink.accent)
                }
            }
            if points.count >= 2 {
                Chart(Array(points), id: \.at) { p in
                    LineMark(x: .value(L("Tag", "Day"), Date(ms: p.at)), y: .value("kg", p.kg))
                        .foregroundStyle(Ink.accent)
                        .interpolationMethod(.monotone)
                    PointMark(x: .value(L("Tag", "Day"), Date(ms: p.at)), y: .value("kg", p.kg))
                        .foregroundStyle(Ink.accent)
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Ink.line); AxisValueLabel().foregroundStyle(Ink.faint) } }
                .chartXAxis { AxisMarks { _ in AxisValueLabel().foregroundStyle(Ink.faint) } }
                .frame(height: 140)
            } else {
                Text(L("Trage dein Gewicht ein, um den Verlauf zu sehen.", "Log your weight to see the trend.")).font(.subheadline).foregroundStyle(Ink.muted)
            }
            Button(L("Gewicht eintragen", "Log weight")) { weighing = true }.buttonStyle(.pillOutline)
        }
    }

    private func recordsCard(_ d: AppData) -> some View {
        let records = Stats.records(d.history).prefix(5)
        return Group {
            if !records.isEmpty {
                Card {
                    Text(L("Bestwerte (geschätztes 1RM)", "Records (estimated 1RM)")).font(.headline).foregroundStyle(Ink.text)
                    ForEach(Array(records), id: \.0) { id, kg in
                        HStack {
                            Text(ExerciseRepo.shared[id]?.title ?? id).foregroundStyle(Ink.text).lineLimit(1)
                            Spacer()
                            Text("\(Fmt.num(kg)) kg").font(.system(.subheadline, design: .rounded).weight(.semibold)).foregroundStyle(Ink.accent)
                        }
                    }
                }
            }
        }
    }

    private func historyRow(_ w: Workout) -> some View {
        Card(padding: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(w.name).font(.headline).foregroundStyle(Ink.text)
                    Text("\(Fmt.weekday(w.startedAt)), \(Fmt.date(w.startedAt))").font(.caption).foregroundStyle(Ink.muted)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text(Fmt.minutes(w.durationMs)).font(.subheadline.weight(.semibold)).foregroundStyle(Ink.text)
                    Text(L("\(w.doneSets.count) Sätze · \(Fmt.num(w.volume, digits: 0)) kg", "\(w.doneSets.count) sets · \(Fmt.num(w.volume, digits: 0)) kg"))
                        .font(.caption).foregroundStyle(Ink.muted)
                }
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(Ink.faint)
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
                VStack(spacing: 12) {
                    Card {
                        Text("\(Fmt.weekday(w.startedAt)), \(Fmt.date(w.startedAt, style: .long))").foregroundStyle(Ink.muted)
                        HStack {
                            StatTile(value: Fmt.minutes(w.durationMs), label: L("Dauer", "Duration"))
                            StatTile(value: "\(w.doneSets.count)", label: L("Sätze", "Sets"))
                            StatTile(value: Fmt.num(w.volume, digits: 0) + " kg", label: L("Volumen", "Volume"))
                        }
                        Picker(L("Gefühl", "Feel"), selection: Binding(get: { w.feel }, set: { v in store.updateWorkout(w.id) { $0.feel = v } })) {
                            Text(L("Leicht", "Easy")).tag(1)
                            Text(L("Gut", "Good")).tag(2)
                            Text(L("Hart", "Hard")).tag(3)
                        }
                        .pickerStyle(.segmented)
                        TextField(L("Notiz", "Note"), text: Binding(get: { w.note }, set: { v in store.updateWorkout(w.id) { $0.note = v } }), axis: .vertical)
                            .foregroundStyle(Ink.text)
                    }
                    ForEach(w.entries) { e in
                        Card(padding: 14) {
                            HStack(spacing: 12) {
                                ExerciseThumb(exercise: ExerciseRepo.shared[e.exerciseId], size: 40)
                                Text(ExerciseRepo.shared[e.exerciseId]?.title ?? e.exerciseId).font(.headline).foregroundStyle(Ink.text)
                            }
                            ForEach(Array(e.sets.enumerated()), id: \.offset) { i, s in
                                HStack {
                                    Text(s.warmup ? "W" : "\(e.sets.prefix(i + 1).filter { !$0.warmup }.count)")
                                        .foregroundStyle(s.warmup ? Ink.coral : Ink.faint).frame(width: 28, alignment: .leading)
                                    Text(s.weight > 0 ? "\(Fmt.num(s.weight)) kg × \(s.reps)" : L("\(s.reps) Wdh.", "\(s.reps) reps"))
                                        .foregroundStyle(Ink.text)
                                    Spacer()
                                }
                                .font(.system(.subheadline, design: .rounded))
                            }
                        }
                    }
                    Button(L("Training löschen", "Delete workout"), role: .destructive) { confirmDelete = true }
                        .buttonStyle(PillButtonStyle(filled: false, color: Ink.red))
                }
                .padding(16)
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
