import SwiftUI

/** The running workout: one card per exercise with its sets, a rest timer and finish. */
struct WorkoutView: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router
    @State private var picking = false
    @State private var confirmFinish = false
    @State private var confirmDiscard = false
    @State private var detail: Exercise?

    var body: some View {
        NavigationStack {
            Group {
                if let w = store.data.active {
                    content(w)
                } else {
                    // Finished or discarded: the cover closes itself.
                    Color.clear.onAppear { router.showWorkout = false }
                }
            }
            .screen()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { router.showWorkout = false } label: { Image(systemName: "chevron.down") }
                        .accessibilityLabel(L("Minimieren", "Minimise"))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Beenden", "Finish")) { confirmFinish = true }.fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $picking) { ExercisePicker { store.addExercise($0) } }
            .sheet(item: $detail) { e in NavigationStack { ExerciseDetailView(exercise: e) } }
            .confirmationDialog(L("Training beenden?", "Finish workout?"), isPresented: $confirmFinish, titleVisibility: .visible) {
                Button(L("Speichern und beenden", "Save and finish")) {
                    store.finishWorkout()
                    router.showWorkout = false
                    router.tab = .progress
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
                    .buttonStyle(.pillOutline)
                    .padding(.top, 4)
            }
            .padding(16)
            .padding(.bottom, w.restEndsAt != nil ? 90 : 0)
        }
        .scrollDismissesKeyboard(.interactively)
        .overlay(alignment: .bottom) { RestBar() }
        .navigationTitle(w.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func header(_ w: Workout) -> some View {
        Card {
            HStack {
                TimelineView(.periodic(from: .now, by: 1)) { _ in
                    StatTile(value: Fmt.clock(Int((nowMs() - w.startedAt) / 1000)), label: L("Dauer", "Duration"))
                }
                StatTile(value: "\(w.doneSets.count)", label: L("Sätze", "Sets"))
                StatTile(value: Fmt.num(w.volume, digits: 0) + " kg", label: L("Volumen", "Volume"))
            }
        }
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
        Card(padding: 14) {
            HStack(spacing: 12) {
                ExerciseThumb(exercise: ex, size: 48)
                    .onTapGesture { if let ex { onInfo(ex) } }
                VStack(alignment: .leading, spacing: 2) {
                    Text(ex?.title ?? entry.exerciseId).font(.headline).foregroundStyle(Ink.text).lineLimit(2)
                    if !last.isEmpty {
                        Text(L("Letztes Mal: ", "Last time: ") + last.map { "\(Fmt.num($0.weight))×\($0.reps)" }.joined(separator: ", "))
                            .font(.caption).foregroundStyle(Ink.muted).lineLimit(1)
                    }
                }
                Spacer()
                Menu {
                    if let ex { Button { onInfo(ex) } label: { Label(L("Anleitung", "How to"), systemImage: "info.circle") } }
                    Menu(L("Pause: \(Fmt.clock(entry.restSec))", "Rest: \(Fmt.clock(entry.restSec))")) {
                        ForEach([0, 30, 45, 60, 90, 120, 150, 180, 240], id: \.self) { s in
                            Button(s == 0 ? L("Keine", "None") : Fmt.clock(s)) { store.setRest(index, seconds: s) }
                        }
                    }
                    Button(role: .destructive) { store.removeEntry(index) } label: { Label(L("Übung entfernen", "Remove exercise"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(Ink.muted).frame(width: 36, height: 36)
                }
            }
            HStack {
                Text(L("Satz", "Set")).frame(width: 34, alignment: .leading)
                if ex?.bodyweight != true { Text("kg").frame(maxWidth: .infinity) }
                Text(L("Wdh.", "Reps")).frame(maxWidth: .infinity)
                Spacer().frame(width: 44)
            }
            .font(.caption.weight(.semibold)).foregroundStyle(Ink.faint)
            ForEach(Array(entry.sets.enumerated()), id: \.offset) { si, s in
                // Working sets count from 1; warm-ups show a W and are skipped.
                let number = entry.sets.prefix(si + 1).filter { !$0.warmup }.count
                SetRow(entry: index, set: si, value: s, label: s.warmup ? "W" : "\(number)", bodyweight: ex?.bodyweight == true)
                    .contextMenu {
                        Button { store.setSet(index, si) { $0.warmup.toggle() } } label: {
                            Label(s.warmup ? L("Arbeitssatz", "Working set") : L("Aufwärmsatz", "Warm-up set"), systemImage: "flame")
                        }
                        Button(role: .destructive) { store.removeSet(index, si) } label: { Label(L("Satz löschen", "Delete set"), systemImage: "trash") }
                    }
            }
            Button { store.addSet(index) } label: { Label(L("Satz", "Set"), systemImage: "plus").font(.subheadline.weight(.semibold)) }
                .buttonStyle(.plain).foregroundStyle(Ink.accent).padding(.top, 2)
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
            Text(label).font(.system(.subheadline, design: .rounded).weight(.bold))
                .foregroundStyle(value.warmup ? Ink.coral : Ink.muted).frame(width: 34, alignment: .leading)
            if !bodyweight {
                NumberField(
                    text: Fmt.num(value.weight, digits: 2),
                    minus: { store.setSet(entry, set) { $0.weight = max(0, $0.weight - step) } },
                    plus: { store.setSet(entry, set) { $0.weight += step } },
                    commit: { v in store.setSet(entry, set) { $0.weight = max(0, v) } }
                )
            }
            NumberField(
                text: "\(value.reps)",
                minus: { store.setSet(entry, set) { $0.reps = max(0, $0.reps - 1) } },
                plus: { store.setSet(entry, set) { $0.reps += 1 } },
                commit: { v in store.setSet(entry, set) { $0.reps = max(0, Int(v.rounded())) } }
            )
            Button { store.toggleDone(entry, set) } label: {
                Image(systemName: "checkmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(value.done ? Ink.bg : Ink.muted)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(value.done ? Ink.accent : Ink.raised))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(value.done ? L("Satz offen", "Mark set open") : L("Satz erledigt", "Mark set done"))
            .sensoryFeedback(.success, trigger: value.done) { _, now in now }
        }
        .padding(.vertical, 2)
        .opacity(value.done ? 0.75 : 1)
    }
}

/** A value with minus and plus; tapping the number edits it with the keyboard. */
private struct NumberField: View {
    let text: String
    let minus: () -> Void
    let plus: () -> Void
    let commit: (Double) -> Void
    @State private var editing = false
    @State private var draft = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 0) {
            Button(action: minus) { Image(systemName: "minus").frame(width: 28, height: 36) }
            Group {
                if editing {
                    TextField("", text: $draft)
                        .keyboardType(.decimalPad)
                        .focused($focused)
                        .onSubmit(save)
                        .onChange(of: focused) { _, f in if !f { save() } }
                } else {
                    Text(text).onTapGesture {
                        draft = text
                        editing = true
                        focused = true
                    }
                }
            }
            .font(.system(.body, design: .rounded).weight(.semibold).monospacedDigit())
            .multilineTextAlignment(.center)
            .foregroundStyle(Ink.text)
            .frame(maxWidth: .infinity)
            Button(action: plus) { Image(systemName: "plus").frame(width: 28, height: 36) }
        }
        .buttonStyle(.plain)
        .foregroundStyle(Ink.muted)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Ink.raised))
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
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(left > 0 ? L("Pause", "Rest") : L("Weiter geht's", "Go"))
                            .font(.caption.weight(.semibold)).foregroundStyle(Ink.mauve)
                        Text(Fmt.clock(left)).font(.system(.title, design: .rounded).weight(.bold).monospacedDigit()).foregroundStyle(Ink.text)
                        Meter(fraction: Double(left) / Double(total), color: Ink.mauve)
                    }
                    Button("-15") { store.shiftRest(-15) }.buttonStyle(PillButtonStyle(filled: false, color: Ink.text))
                    Button("+15") { store.shiftRest(15) }.buttonStyle(PillButtonStyle(filled: false, color: Ink.text))
                    Button { store.skipRest() } label: { Image(systemName: "forward.end.fill") }
                        .buttonStyle(PillButtonStyle(color: Ink.mauve))
                        .accessibilityLabel(L("Pause überspringen", "Skip rest"))
                }
                .padding(16)
                .background(Ink.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Ink.line, lineWidth: 1))
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("restBar")
                .onChange(of: left <= 0) { _, over in
                    // Clears the finished timer a few seconds after it ran out.
                    if over { Task { try? await Task.sleep(for: .seconds(4)); if (store.data.active?.restEndsAt ?? 0) <= nowMs() { store.skipRest() } } }
                }
            }
        }
    }
}
