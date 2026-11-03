import SwiftUI

/** The food tab: today's diary against the daily targets, and water. */
struct FoodView: View {
    @Environment(Store.self) private var store
    @State private var adding = false
    @State private var dayOffset = 0

    var body: some View {
        let d = store.data
        let date = Stats.calendar.date(byAdding: .day, value: dayOffset, to: Date())!
        let day = Stats.isoDay(date)
        let entries = d.food.filter { $0.date == day }
        let t = Nutrition.targets(kg: Stats.latestWeight(d) ?? Nutrition.defaultKg, goal: d.profile.goal)
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    dayPicker(date)
                    macros(entries, t)
                    water(d.water[day] ?? 0, goal: t.waterMl, day: day)
                    SectionTitle(text: L("Tagebuch", "Diary"))
                    if entries.isEmpty {
                        Card { Text(L("Noch nichts eingetragen.", "Nothing logged yet.")).foregroundStyle(Ink.muted) }
                    }
                    ForEach(entries) { e in
                        Card(padding: 14) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(e.name).foregroundStyle(Ink.text)
                                    Text(L("\(e.protein) g Eiweiß · \(e.carbs) g KH · \(e.fat) g Fett", "\(e.protein) g protein · \(e.carbs) g carbs · \(e.fat) g fat"))
                                        .font(.caption).foregroundStyle(Ink.muted)
                                }
                                Spacer()
                                Text("\(e.kcal) kcal").font(.system(.subheadline, design: .rounded).weight(.semibold)).foregroundStyle(Ink.text)
                            }
                        }
                        .contextMenu {
                            Button(role: .destructive) { store.deleteFood(e.id) } label: { Label(L("Löschen", "Delete"), systemImage: "trash") }
                        }
                    }
                    Text(L("Ziele aus Körpergewicht (\(Fmt.num(Stats.latestWeight(d) ?? Nutrition.defaultKg)) kg) und Ziel (\(d.profile.goal.label)).",
                           "Targets from body weight (\(Fmt.num(Stats.latestWeight(d) ?? Nutrition.defaultKg)) kg) and goal (\(d.profile.goal.label))."))
                        .font(.caption).foregroundStyle(Ink.faint).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
            }
            .screen()
            .navigationTitle(L("Essen", "Food"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { adding = true } label: { Image(systemName: "plus") }.accessibilityLabel(L("Eintragen", "Log food"))
                }
            }
            .sheet(isPresented: $adding) { FoodEditor(day: day) }
        }
    }

    private func dayPicker(_ date: Date) -> some View {
        HStack {
            Button { dayOffset -= 1 } label: { Image(systemName: "chevron.left").frame(width: 36, height: 36) }
            Spacer()
            Text(dayOffset == 0 ? L("Heute", "Today") : dayOffset == -1 ? L("Gestern", "Yesterday")
                 : date.formatted(Date.FormatStyle(date: .abbreviated, time: .omitted).locale(I18n.locale)))
                .font(.headline).foregroundStyle(Ink.text)
            Spacer()
            Button { dayOffset += 1 } label: { Image(systemName: "chevron.right").frame(width: 36, height: 36) }
                .disabled(dayOffset >= 0)
        }
        .foregroundStyle(Ink.muted)
    }

    private func macros(_ entries: [FoodEntry], _ t: Nutrition.Targets) -> some View {
        let kcal = entries.reduce(0) { $0 + $1.kcal }
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("\(kcal)").font(.system(size: 40, weight: .bold, design: .rounded)).foregroundStyle(Ink.text)
                Text(L("von \(t.kcal) kcal", "of \(t.kcal) kcal")).foregroundStyle(Ink.muted)
                Spacer()
                let left = t.kcal - kcal
                Text(left >= 0 ? L("noch \(left)", "\(left) left") : L("\(-left) drüber", "\(-left) over"))
                    .font(.subheadline.weight(.semibold)).foregroundStyle(left >= 0 ? Ink.accent : Ink.coral)
            }
            Meter(fraction: Double(kcal) / Double(max(1, t.kcal)))
            HStack(spacing: 12) {
                macro(L("Eiweiß", "Protein"), entries.reduce(0) { $0 + $1.protein }, t.protein, Ink.accent)
                macro(L("Kohlenhydrate", "Carbs"), entries.reduce(0) { $0 + $1.carbs }, t.carbs, Ink.mauve)
                macro(L("Fett", "Fat"), entries.reduce(0) { $0 + $1.fat }, t.fat, Ink.coral)
            }
        }
    }

    private func macro(_ name: String, _ value: Int, _ target: Int, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(name).font(.caption).foregroundStyle(Ink.muted).lineLimit(1)
            Text("\(value)/\(target) g").font(.system(.subheadline, design: .rounded).weight(.semibold)).foregroundStyle(Ink.text)
            Meter(fraction: Double(value) / Double(max(1, target)), color: color)
        }
        .frame(maxWidth: .infinity)
    }

    private func water(_ ml: Int, goal: Int, day: String) -> some View {
        Card {
            HStack {
                Image(systemName: "drop.fill").foregroundStyle(Ink.mauve)
                Text(L("Wasser", "Water")).font(.headline).foregroundStyle(Ink.text)
                Spacer()
                Text("\(Fmt.num(Double(ml) / 1000, digits: 2)) / \(Fmt.num(Double(goal) / 1000, digits: 2)) l")
                    .font(.system(.subheadline, design: .rounded).weight(.semibold)).foregroundStyle(Ink.muted)
            }
            Meter(fraction: Double(ml) / Double(max(1, goal)), color: Ink.mauve)
            HStack {
                Button("-250 ml") { store.addWater(-250, day: day) }.buttonStyle(.pillOutline).disabled(ml <= 0)
                Button("+250 ml") { store.addWater(250, day: day) }.buttonStyle(PillButtonStyle(color: Ink.mauve))
                Button("+500 ml") { store.addWater(500, day: day) }.buttonStyle(PillButtonStyle(color: Ink.mauve))
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

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L("Was hast du gegessen?", "What did you eat?"), text: $name)
                    field("kcal", $kcal)
                    field(L("Eiweiß (g)", "Protein (g)"), $protein)
                    field(L("Kohlenhydrate (g)", "Carbs (g)"), $carbs)
                    field(L("Fett (g)", "Fat (g)"), $fat)
                }
                .listRowBackground(Ink.surface)
            }
            .screen()
            .navigationTitle(L("Eintragen", "Log food"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Speichern", "Save")) {
                        store.addFood(FoodEntry(
                            date: day, name: name.trimmingCharacters(in: .whitespaces),
                            kcal: Int(kcal) ?? 0, protein: Int(protein) ?? 0, carbs: Int(carbs) ?? 0, fat: Int(fat) ?? 0
                        ))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || Int(kcal) == nil)
                }
            }
        }
    }

    private func field(_ label: String, _ value: Binding<String>) -> some View {
        HStack {
            Text(label).foregroundStyle(Ink.muted)
            TextField("0", text: value).keyboardType(.numberPad).multilineTextAlignment(.trailing)
        }
    }
}
