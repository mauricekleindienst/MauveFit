import SwiftUI

/** The running workout: one card per exercise with its sets, a rest timer and finish; then the summary. */
struct WorkoutView: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router
    @State private var picking = false
    @State private var confirmFinish = false
    @State private var confirmDiscard = false
    @State private var detail: Exercise?
    /** The workout just saved, shown as the summary until "Done". */
    @State private var summaryId: String? = Demo.summaryId

    var body: some View {
        NavigationStack {
            Group {
                if let id = summaryId {
                    SummaryView(workoutId: id) { router.showWorkout = false }
                } else if let w = store.data.active {
                    content(w)
                } else {
                    // Discarded, or saved with nothing ticked: the cover closes itself.
                    Color.clear.onAppear { router.showWorkout = false }
                }
            }
            .screen()
            .sheet(isPresented: $picking) { ExercisePicker { store.addExercise($0) } }
            .sheet(item: $detail) { e in NavigationStack { ExerciseDetailView(exercise: e) } }
            .confirmationDialog(L("Training beenden?", "Finish workout?"), isPresented: $confirmFinish, titleVisibility: .visible) {
                Button(L("Speichern und beenden", "Save and finish")) {
                    if let id = store.finishWorkout() { summaryId = id } else { router.showWorkout = false }
                }
                Button(L("Verwerfen", "Discard"), role: .destructive) { confirmDiscard = true }
                Button(L("Weiter trainieren", "Keep training"), role: .cancel) {}
            } message: {
                Text(L("Nicht abgehakte Sätze werden nicht gespeichert.", "Sets that are not ticked off are not saved."))
            }
            .alert(L("Training verwerfen?", "Discard workout?"), isPresented: $confirmDiscard) {
                Button(L("Verwerfen", "Discard"), role: .destructive) {
                    store.discardWorkout()
                    router.showWorkout = false
                }
                Button(L("Abbrechen", "Cancel"), role: .cancel) {}
            } message: {
                Text(L("Alle Sätze dieses Trainings gehen verloren.", "Every set of this workout is lost."))
            }
        }
    }

    private func content(_ w: Workout) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                header(w)
                ForEach(Array(w.entries.enumerated()), id: \.element.id) { i, e in
                    EntryCard(index: i, entry: e, onInfo: { detail = $0 })
                }
                Button { picking = true } label: { Label(L("Übung hinzufügen", "Add exercise"), systemImage: "plus") }
                    .buttonStyle(.ghost)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, w.restEndsAt != nil ? 130 : 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .overlay(alignment: .bottom) { RestBar() }
        .navigationTitle(w.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button { router.showWorkout = false } label: { Image(systemName: "chevron.down").fontWeight(.semibold) }
                    .accessibilityLabel(L("Minimieren", "Minimise"))
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(L("Beenden", "Finish")) { confirmFinish = true }
                    .fontWeight(.bold)
                    .accessibilityIdentifier("finish")
            }
        }
    }

    private func header(_ w: Workout) -> some View {
        HStack(spacing: 10) {
            TimelineView(.periodic(from: .now, by: 1)) { _ in
                StatTile(value: Fmt.clock(Int((nowMs() - w.startedAt) / 1000)), label: L("Dauer", "Duration"))
            }
            StatTile(value: "\(w.doneSets.count)", label: w.doneSets.count == 1 ? L("Satz", "Set") : L("Sätze", "Sets"))
            StatTile(value: Stats.headline(w), label: w.volume > 0 || w.doneSets.isEmpty ? L("Volumen", "Volume") : L("Gesamt", "Total"), color: Ink.accent)
        }
        .padding(.top, 8)
    }
}

/** One exercise of the running workout with its set rows. */
private struct EntryCard: View {
    @Environment(Store.self) private var store
    let index: Int
    let entry: ExerciseEntry
    let onInfo: (Exercise) -> Void

