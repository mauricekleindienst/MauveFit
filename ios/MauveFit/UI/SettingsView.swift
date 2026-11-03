import SwiftUI

/** Look, language, training defaults and the goal behind the food targets. */
struct SettingsView: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var confirmReset = false

    var body: some View {
        let s = store.data.settings
        let p = store.data.profile
        NavigationStack {
            Form {
                Section {
                    Picker(L("Design", "Theme"), selection: Binding(get: { s.theme }, set: { v in store.updateSettings { $0.theme = v } })) {
                        Text(L("System", "System")).tag("system")
                        Text(L("Dunkel", "Dark")).tag("dark")
                        Text(L("Hell", "Light")).tag("light")
                    }
                    .pickerStyle(.segmented)
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L("Akzentfarbe", "Accent")).font(.fit(.body)).foregroundStyle(Ink.text)
                        // The free accents, plus the ones bought in the shop.
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 6), spacing: 12) {
                            ForEach(Accent.allCases.filter { !$0.premium || store.data.owned.contains("accent:" + $0.rawValue) }) { a in
                                let on = Accent.of(s.accent) == a
                                Button { store.updateSettings { $0.accent = a.rawValue } } label: {
                                    VStack(spacing: 6) {
                                        Circle().fill(Color(uiColor: .tone(a.tones.ink, a.tones.paper)))
                                            .frame(width: 34, height: 34)
                                            .overlay { if on { Image(systemName: "checkmark").font(.system(size: 14, weight: .heavy)).foregroundStyle(Ink.bg) } }
                                            .overlay(Circle().stroke(on ? Ink.text : Color.clear, lineWidth: 2).padding(-4))
                                        Text(a.label).font(.fit(.labelSmall)).foregroundStyle(on ? Ink.text : Ink.muted).lineLimit(1).minimumScaleFactor(0.8)
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(a.label)
                                .accessibilityAddTraits(on ? .isSelected : [])
                            }
                        }
                    }
                    .padding(.vertical, 4)
                    Picker(L("Sprache", "Language"), selection: Binding(get: { s.language }, set: { v in store.updateSettings { $0.language = v } })) {
                        Text(L("System", "System")).tag("system")
                        Text("Deutsch").tag("de")
                        Text("English").tag("en")
                    }
                } header: { header(L("Aussehen", "Look")) }
                .listRowBackground(Ink.surface)

                Section {
                    Picker(L("Ziel", "Goal"), selection: Binding(get: { p.goal }, set: { v in store.updateProfile { $0.goal = v } })) {
                        ForEach(Goal.allCases) { g in Text(g.label).tag(g) }
                    }
                    Stepper(L("Wochenziel: \(Fmt.workouts(s.weeklyGoal))", "Weekly goal: \(Fmt.workouts(s.weeklyGoal))"),
                            value: Binding(get: { s.weeklyGoal }, set: { v in store.updateSettings { $0.weeklyGoal = v } }), in: 1...7)
                    Stepper(L("Standardpause: \(Fmt.clock(s.defaultRest))", "Default rest: \(Fmt.clock(s.defaultRest))"),
                            value: Binding(get: { s.defaultRest }, set: { v in store.updateSettings { $0.defaultRest = v } }), in: 0...600, step: 15)
                    Picker(L("Gewichtsschritt", "Weight step"), selection: Binding(get: { s.weightStep }, set: { v in store.updateSettings { $0.weightStep = v } })) {
                        ForEach([0.5, 1, 1.25, 2, 2.5, 5], id: \.self) { v in Text("\(Fmt.kg(v)) kg").tag(v) }
                    }
                    Picker(L("Stange", "Bar"), selection: Binding(get: { s.barWeight }, set: { v in store.updateSettings { $0.barWeight = v } })) {
                        ForEach([10.0, 15, 20], id: \.self) { v in Text("\(Fmt.kg(v)) kg").tag(v) }
                    }
                } header: { header(L("Training", "Training")) }
                .font(.fit(.body))
                .listRowBackground(Ink.surface)

                Section {
                    Toggle(isOn: Binding(get: { s.healthWrite }, set: { v in Task { await store.setHealth(v) } })) {
                        Label(L("Mit Apple Health synchronisieren", "Sync with Apple Health"), systemImage: "heart.fill")
                    }
                    .accessibilityIdentifier("healthToggle")
                } header: { header("Apple Health") } footer: {
                    Text(L("Schreibt fertige Trainings als Krafttraining und dein Körpergewicht in Apple Health und liest dein Gewicht für die Ernährungsziele. Die Daten bleiben auf deinem iPhone.",
                           "Writes finished workouts as strength training and your body weight to Apple Health, and reads your weight for the nutrition targets. The data stays on your iPhone."))
                        .font(.fit(.bodySmall))
                }
                .font(.fit(.body))
                .listRowBackground(Ink.surface)

                Section {
                    // Guideline 1.4.1: the targets and suggestions are rough guidance only.
                    Label {
                        Text(L("Trainingsvorschläge und Ernährungsziele sind allgemeine Richtwerte und keine medizinische Beratung. Sprich bei Beschwerden, Vorerkrankungen oder in der Schwangerschaft vorher mit einer Ärztin oder einem Arzt.",
                               "Training suggestions and nutrition targets are general guidance, not medical advice. If you have pain, a medical condition or are pregnant, talk to a doctor first."))
                            .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                    } icon: { Image(systemName: "heart.text.square").foregroundStyle(Ink.accent) }
                    Link(destination: URL(string: "https://github.com/mauricekleindienst/MauveFit/blob/main/PRIVACY.md")!) {
                        Label(L("Datenschutz", "Privacy"), systemImage: "hand.raised")
                    }
                    Text(L("Alle Daten bleiben auf diesem iPhone. Die App hat kein Konto und geht nicht online.",
                           "All data stays on this iPhone. The app has no account and never goes online."))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                    NavigationLink { LicensesView() } label: { Label(L("Open-Source-Lizenzen", "Open-source licences"), systemImage: "doc.text") }
                        .accessibilityIdentifier("licenses")
                    Link(destination: Drawings.licenseURL) {
                        Text(L("Illustrationen: Everkinetic, CC BY-SA 4.0", "Illustrations: Everkinetic, CC BY-SA 4.0")).font(.fit(.bodySmall))
                    }
                    if let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                        Text("Mauve Fit \(v)").font(.fit(.bodySmall)).foregroundStyle(Ink.faint)
                    }
                } header: { header(L("Über", "About")) }
                .font(.fit(.body))
                .listRowBackground(Ink.surface)

                Section {
                    Button(L("Alle Daten löschen", "Delete all data"), role: .destructive) { confirmReset = true }
                } footer: {
                    Text(L("Löscht Trainings, Pläne, Gewicht, Ernährung und Einstellungen von diesem iPhone.",
                           "Deletes workouts, plans, weight, nutrition and settings from this iPhone."))
                        .font(.fit(.bodySmall))
                }
                .listRowBackground(Ink.surface)
            }
            .alert(L("Alle Daten löschen?", "Delete all data?"), isPresented: $confirmReset) {
                Button(L("Löschen", "Delete"), role: .destructive) {
                    store.reset()
                    dismiss()
                }
                Button(L("Abbrechen", "Cancel"), role: .cancel) {}
            } message: {
                Text(L("Das kann nicht rückgängig gemacht werden.", "This cannot be undone."))
            }
            .screen()
            .accessibilityIdentifier("settingsScreen")
            .navigationTitle(L("Einstellungen", "Settings"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("Fertig", "Done")) { dismiss() }.fontWeight(.semibold) } }
        }
        // The sheet sits outside the tab view, so it follows the theme setting itself.
        .preferredColorScheme(s.theme == "light" ? .light : s.theme == "dark" ? .dark : nil)
        .tint(Ink.accent)
    }

    private func header(_ text: String) -> some View {
        Text(text).font(.fit(.titleSmall)).foregroundStyle(Ink.muted).textCase(nil)
    }
}
