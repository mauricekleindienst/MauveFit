import SwiftUI

/** The plans tab: start, edit and add plans, or browse the exercise library. */
struct TrainingView: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router
    @State private var editing: Routine?

    var body: some View {
        let d = store.data
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    HStack {
                        Button {
                            store.startWorkout(nil)
                            router.showWorkout = true
                        } label: { Label(L("Freies Training", "Free workout"), systemImage: "play.fill") }
                            .buttonStyle(.pillOutline)
                            .disabled(d.active != nil)
                        NavigationLink { LibraryView() } label: { Label(L("Übungen", "Exercises"), systemImage: "books.vertical") }
                            .buttonStyle(.pillOutline)
                            .accessibilityIdentifier("library")
                        Spacer()
                    }
                    SectionTitle(text: L("Pläne", "Plans"))
                    if d.routines.isEmpty {
                        Card { Text(L("Noch keine Pläne. Lege oben rechts einen an.", "No plans yet. Add one at the top right.")).foregroundStyle(Ink.muted) }
                    }
                    ForEach(d.routines) { r in routineCard(r, running: d.active) }
                }
                .padding(16)
            }
            .screen()
            .navigationTitle(L("Training", "Training"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editing = Routine(name: "") } label: { Image(systemName: "plus") }
                        .accessibilityLabel(L("Neuer Plan", "New plan"))
                }
            }
            .sheet(item: $editing) { r in RoutineEditor(routine: r) }
        }
    }

    private func routineCard(_ r: Routine, running: Workout?) -> some View {
        Card {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(r.name.isEmpty ? L("Ohne Namen", "Untitled") : r.name)
                        .font(.system(.title3, design: .rounded).weight(.bold)).foregroundStyle(Ink.text)
                    if !r.note.isEmpty { Text(r.note).font(.subheadline).foregroundStyle(Ink.muted) }
                }
                Spacer()
                Menu {
                    Button { editing = r } label: { Label(L("Bearbeiten", "Edit"), systemImage: "pencil") }
                    Button {
                        var copy = r
                        copy.id = newId()
                        copy.name = r.name + L(" (Kopie)", " (copy)")
                        store.saveRoutine(copy)
                    } label: { Label(L("Duplizieren", "Duplicate"), systemImage: "plus.square.on.square") }
                    Button(role: .destructive) { store.deleteRoutine(r.id) } label: { Label(L("Löschen", "Delete"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(Ink.muted).frame(width: 36, height: 36)
                }
            }
            Text(r.exercises.compactMap { ExerciseRepo.shared[$0.exerciseId]?.title }.joined(separator: " · "))
                .font(.footnote).foregroundStyle(Ink.faint).lineLimit(2)
            HStack {
                Text(L("\(r.exercises.count) Übungen · \(r.exercises.reduce(0) { $0 + $1.sets }) Sätze",
                       "\(r.exercises.count) exercises · \(r.exercises.reduce(0) { $0 + $1.sets }) sets"))
                    .font(.caption).foregroundStyle(Ink.muted)
                Spacer()
                if running?.routineId == r.id {
                    Button(L("Fortsetzen", "Resume")) { router.showWorkout = true }.buttonStyle(.pill)
                } else {
                    Button(L("Starten", "Start")) {
                        store.startWorkout(r)
                        router.showWorkout = true
                    }
                    .buttonStyle(.pill)
                    .disabled(running != nil || r.exercises.isEmpty)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { editing = r }
    }
}

/** Edits a plan in a sheet; nothing is saved until "Save". */
struct RoutineEditor: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State var routine: Routine
    @State private var picking = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField(L("Name", "Name"), text: $routine.name)
                    TextField(L("Notiz", "Note"), text: $routine.note)
                }
                .listRowBackground(Ink.surface)
                Section(L("Übungen", "Exercises")) {
                    ForEach($routine.exercises) { $p in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                ExerciseThumb(exercise: ExerciseRepo.shared[p.exerciseId], size: 40)
                                Text(ExerciseRepo.shared[p.exerciseId]?.title ?? p.exerciseId).foregroundStyle(Ink.text)
                            }
                            Stepper(L("\(p.sets) Sätze", "\(p.sets) sets"), value: $p.sets, in: 1...10)
                            Stepper(L("\(p.reps) Wdh.", "\(p.reps) reps"), value: $p.reps, in: 1...50)
                            Stepper(L("Pause \(Fmt.clock(p.restSec))", "Rest \(Fmt.clock(p.restSec))"), value: $p.restSec, in: 0...600, step: 15)
                        }
                        .foregroundStyle(Ink.muted)
                    }
                    .onDelete { routine.exercises.remove(atOffsets: $0) }
                    .onMove { routine.exercises.move(fromOffsets: $0, toOffset: $1) }
                    Button { picking = true } label: { Label(L("Übung hinzufügen", "Add exercise"), systemImage: "plus") }
                }
                .listRowBackground(Ink.surface)
            }
            .screen()
            .navigationTitle(routine.name.isEmpty ? L("Neuer Plan", "New plan") : routine.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Speichern", "Save")) {
                        if routine.name.trimmingCharacters(in: .whitespaces).isEmpty { routine.name = L("Mein Plan", "My plan") }
                        store.saveRoutine(routine)
                        dismiss()
                    }
                }
                ToolbarItem(placement: .bottomBar) { EditButton() }
            }
            .sheet(isPresented: $picking) {
                ExercisePicker { id in
                    routine.exercises.append(PlanExercise(exerciseId: id, restSec: store.data.settings.defaultRest))
                }
            }
        }
    }
}