    var body: some View {
        let ex = ExerciseRepo.shared[entry.exerciseId]
        let last = Stats.lastSets(store.data.history, entry.exerciseId)
        // Weight stays editable on a body-weight exercise once extra weight was used.
        let bodyweight = ex?.bodyweight == true && !entry.sets.contains { $0.weight > 0 }
        let done = entry.sets.filter { $0.done && !$0.warmup }.count
        let total = entry.sets.filter { !$0.warmup }.count
        Card(padding: 16) {
            HStack(spacing: 12) {
                Button { if let ex { onInfo(ex) } } label: { ExerciseThumb(exercise: ex, size: 56) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("Anleitung", "How to"))
                VStack(alignment: .leading, spacing: 3) {
                    Text(ex?.title ?? entry.exerciseId).font(.fit(.titleLarge)).foregroundStyle(Ink.text).lineLimit(2)
                    Text(last.isEmpty ? L("\(done) von \(total)", "\(done) of \(total)") + (ex.map { " · " + Labels.muscle($0.target) } ?? "")
                         : L("Letztes Mal: ", "Last time: ") + last.map { Fmt.set($0, bodyweight: bodyweight) }.joined(separator: ", "))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted).lineLimit(1)
                }
                Spacer(minLength: 0)
                Menu {
                    if let ex { Button { onInfo(ex) } label: { Label(L("Anleitung", "How to"), systemImage: "info.circle") } }
                    Menu(L("Pause: \(Fmt.clock(entry.restSec))", "Rest: \(Fmt.clock(entry.restSec))")) {
                        ForEach([0, 30, 45, 60, 90, 120, 150, 180, 240], id: \.self) { s in
                            Button(s == 0 ? L("Keine", "None") : Fmt.clock(s)) { store.setRest(index, seconds: s) }
                        }
                    }
                    Button(role: .destructive) { store.removeEntry(index) } label: { Label(L("Übung entfernen", "Remove exercise"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 16, weight: .semibold)).foregroundStyle(Ink.text)
                        .frame(width: 40, height: 40).background(Circle().fill(Ink.raised))
                }
                .accessibilityLabel(L("Mehr", "More"))
            }
            HStack(spacing: 8) {
                Text(L("Satz", "Set")).frame(width: 34, alignment: .leading)
                if !bodyweight { Text("kg").frame(maxWidth: .infinity) }
                Text(L("Wdh.", "Reps")).frame(maxWidth: .infinity)
                Spacer().frame(width: 44)
            }
            .font(.fit(.labelSmall)).foregroundStyle(Ink.faint)
            .padding(.top, 4)
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            ForEach(Array(entry.sets.enumerated()), id: \.offset) { si, s in
                // Working sets count from 1; warm-ups show a W and are skipped.
                let number = entry.sets.prefix(si + 1).filter { !$0.warmup }.count
                SetRow(entry: index, set: si, value: s, label: s.warmup ? "W" : "\(number)", bodyweight: bodyweight)
                    // Four controls in a row: larger text would push the numbers out of their fields.
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .contextMenu {
                        Button { store.setSet(index, si) { $0.warmup.toggle() } } label: {
                            Label(s.warmup ? L("Arbeitssatz", "Working set") : L("Aufwärmsatz", "Warm-up set"), systemImage: "flame")
                        }
                        Button(role: .destructive) { store.removeSet(index, si) } label: { Label(L("Satz löschen", "Delete set"), systemImage: "trash") }
                    }
            }
            Button { store.addSet(index) } label: {
                Label(L("Satz hinzufügen", "Add set"), systemImage: "plus").font(.fit(.label)).frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(Capsule().fill(Ink.raised))
            }
            .buttonStyle(.plain).foregroundStyle(Ink.text).padding(.top, 2)
        }
    }
}

/** Weight and reps of one set with steppers, and the tick that starts the rest. */
private struct SetRow: View {
    @Environment(Store.self) private var store
    let entry: Int
    let set: Int
    let value: SetEntry
    let label: String
    let bodyweight: Bool

    var body: some View {
        let step = store.data.settings.weightStep
        HStack(spacing: 8) {
            Text(label).font(.fit(.number))
                .foregroundStyle(value.warmup ? Ink.coral : Ink.muted).frame(width: 34, alignment: .leading)
            if !bodyweight {
                NumberField(
                    name: L("Gewicht", "Weight"),
                    text: Fmt.kg(value.weight),
                    done: value.done,
                    minus: { store.setSet(entry, set) { $0.weight = max(0, $0.weight - step) } },
                    plus: { store.setSet(entry, set) { $0.weight += step } },
                    commit: { v in store.setSet(entry, set) { $0.weight = max(0, v) } }
                )
            }
            NumberField(
                name: L("Wiederholungen", "Reps"),
                text: "\(value.reps)",
                done: value.done,
                minus: { store.setSet(entry, set) { $0.reps = max(0, $0.reps - 1) } },
                plus: { store.setSet(entry, set) { $0.reps += 1 } },
                commit: { v in store.setSet(entry, set) { $0.reps = max(0, Int(v.rounded())) } }
            )
            Button { store.toggleDone(entry, set) } label: {
                Image(systemName: "checkmark")
                    .font(.system(size: 16, weight: .heavy))
                    .foregroundStyle(value.done ? Ink.bg : Ink.muted)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(value.done ? Ink.accent : Ink.raised))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(value.done ? L("Satz offen", "Mark set open") : L("Satz erledigt", "Mark set done"))
            .sensoryFeedback(.success, trigger: value.done) { _, now in now }
        }
        .padding(.vertical, 2)
    }
}

