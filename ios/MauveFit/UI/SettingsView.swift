import SwiftUI

/** Language, weekly goal, rest and weight step, and the training goal behind the food targets. */
struct SettingsView: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let s = store.data.settings
        let p = store.data.profile
        NavigationStack {
            Form {
                Section(L("Allgemein", "General")) {
                    Picker(L("Sprache", "Language"), selection: Binding(get: { s.language }, set: { v in store.updateSettings { $0.language = v } })) {
                        Text(L("System", "System")).tag("system")
                        Text("Deutsch").tag("de")
                        Text("English").tag("en")
                    }
                    Picker(L("Ziel", "Goal"), selection: Binding(get: { p.goal }, set: { v in store.updateProfile { $0.goal = v } })) {
                        ForEach(Goal.allCases) { g in Text(g.label).tag(g) }
                    }
                }
                .listRowBackground(Ink.surface)
                Section(L("Training", "Training")) {
                    Stepper(L("Wochenziel: \(s.weeklyGoal) Trainings", "Weekly goal: \(s.weeklyGoal) workouts"),
                            value: Binding(get: { s.weeklyGoal }, set: { v in store.updateSettings { $0.weeklyGoal = v } }), in: 1...7)
                    Stepper(L("Standardpause: \(Fmt.clock(s.defaultRest))", "Default rest: \(Fmt.clock(s.defaultRest))"),
                            value: Binding(get: { s.defaultRest }, set: { v in store.updateSettings { $0.defaultRest = v } }), in: 0...600, step: 15)
                    Picker(L("Gewichtsschritt", "Weight step"), selection: Binding(get: { s.weightStep }, set: { v in store.updateSettings { $0.weightStep = v } })) {
                        ForEach([0.5, 1, 1.25, 2, 2.5, 5], id: \.self) { v in Text("\(Fmt.num(v, digits: 2)) kg").tag(v) }
                    }
                }
                .listRowBackground(Ink.surface)
                Section {
                    Text(Exercise.attribution).font(.footnote).foregroundStyle(Ink.muted)
                    Text(L("Übungsdaten: exercises-dataset von hasaneyldrm", "Exercise data: exercises-dataset by hasaneyldrm"))
                        .font(.footnote).foregroundStyle(Ink.muted)
                    if let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                        Text("Mauve Fit \(v)").font(.footnote).foregroundStyle(Ink.faint)
                    }
                }
                .listRowBackground(Ink.surface)
            }
            .screen()
            .navigationTitle(L("Einstellungen", "Settings"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("Fertig", "Done")) { dismiss() } } }
        }
    }
}
