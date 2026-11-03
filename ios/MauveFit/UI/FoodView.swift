import SwiftUI

/** The food tab: the day's diary against the daily targets, and water. */
struct FoodView: View {
    @Environment(Store.self) private var store
    @State private var adding = false
    @State private var dayOffset = 0

    var body: some View {
        let d = store.data
        let date = Stats.calendar.date(byAdding: .day, value: dayOffset, to: Date())!
        let day = Stats.isoDay(date)
        let entries = d.food.filter { $0.date == day }
        let kg = Stats.latestWeight(d)
        let t = Nutrition.targets(kg: kg ?? Nutrition.defaultKg, goal: d.profile.goal)
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    TabHeader(title: L("Ernährung", "Nutrition"), subtitle: d.profile.goal.label) {
                        GlassIconButton(icon: "plus", label: L("Essen eintragen", "Log food")) { adding = true }
                    }
                    dayPicker(date)
                    macros(entries, t)
                    water(d.water[day] ?? 0, goal: t.waterMl, day: day)
                    SectionTitle(text: L("Tagebuch", "Diary"))
                    if entries.isEmpty {
                        Card { Text(L("Noch nichts eingetragen. Oben rechts kommt etwas dazu.", "Nothing logged yet. Add something at the top right.")).font(.fit(.body)).foregroundStyle(Ink.muted) }
                    }
                    ForEach(entries) { e in
                        Card(padding: 16) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(e.name).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                                    Text(L("\(e.protein) g Protein · \(e.carbs) g KH · \(e.fat) g Fett", "\(e.protein) g protein · \(e.carbs) g carbs · \(e.fat) g fat"))
                                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                                }
                                Spacer()
                                Text("\(e.kcal) kcal").font(.fit(.number)).foregroundStyle(Ink.text)
                            }
                        }
                        .contextMenu {
                            Button(role: .destructive) { store.deleteFood(e.id) } label: { Label(L("Löschen", "Delete"), systemImage: "trash") }
                        }
                    }
                    Text(kg.map { L("Ziele aus deinem Gewicht (\(Fmt.kg($0)) kg) und Ziel (\(d.profile.goal.label)).", "Targets from your weight (\(Fmt.kg($0)) kg) and goal (\(d.profile.goal.label)).") }
                         ?? L("Ziele für \(Fmt.kg(Nutrition.defaultKg)) kg, bis du dein Gewicht unter Fortschritt einträgst.", "Targets for \(Fmt.kg(Nutrition.defaultKg)) kg until you log your weight under Progress."))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.faint).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .tabRoot()
            .sheet(isPresented: $adding) { FoodEditor(day: day) }
        }
    }

    private func dayPicker(_ date: Date) -> some View {
        HStack {
            RoundButton(icon: "chevron.left", label: L("Tag davor", "Previous day")) { dayOffset -= 1 }
            Spacer()
            Text(dayOffset == 0 ? L("Heute", "Today") : dayOffset == -1 ? L("Gestern", "Yesterday")
                 : date.formatted(Date.FormatStyle().weekday(.abbreviated).day().month(.abbreviated).locale(I18n.locale)))
                .font(.fit(.titleLarge)).foregroundStyle(Ink.text)
            Spacer()
            RoundButton(icon: "chevron.right", label: L("Tag danach", "Next day")) { dayOffset += 1 }
                .disabled(dayOffset >= 0).opacity(dayOffset >= 0 ? 0.35 : 1)
        }
    }

    private func macros(_ entries: [FoodEntry], _ t: Nutrition.Targets) -> some View {
        let kcal = entries.reduce(0) { $0 + $1.kcal }
        let left = t.kcal - kcal
        return Card {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(kcal)").font(.fit(.display)).tracking(-1.2).foregroundStyle(Ink.text).lineLimit(1).minimumScaleFactor(0.6)
                Text(L("von \(t.kcal) kcal", "of \(t.kcal) kcal")).font(.fit(.body)).foregroundStyle(Ink.muted)
                Spacer()
                Text(left >= 0 ? L("noch \(left)", "\(left) left") : L("\(-left) drüber", "\(-left) over"))
                    .font(.fit(.titleSmall)).foregroundStyle(left >= 0 ? Ink.accent : Ink.coral)
            }
            Meter(fraction: Double(kcal) / Double(max(1, t.kcal)), color: left >= 0 ? Ink.accent : Ink.coral)
            HStack(spacing: 12) {
                macro(L("Protein", "Protein"), entries.reduce(0) { $0 + $1.protein }, t.protein, Ink.accent)
                macro(L("Kohlenhydrate", "Carbs"), entries.reduce(0) { $0 + $1.carbs }, t.carbs, Ink.mauve)
                macro(L("Fett", "Fat"), entries.reduce(0) { $0 + $1.fat }, t.fat, Ink.coral)
            }
            .padding(.top, 4)
        }
    }

    private func macro(_ name: String, _ value: Int, _ target: Int, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(name).font(.fit(.labelSmall)).foregroundStyle(Ink.muted).lineLimit(1)
            Text("\(value)/\(target) g").font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1).minimumScaleFactor(0.7)
            Meter(fraction: Double(value) / Double(max(1, target)), color: color)
        }
        .frame(maxWidth: .infinity)
    }

    private func water(_ ml: Int, goal: Int, day: String) -> some View {
        Card {
            HStack {
                Image(systemName: "drop.fill").foregroundStyle(Ink.mauve)
                Text(L("Wasser", "Water")).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                Spacer()
                Text("\(Fmt.num(Double(ml) / 1000, digits: 2)) / \(Fmt.num(Double(goal) / 1000, digits: 2)) l")
                    .font(.fit(.number)).foregroundStyle(Ink.text)
            }
            Meter(fraction: Double(ml) / Double(max(1, goal)), color: Ink.mauve)
            HStack(spacing: 8) {
                Button("−250 ml") { store.addWater(-250, day: day) }.buttonStyle(.pillOutline).disabled(ml <= 0)
                Button("+250 ml") { store.addWater(250, day: day) }.buttonStyle(PillButtonStyle(color: Ink.mauve))
                Button("+500 ml") { store.addWater(500, day: day) }.buttonStyle(PillButtonStyle(color: Ink.mauve)).disabled(ml >= 10_000)
            }
        }
    }
}