/** A value with minus and plus; tapping the number edits it with the keyboard. */
private struct NumberField: View {
    /** What the value is, for VoiceOver ("Gewicht", "Wiederholungen"). */
    let name: String
    let text: String
    let done: Bool
    let minus: () -> Void
    let plus: () -> Void
    let commit: (Double) -> Void
    @State private var editing = false
    @State private var draft = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 0) {
            Button(action: minus) { Image(systemName: "minus").font(.system(size: 13, weight: .bold)).frame(width: 30, height: 44) }
                .accessibilityLabel(L("\(name) verringern", "Decrease \(name.lowercased())"))
            Group {
                if editing {
                    TextField("", text: $draft)
                        .keyboardType(.decimalPad)
                        .focused($focused)
                        .onSubmit(save)
                        .onChange(of: focused) { _, f in if !f { save() } }
                } else {
                    Text(text).lineLimit(1).minimumScaleFactor(0.7)
                        .accessibilityLabel("\(name): \(text)")
                        .accessibilityHint(L("Doppeltippen zum Eintippen", "Double-tap to type"))
                        .accessibilityAddTraits(.isButton)
                        .onTapGesture {
                        draft = text
                        editing = true
                        focused = true
                    }
                }
            }
            .font(.fit(.number).monospacedDigit())
            .multilineTextAlignment(.center)
            .foregroundStyle(Ink.text)
            .frame(maxWidth: .infinity)
            Button(action: plus) { Image(systemName: "plus").font(.system(size: 13, weight: .bold)).frame(width: 30, height: 44) }
                .accessibilityLabel(L("\(name) erhöhen", "Increase \(name.lowercased())"))
        }
        .buttonStyle(.plain)
        .foregroundStyle(Ink.muted)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(done ? Ink.accent.opacity(0.14) : Ink.raised))
    }

    private func save() {
        guard editing else { return }
        editing = false
        if let v = Double(draft.replacingOccurrences(of: ",", with: ".")) { commit(v) }
    }
}

/** The running rest timer, pinned to the bottom of the workout. */
private struct RestBar: View {
    @Environment(Store.self) private var store

    var body: some View {
        if let end = store.data.active?.restEndsAt {
            TimelineView(.periodic(from: .now, by: 0.5)) { ctx in
                let left = Int((Double(end) - ctx.date.timeIntervalSince1970 * 1000) / 1000 + 0.999)
                let total = max(1, store.data.active?.restTotalSec ?? 1)
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(left > 0 ? L("Pause", "Rest") : L("Weiter geht's", "Go"))
                            .font(.fit(.titleSmall)).foregroundStyle(Ink.mauve)
                        Text(Fmt.clock(left)).font(.fit(.numberLarge).monospacedDigit()).foregroundStyle(Ink.text)
                        Meter(fraction: Double(left) / Double(total), color: Ink.mauve)
                    }
                    Button("-15") { store.shiftRest(-15) }.buttonStyle(.pillOutline)
                        .accessibilityLabel(L("15 Sekunden weniger", "15 seconds less"))
                    Button("+15") { store.shiftRest(15) }.buttonStyle(.pillOutline)
                        .accessibilityLabel(L("15 Sekunden mehr", "15 seconds more"))
                    Button { store.skipRest() } label: { Image(systemName: "forward.end.fill") }
                        .buttonStyle(PillButtonStyle(color: Ink.mauve))
                        .accessibilityLabel(L("Pause überspringen", "Skip rest"))
                }
                .padding(18)
                .floatingGlass(in: RoundedRectangle(cornerRadius: Radius.hero, style: .continuous))
                .glassGroup()
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("restBar")
            }
            // Clears the finished timer a few seconds after it ran out; also one that ran out
            // while the app was closed, which would otherwise stay on "Go" for good.
            .task(id: end) {
                let wait = Double(end - nowMs()) / 1000 + 4
                if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
                if !Task.isCancelled { await MainActor.run { store.endRest(end) } }
            }
        }
    }
}

/** Shown after saving a workout: the numbers, new records, what was trained and how it felt. */
struct SummaryView: View {
    @Environment(Store.self) private var store
    let workoutId: String
    let onDone: () -> Void

