import SwiftUI

/**
 * Cardio this week with a bar per day, logging by hand and, with Apple Health switched on,
 * taking over sessions from the watch, as on Android (ui/CardioUi.kt).
 */
struct CardioCard: View {
    @Environment(Store.self) private var store
    @State private var logging = false
    @State private var status: String?
    @State private var importing = false

    var body: some View {
        Card {
            CardioWeek(cardio: store.data.cardio)
            Text(L("Cardio zählt nicht zum Wochenziel, das bleibt beim Krafttraining.",
                   "Cardio does not count toward the weekly goal, which stays with strength training."))
                .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
            HStack(spacing: 8) {
                Button { logging = true } label: { Label(L("Cardio eintragen", "Log cardio"), systemImage: "plus") }
                    .buttonStyle(.primary)
                    .accessibilityIdentifier("logCardio")
                if store.data.settings.healthWrite {
                    Button { Task { await importFromHealth() } } label: { Label(L("Aus Health", "From Health"), systemImage: "heart.fill") }
                        .buttonStyle(.ghost)
                        .disabled(importing)
                }
            }
            if let status { Text(status).font(.fit(.bodySmall)).foregroundStyle(Ink.accent) }
        }
        .sheet(isPresented: $logging) { CardioSheet { store.addCardio($0) } }
    }

    private func importFromHealth() async {
        importing = true
        defer { importing = false }
        guard let found = await Health.shared.recentCardio() else {
            status = L("Kein Zugriff auf Trainings in Apple Health.", "No access to workouts in Apple Health.")
            return
        }
        let added = store.importCardio(found)
        status = added == 0 ? L("Nichts Neues aus den letzten 14 Tagen.", "Nothing new from the last 14 days.")
            : added == 1 ? L("1 Einheit übernommen.", "1 session imported.") : L("\(added) Einheiten übernommen.", "\(added) sessions imported.")
    }
}

/** "Cardio this week: 95 min" with a bar per day, today's label in the accent. */
struct CardioWeek: View {
    let cardio: [CardioSession]

    var body: some View {
        let minutes = Cardio.weekMinutes(cardio)
        let total = minutes.reduce(0, +)
        let peak = max(1, minutes.max() ?? 1)
        let today = Stats.isoWeekday(Date()) - 1
        let start = Stats.weekStart(Date())
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(text: L("Cardio diese Woche", "Cardio this week")).padding(.top, -10)
            Text(Cardio.fmtMinutes(total)).font(.fit(.headline)).foregroundStyle(total > 0 ? Ink.mauve : Ink.text)
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(0..<7, id: \.self) { i in
                    VStack(spacing: 6) {
                        GeometryReader { g in
                            VStack {
                                Spacer(minLength: 0)
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(minutes[i] == 0 ? Ink.raised : Ink.mauve)
                                    .frame(height: max(4, g.size.height * CGFloat(minutes[i]) / CGFloat(peak)))
                            }
                        }
                        Text(String(Stats.addDays(start, i).formatted(Date.FormatStyle().weekday(.narrow).locale(I18n.locale))))
                            .font(.fit(.labelSmall)).foregroundStyle(i == today ? Ink.accent : Ink.faint)
                    }
                }
            }
            .frame(height: 72)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("Cardio diese Woche: \(Cardio.fmtMinutes(total))", "Cardio this week: \(Cardio.fmtMinutes(total))"))
        }
    }
}

/** Log sheet: kind, minutes, and optionally distance and effort. The session ends now. */
struct CardioSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onSave: (CardioSession) -> Void
    @State private var kind: CardioKind = .run
    @State private var minutes = 30
    @State private var distance = ""
    @State private var effort: Cardio.Effort?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(L("Art", "Kind"), selection: $kind) {
                        ForEach(CardioKind.allCases) { k in Label(k.label, systemImage: k.symbol).tag(k) }
                    }
                    Stepper(L("Dauer: \(Cardio.fmtMinutes(minutes))", "Duration: \(Cardio.fmtMinutes(minutes))"), value: $minutes, in: 5...600, step: 5)
                    HStack {
                        Text(L("Strecke in km", "Distance in km")).foregroundStyle(Ink.muted)
                        TextField(L("optional", "optional"), text: $distance).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                    }
                }
                .listRowBackground(Ink.surface)
                Section(L("Anstrengung (optional)", "Effort (optional)")) {
                    HStack(spacing: 8) {
                        // Tapping the chosen one again clears it.
                        ForEach(Cardio.Effort.allCases) { e in Chip(text: e.label, on: effort == e) { effort = effort == e ? nil : e } }
                    }
                }
                .listRowBackground(Ink.surface)
            }
            .font(.fit(.body))
            .screen()
            .navigationTitle(L("Cardio eintragen", "Log cardio"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Speichern", "Save")) {
                        let km = Double(distance.replacingOccurrences(of: ",", with: ".")).flatMap { (0.1...500).contains($0) ? $0 : nil }
                        onSave(CardioSession(at: nowMs() - Int64(minutes) * 60_000, kind: kind, minutes: minutes, distanceKm: km, rpe: effort?.rawValue))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/** A cardio session in the history: a mauve icon, and minutes instead of volume. */
struct CardioRow: View {
    @Environment(Store.self) private var store
    let session: CardioSession
    @State private var confirm = false

    var body: some View {
        let s = session
        Card(padding: 14) {
            HStack(spacing: 12) {
                Image(systemName: s.kind.symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(Ink.mauve)
                    .frame(width: 44, height: 44).background(Circle().fill(Ink.mauve.opacity(0.16)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.note.isEmpty ? s.kind.label : s.note).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1)
                    Text([Fmt.date(s.at), Cardio.Effort.of(s.rpe)?.label, s.avgHr.map { "\($0) bpm" }, s.source == "health" ? "Apple Health" : nil]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Cardio.fmtMinutes(s.minutes)).font(.fit(.titleMedium)).foregroundStyle(Ink.mauve)
                    if let km = s.distanceKm { Text(Cardio.fmtKm(km)).font(.fit(.bodySmall)).foregroundStyle(Ink.faint) }
                }
                Button { confirm = true } label: {
                    Image(systemName: "trash").font(.system(size: 14, weight: .semibold)).foregroundStyle(Ink.muted)
                        .frame(width: 36, height: 36).background(Circle().fill(Ink.raised))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Cardio löschen", "Delete cardio"))
            }
        }
        .alert(L("Cardio löschen?", "Delete cardio?"), isPresented: $confirm) {
            Button(L("Löschen", "Delete"), role: .destructive) { store.deleteCardio(s.id) }
            Button(L("Abbrechen", "Cancel"), role: .cancel) {}
        }
    }
}