/** Logs one food by hand: name, energy and macros. */
private struct FoodEditor: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    let day: String
    @State private var name = ""
    @State private var kcal = ""
    @State private var protein = ""
    @State private var carbs = ""
    @State private var fat = ""

    /** A whole number from 0, "12,5" rounded; nil for anything else. Empty counts as 0 unless [required]. */
    static func amount(_ text: String, required: Bool = false) -> Int? {
        let t = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        if t.isEmpty { return required ? nil : 0 }
        guard let v = Double(t), v >= 0, v < 100_000 else { return nil }
        return Int(v.rounded())
    }

    var body: some View {
        let valid = !name.trimmingCharacters(in: .whitespaces).isEmpty && FoodEditor.amount(kcal, required: true) != nil
            && [protein, carbs, fat].allSatisfy { FoodEditor.amount($0) != nil }
        NavigationStack {
            Form {
                Section {
                    TextField(L("Was hast du gegessen?", "What did you eat?"), text: $name).font(.fit(.titleMedium))
                    field("kcal", $kcal)
                    field(L("Protein (g)", "Protein (g)"), $protein)
                    field(L("Kohlenhydrate (g)", "Carbs (g)"), $carbs)
                    field(L("Fett (g)", "Fat (g)"), $fat)
                } footer: {
                    Text(L("Nur Zahlen ab 0.", "Numbers from 0 only.")).font(.fit(.bodySmall))
                }
                .listRowBackground(Ink.surface)
            }
            .screen()
            .navigationTitle(L("Essen eintragen", "Log food"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Speichern", "Save")) {
                        store.addFood(FoodEntry(
                            date: day, name: name.trimmingCharacters(in: .whitespaces),
                            kcal: FoodEditor.amount(kcal) ?? 0, protein: FoodEditor.amount(protein) ?? 0,
                            carbs: FoodEditor.amount(carbs) ?? 0, fat: FoodEditor.amount(fat) ?? 0
                        ))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(!valid)
                }
            }
        }
    }

    private func field(_ label: String, _ value: Binding<String>) -> some View {
        HStack {
            Text(label).font(.fit(.body)).foregroundStyle(Ink.muted)
            TextField("0", text: value).font(.fit(.number)).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
        }
    }
}