    var body: some View {
        let d = store.data
        if let w = d.history.first(where: { $0.id == workoutId }) {
            let records = Stats.newRecords(w, history: d.history)
            let headline = !records.isEmpty ? L("Rekordtag.", "Record day.")
                : w.doneSets.count >= 20 ? L("Stark durchgezogen.", "Strong session.") : L("Erledigt.", "Done.")
            let index = d.history.count - (d.history.firstIndex { $0.id == w.id } ?? 0)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(w.name).font(.fit(.titleSmall)).foregroundStyle(Ink.accent)
                        Text(headline).font(.fit(.display)).tracking(-1.2).foregroundStyle(Ink.text)
                            .lineLimit(2).minimumScaleFactor(0.6)
                        Text(L("Einheit Nr. \(index) · \(Stats.workoutsThisWeek(d.history)) von \(d.settings.weeklyGoal) diese Woche",
                               "Session #\(index) · \(Stats.workoutsThisWeek(d.history)) of \(d.settings.weeklyGoal) this week"))
                            .font(.fit(.body)).foregroundStyle(Ink.muted)
                    }
                    .padding(.top, 24)
                    HStack(spacing: 10) {
                        StatTile(value: Fmt.duration(w.durationMs), label: L("Dauer", "Duration"))
                        StatTile(value: "\(w.doneSets.count)", label: w.doneSets.count == 1 ? L("Satz", "Set") : L("Sätze", "Sets"))
                        StatTile(value: Stats.headline(w), label: w.volume > 0 ? L("Volumen", "Volume") : L("Gesamt", "Total"), color: Ink.accent)
                    }
                    if !records.isEmpty {
                        Card {
                            Label(records.count == 1 ? L("Neuer Rekord", "New record") : L("\(records.count) neue Rekorde", "\(records.count) new records"),
                                  systemImage: "trophy.fill")
                                .font(.fit(.titleSmall)).foregroundStyle(Ink.accent)
                            ForEach(records, id: \.0) { id, s in
                                let ex = ExerciseRepo.shared[id]
                                HStack {
                                    Text(ex?.title ?? id).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1)
                                    Spacer()
                                    Text(Fmt.set(s, bodyweight: ex?.bodyweight == true)).font(.fit(.number)).foregroundStyle(Ink.accent)
                                }
                            }
                        }
                    }
                    Card {
                        SectionTitle(text: L("Trainiert", "Trained")).padding(.top, -10)
                        ForEach(w.entries) { e in
                            let ex = ExerciseRepo.shared[e.exerciseId]
                            HStack(spacing: 12) {
                                ExerciseThumb(exercise: ex, size: 44)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(ex?.title ?? e.exerciseId).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1)
                                    Text(e.sets.filter { !$0.warmup }.map { Fmt.set($0, bodyweight: ex?.bodyweight == true) }.joined(separator: "  "))
                                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted).lineLimit(1)
                                }
                            }
                        }
                    }
                    FeelCard(workout: w)
                    Button(L("Fertig", "Done"), action: onDone).buttonStyle(.primary).accessibilityIdentifier("summaryDone")
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .accessibilityIdentifier("summary")
            .toolbar(.hidden, for: .navigationBar)
            .sensoryFeedback(.success, trigger: records.count) { _, n in n > 0 }
        } else {
            Color.clear.onAppear(perform: onDone)
        }
    }
}

/** How the workout felt and a free note, saved on the workout as it is typed. */
struct FeelCard: View {
    @Environment(Store.self) private var store
    let workout: Workout

    var body: some View {
        Card {
            SectionTitle(text: L("Wie war's?", "How did it feel?")).padding(.top, -10)
            HStack(spacing: 8) {
                ForEach([(1, L("Leicht", "Easy"), "leaf.fill"), (2, L("Gut", "Good"), "hand.thumbsup.fill"), (3, L("Hart", "Hard"), "flame.fill")], id: \.0) { v, text, icon in
                    Chip(text: text, on: workout.feel == v, icon: icon) {
                        store.updateWorkout(workout.id) { $0.feel = $0.feel == v ? 0 : v }
                    }
                }
            }
            TextField(L("Notiz", "Note"), text: Binding(get: { workout.note }, set: { v in store.updateWorkout(workout.id) { $0.note = v } }), axis: .vertical)
                .font(.fit(.body))
                .foregroundStyle(Ink.text)
                .padding(12)
                .background(Ink.raised, in: RoundedRectangle(cornerRadius: Radius.row, style: .continuous))
        }
    }
}