/** Searchable list of the whole database; [onPick] gets the chosen exercise id. */
struct ExercisePicker: View {
    @Environment(\.dismiss) private var dismiss
    let onPick: (String) -> Void

    var body: some View {
        NavigationStack {
            ExerciseList { e in
                onPick(e.id)
                dismiss()
            }
            .navigationTitle(L("Übung wählen", "Choose exercise"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("Abbrechen", "Cancel")) { dismiss() } } }
        }
    }
}

/** Browses the database; tapping opens the how-to. */
struct LibraryView: View {
    var body: some View {
        ExerciseList(onPick: nil).navigationTitle(L("Übungen", "Exercises"))
    }
}

/** The filtered list behind the picker and the library. */
struct ExerciseList: View {
    @Environment(Store.self) private var store
    let onPick: ((Exercise) -> Void)?
    @State private var query = ""
    @State private var category: String?
    @State private var favoritesOnly = false

    var body: some View {
        let repo = ExerciseRepo.shared
        let found = repo.search(query, category: category).filter { !favoritesOnly || store.data.favorites.contains($0.id) }
        List {
            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip(L("Favoriten", "Favourites"), on: favoritesOnly, icon: "star.fill") { favoritesOnly.toggle() }
                        chip(L("Alle", "All"), on: category == nil) { category = nil }
                        ForEach(repo.categories, id: \.self) { c in
                            chip(Labels.category(c), on: category == c) { category = category == c ? nil : c }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
            }
            Section(L("\(found.count) Übungen", "\(found.count) exercises")) {
                ForEach(found.prefix(300)) { e in
                    if let onPick {
                        Button { onPick(e) } label: { row(e) }
                    } else {
                        NavigationLink { ExerciseDetailView(exercise: e) } label: { row(e) }
                    }
                }
            }
            .listRowBackground(Ink.surface)
        }
        .screen()
        .searchable(text: $query, prompt: L("Übung oder Muskel suchen", "Search exercise or muscle"))
    }

    private func row(_ e: Exercise) -> some View {
        HStack(spacing: 12) {
            ExerciseThumb(exercise: e, size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(e.title).foregroundStyle(Ink.text).lineLimit(2)
                Text("\(Labels.muscle(e.target)) · \(Labels.equipment(e.equipment))").font(.caption).foregroundStyle(Ink.muted)
            }
            Spacer()
            if store.data.favorites.contains(e.id) { Image(systemName: "star.fill").font(.caption).foregroundStyle(Ink.accent) }
        }
    }

    private func chip(_ text: String, on: Bool, icon: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { Image(systemName: icon).font(.caption) }
                Text(text)
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 14).padding(.vertical, 8)
            .foregroundStyle(on ? Ink.bg : Ink.text)
            .background(Capsule().fill(on ? Ink.accent : Ink.raised))
        }
        .buttonStyle(.plain)
    }
}

/** The how-to of one exercise: still, muscles, equipment and steps. */
struct ExerciseDetailView: View {
    @Environment(Store.self) private var store
    let exercise: Exercise

    var body: some View {
        let fav = store.data.favorites.contains(exercise.id)
        let best = Stats.records(store.data.history).first { $0.0 == exercise.id }?.1
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AsyncImage(url: exercise.imageUrl) { phase in
                    if let img = phase.image { img.resizable().scaledToFit() } else { ProgressView().frame(height: 240) }
                }
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                Text(Exercise.attribution).font(.caption2).foregroundStyle(Ink.faint)
                Card {
                    HStack {
                        StatTile(value: Labels.muscle(exercise.target), label: L("Zielmuskel", "Target"))
                        StatTile(value: Labels.equipment(exercise.equipment), label: L("Gerät", "Equipment"))
                    }
                    if !exercise.secondary.isEmpty {
                        Text(L("Mit dabei: ", "Also works: ") + exercise.secondary.map(Labels.muscle).joined(separator: ", "))
                            .font(.footnote).foregroundStyle(Ink.muted)
                    }
                    if let best {
                        Text(L("Bestwert (geschätztes 1RM): \(Fmt.num(best)) kg", "Best (estimated 1RM): \(Fmt.num(best)) kg"))
                            .font(.footnote.weight(.semibold)).foregroundStyle(Ink.accent)
                    }
                }
                SectionTitle(text: L("Ausführung", "How to"))
                ForEach(Array(ExerciseRepo.shared.steps(exercise).enumerated()), id: \.offset) { i, s in
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(i + 1)").font(.system(.subheadline, design: .rounded).weight(.bold)).foregroundStyle(Ink.accent).frame(width: 20)
                        Text(s).foregroundStyle(Ink.text)
                    }
                }
            }
            .padding(16)
        }
        .screen()
        .navigationTitle(exercise.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { store.toggleFavorite(exercise.id) } label: { Image(systemName: fav ? "star.fill" : "star") }
                    .accessibilityLabel(L("Favorit", "Favourite"))
            }
        }
    }
}
