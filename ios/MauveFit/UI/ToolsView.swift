import SwiftUI

/** Gym helpers that need no workout: interval timer, 1RM, plates and warm-up sets (ui/ToolsScreen.kt). */
struct ToolsView: View {
    @Environment(Store.self) private var store

    var body: some View {
        let s = store.data.settings
        ScrollView {
            VStack(spacing: 12) {
                Text(L("Timer und Rechner fürs Training", "Timer and calculators for training"))
                    .font(.fit(.body)).foregroundStyle(Ink.muted).frame(maxWidth: .infinity, alignment: .leading)
                CardGrid {
                    IntervalCard()
                    OneRmCard(step: s.weightStep)
                    PlateCard(bar: s.barWeight)
                    WarmupCard(step: s.weightStep, bar: s.barWeight)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .screen()
        .scrollDismissesKeyboard(.interactively)
        .accessibilityIdentifier("toolsScreen")
        .navigationTitle(L("Werkzeuge", "Tools"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private func toolHeader(_ icon: String, _ title: String, _ sub: String) -> some View {
    HStack(spacing: 12) {
        Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(Ink.accent)
            .frame(width: 40, height: 40).background(Circle().fill(Ink.accent.opacity(0.16)))
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
            Text(sub).font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
        }
    }
}

/** Accepts "62,5" as well as "62.5"; nil for anything that is not a sensible load. */
private func parseKg(_ text: String) -> Double? {
    Double(text.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 && $0 < 1000 ? $0 : nil }
}

private struct KgField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        HStack {
            Text(label).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
            TextField("0", text: $text).keyboardType(.decimalPad).multilineTextAlignment(.trailing).font(.fit(.number))
            Text("kg").font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Ink.raised, in: RoundedRectangle(cornerRadius: Radius.row, style: .continuous))
    }
}

private func tableRow(_ a: String, _ b: String, _ c: String, header: Bool, tinted: Bool) -> some View {
    HStack {
        Text(a).frame(maxWidth: .infinity, alignment: .leading)
        Text(b).frame(maxWidth: .infinity, alignment: .leading)
        Text(c).frame(maxWidth: .infinity, alignment: .trailing)
    }
    .font(header ? .fit(.labelSmall) : .fit(.bodyMedium))
    .foregroundStyle(header ? Ink.muted : Ink.text)
    .padding(.horizontal, 10).padding(.vertical, 6)
    .background(tinted ? Ink.raised : .clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
}

/** EMOM, Tabata or own intervals, drawn from the elapsed time so it never drifts. */
private struct IntervalCard: View {
    enum Mode: CaseIterable { case emom, tabata, custom }
    @State private var mode: Mode = .emom
    @State private var emomRounds = 10
    @State private var work = 40
    @State private var rest = 20
    @State private var rounds = 8
    /** Running since, and the time banked before a pause. */
    @State private var startedAt: Date?
    @State private var banked: Int64 = 0

    private var plan: Intervals.Plan {
        switch mode {
        case .emom: Intervals.emom(emomRounds)
        case .tabata: Intervals.tabata
        case .custom: Intervals.Plan(workSec: work, restSec: rest, rounds: rounds)
        }
    }

    var body: some View {
        Card {
            toolHeader("timer", L("Intervall-Timer", "Interval timer"), L("EMOM, Tabata oder eigene Intervalle", "EMOM, Tabata or your own intervals"))
            let idle = startedAt == nil && banked == 0
            if idle {
                HStack(spacing: 8) {
                    Chip(text: "EMOM", on: mode == .emom) { mode = .emom }
                    Chip(text: "Tabata", on: mode == .tabata) { mode = .tabata }
                    Chip(text: L("Eigene", "Custom"), on: mode == .custom) { mode = .custom }
                }
                switch mode {
                case .emom:
                    StepperBox(label: L("Runden zu je 1 Minute", "Rounds of 1 minute"), value: "\(emomRounds)",
                               minus: { emomRounds = max(1, emomRounds - 1) }, plus: { emomRounds = min(60, emomRounds + 1) })
                case .tabata:
                    Text(L("20 s Arbeit, 10 s Pause, 8 Runden", "20 s work, 10 s rest, 8 rounds")).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
                case .custom:
                    HStack(spacing: 8) {
                        StepperBox(label: L("Arbeit", "Work"), value: Intervals.fmt(work), minus: { work = max(5, work - 5) }, plus: { work = min(600, work + 5) })
                        StepperBox(label: L("Pause", "Rest"), value: Intervals.fmt(rest), minus: { rest = max(0, rest - 5) }, plus: { rest = min(600, rest + 5) })
                        StepperBox(label: L("Runden", "Rounds"), value: "\(rounds)", minus: { rounds = max(1, rounds - 1) }, plus: { rounds = min(50, rounds + 1) })
                    }
                }
                Text(L("Gesamt \(Intervals.fmt(Int(plan.totalMs / 1000)))", "Total \(Intervals.fmt(Int(plan.totalMs / 1000)))"))
                    .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
            }
            TimelineView(.periodic(from: .now, by: 0.25)) { ctx in
                let elapsed = banked + (startedAt.map { Int64(ctx.date.timeIntervalSince($0) * 1000) } ?? 0)
                let tick = Intervals.at(plan, elapsed)
                let color = tick.phase == .rest ? Ink.mauve : Ink.accent
                VStack(alignment: .leading, spacing: 6) {
                    Text(idle ? L("Bereit", "Ready") : tick.phase == .work ? L("Arbeit", "Work") : tick.phase == .rest ? L("Pause", "Rest") : L("Fertig", "Done"))
                        .font(.fit(.titleSmall)).foregroundStyle(color)
                    Text(Intervals.fmt(idle ? plan.workSec : Intervals.secondsLeft(tick))).font(.fit(.numberLarge)).foregroundStyle(Ink.text)
                    Text(L("Runde \(max(1, tick.round)) von \(plan.rounds)", "Round \(max(1, tick.round)) of \(plan.rounds)"))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                    Meter(fraction: tick.phaseMs > 0 ? 1 - Double(tick.remainingMs) / Double(tick.phaseMs) : (tick.phase == .done ? 1 : 0), color: color)
                }
                .sensoryFeedback(.impact, trigger: tick.phase == .work ? tick.round * 2 : tick.round * 2 + 1)
                .accessibilityElement(children: .combine)
            }
            HStack(spacing: 8) {
                if startedAt != nil {
                    Button { banked += Int64(Date().timeIntervalSince(startedAt!) * 1000); startedAt = nil } label: { Label(L("Pause", "Pause"), systemImage: "pause.fill") }
                        .buttonStyle(.primary)
                } else {
                    let done = banked >= plan.totalMs
                    Button {
                        if done { banked = 0 }
                        startedAt = Date()
                    } label: { Label(banked == 0 || done ? L("Starten", "Start") : L("Weiter", "Resume"), systemImage: "play.fill") }
                        .buttonStyle(.primary)
                }
                RoundButton(icon: "arrow.counterclockwise", label: L("Zurücksetzen", "Reset"), size: 52) { startedAt = nil; banked = 0 }
                    .disabled(idle).opacity(idle ? 0.4 : 1)
            }
        }
        // Keeps counting while the screen is locked only as long as the app is open; that is enough for a timer you watch.
        .onDisappear { if let s = startedAt { banked += Int64(Date().timeIntervalSince(s) * 1000); startedAt = nil } }
    }
}

/** Estimate the max from one hard set, and the loads per share of it. */
private struct OneRmCard: View {
    let step: Double
    @State private var weight = ""
    @State private var reps = 5

    var body: some View {
        Card {
            toolHeader("chart.line.uptrend.xyaxis", L("1RM-Rechner", "1RM calculator"), L("Maximalkraft aus einem schweren Satz schätzen", "Estimate your max from one hard set"))
            HStack(spacing: 8) {
                KgField(label: L("Gewicht", "Weight"), text: $weight)
                StepperBox(label: L("Wiederholungen", "Reps"), value: "\(reps)", minus: { reps = max(1, reps - 1) }, plus: { reps = min(30, reps + 1) })
                    .frame(width: 132)
            }
            if let kg = parseKg(weight) {
                let oneRm = Strength.oneRm(kg, reps)
                SectionTitle(text: L("Geschätztes 1RM", "Estimated 1RM")).padding(.top, -6)
                Text("\(Fmt.kg(Stats.roundTo(oneRm, step))) kg").font(.fit(.headline)).foregroundStyle(Ink.accent)
                if reps > 12 {
                    Text(L("Über 12 Wiederholungen wird die Schätzung ungenau.", "Beyond 12 reps the estimate gets rough.")).font(.fit(.bodySmall)).foregroundStyle(Ink.coral)
                }
                tableRow(L("Anteil", "Share"), L("Gewicht", "Weight"), L("Wdh.", "Reps"), header: true, tinted: false)
                ForEach(Array(Strength.table(oneRm, step: step).enumerated()), id: \.offset) { i, r in
                    tableRow("\(r.percent) %", "\(Fmt.kg(r.weight)) kg", L("ca. \(r.reps)", "about \(r.reps)"), header: false, tinted: i % 2 == 0)
                }
            } else {
                Text(L("Trag Gewicht und Wiederholungen eines Satzes ein, den du sauber geschafft hast.", "Enter weight and reps of a set you completed with good form."))
                    .font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
            }
        }
    }
}

/** What goes on each side of the bar, drawn as plates sized by weight. */
private struct PlateCard: View {
    let bar: Double
    @State private var target = ""
    @State private var barKg: Double?

    var body: some View {
        let b = barKg ?? bar
        Card {
            toolHeader("scalemass", L("Scheibenrechner", "Plate calculator"), L("Was pro Seite auf die Stange kommt", "What goes on each side of the bar"))
            HStack(spacing: 8) {
                KgField(label: L("Zielgewicht", "Target weight"), text: $target)
                StepperBox(label: L("Stange", "Bar"), value: "\(Fmt.kg(b)) kg", minus: { barKg = max(0, b - 2.5) }, plus: { barKg = min(30, b + 2.5) })
                    .frame(width: 132)
            }
            if let total = parseKg(target) {
                if let plates = Coach.plates(total, bar: b) {
                    let loaded = b + plates.reduce(0, +) * 2
                    if plates.isEmpty {
                        Text(total - b > 1e-6 ? L("Nur die Stange. Nächstmöglich: \(Fmt.kg(b)) kg", "Just the bar. Closest possible: \(Fmt.kg(b)) kg") : L("Nur die Stange", "Just the bar"))
                            .font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
                    } else {
                        SectionTitle(text: L("Pro Seite", "Per side")).padding(.top, -6)
                        PlateRow(plates: plates)
                        Text(plates.map { Fmt.kg($0) }.joined(separator: " + ") + " kg").font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                        if abs(loaded - total) > 1e-6 {
                            Text(L("Nächstmöglich: \(Fmt.kg(loaded)) kg", "Closest possible: \(Fmt.kg(loaded)) kg")).font(.fit(.bodySmall)).foregroundStyle(Ink.coral)
                        }
                    }
                } else {
                    Text(L("Weniger als die Stange (\(Fmt.kg(b)) kg)", "Less than the bar (\(Fmt.kg(b)) kg)")).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
                }
            }
        }
    }
}

struct PlateRow: View {
    let plates: [Double]

    var body: some View {
        HStack(alignment: .center, spacing: 3) {
            RoundedRectangle(cornerRadius: 2).fill(Ink.lineStrong).frame(width: 20, height: 8)
            ForEach(Array(plates.enumerated()), id: \.offset) { _, p in
                RoundedRectangle(cornerRadius: 4).fill(plateColor(p)).frame(width: p >= 10 ? 16 : 10, height: 28 + p * 2.6)
            }
        }
        .frame(height: 96)
        .accessibilityHidden(true)
    }

    private func plateColor(_ kg: Double) -> Color {
        switch kg {
        case 25: Ink.red
        case 20: Color(hex: 0x5B8CFF)
        case 15: Color(hex: 0xFFD43B)
        case 10: Color(hex: 0x4CAF6A)
        default: Ink.faint
        }
    }
}

/** Ramp to the working weight, with the plates per side. */
private struct WarmupCard: View {
    let step: Double
    let bar: Double
    @State private var weight = ""

    var body: some View {
        Card {
            toolHeader("flame", L("Aufwärmsätze", "Warm-up sets"), L("Rampe zu deinem Arbeitsgewicht", "Ramp up to your working weight"))
            KgField(label: L("Arbeitsgewicht", "Working weight"), text: $weight)
            if let work = parseKg(weight) {
                let sets = Coach.warmups(work, step: step, bar: bar)
                if sets.isEmpty {
                    Text(L("Bei diesem Gewicht reicht die leere Stange zum Aufwärmen.", "At this weight the empty bar is warm-up enough.")).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
                } else {
                    tableRow(L("Satz", "Set"), L("Gewicht", "Weight"), L("Pro Seite", "Per side"), header: true, tinted: false)
                    ForEach(Array(sets.enumerated()), id: \.offset) { i, s in
                        tableRow("\(s.reps) ×", "\(Fmt.kg(s.weight)) kg", perSide(s.weight), header: false, tinted: i % 2 == 0)
                    }
                    HStack {
                        Text(L("Arbeit", "Work")).font(.fit(.bodyMedium).weight(.semibold)).foregroundStyle(Ink.accent)
                        Spacer()
                        Text("\(Fmt.kg(work)) kg").font(.fit(.bodyMedium).weight(.semibold)).foregroundStyle(Ink.accent)
                    }
                    .padding(.horizontal, 10)
                }
            }
        }
    }

    private func perSide(_ total: Double) -> String {
        guard let plates = Coach.plates(total, bar: bar) else { return "–" }
        return plates.isEmpty ? L("Stange", "Bar") : plates.map { Fmt.kg($0) }.joined(separator: " + ")
    }
}
