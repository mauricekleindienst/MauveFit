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
                    HStack(spacing: 10) {
                        Button {
                            store.startWorkout(nil)
                            router.showWorkout = true
                        } label: {
                            shortcut(L("Freies Training", "Free workout"), L("Ohne Plan starten", "Start without a plan"), "play.fill")
                        }
                        .buttonStyle(.plain)
                        .disabled(d.active != nil)
                        NavigationLink { LibraryView() } label: {
                            shortcut(L("Übungen", "Exercises"), L("\(ExerciseRepo.shared.all.count) mit Anleitung", "\(ExerciseRepo.shared.all.count) with how-to"), "books.vertical.fill")
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("library")
                    }
                    SectionTitle(text: L("Deine Pläne", "Your plans"))
                    if d.routines.isEmpty {
                        Card { Text(L("Noch keine Pläne. Lege oben rechts einen an.", "No plans yet. Add one at the top right.")).font(.fit(.body)).foregroundStyle(Ink.muted) }
                    }
                    ForEach(d.routines) { r in routineCard(r, running: d.active) }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
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

    /** Icon in an accent disc, title and one line below, in an accent disc. */
    private func shortcut(_ title: String, _ sub: String, _ icon: String) -> some View {
        Card(padding: 16, spacing: 0) {
            Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(Ink.accent)
                .frame(width: 40, height: 40).background(Circle().fill(Ink.accent.opacity(0.16)))
            Spacer().frame(height: 14)
            Text(title).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(1)
            Text(sub).font(.fit(.bodySmall)).foregroundStyle(Ink.muted).lineLimit(1)
        }
    }

    private func routineCard(_ r: Routine, running: Workout?) -> some View {
        Card {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(r.name.isEmpty ? L("Ohne Namen", "Untitled") : r.name)
                        .font(.fit(.headlineSmall)).foregroundStyle(Ink.text)
                    Text([Fmt.exercises(r.exercises.count), Fmt.sets(r.exercises.reduce(0) { $0 + $1.sets }), r.note].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
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
                    Image(systemName: "ellipsis").font(.system(size: 16, weight: .semibold)).foregroundStyle(Ink.text)
                        .frame(width: 40, height: 40).background(Circle().fill(Ink.raised))
                }
                .accessibilityLabel(L("Mehr", "More"))
            }
            if !r.exercises.isEmpty {
                HStack(spacing: 8) {
                    ForEach(r.exercises.prefix(5)) { p in ExerciseThumb(exercise: ExerciseRepo.shared[p.exerciseId], size: 44) }
                    if r.exercises.count > 5 {
                        Text("+\(r.exercises.count - 5)").font(.fit(.label)).foregroundStyle(Ink.muted)
                            .frame(width: 44, height: 44).background(Ink.raised, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                Text(r.exercises.compactMap { ExerciseRepo.shared[$0.exerciseId]?.title }.joined(separator: " · "))
                    .font(.fit(.bodySmall)).foregroundStyle(Ink.faint).lineLimit(2)
            }
            if running?.routineId == r.id {
                Button(L("Fortsetzen", "Resume")) { router.showWorkout = true }.buttonStyle(.primary)
            } else {
                Button {
                    store.startWorkout(r)
                    router.showWorkout = true
                } label: { Label(L("Starten", "Start"), systemImage: "play.fill") }
                    .buttonStyle(.primary)
                    .disabled(running != nil || r.exercises.isEmpty)
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
                    TextField(L("Name", "Name"), text: $routine.name).font(.fit(.titleMedium))
                    TextField(L("Notiz", "Note"), text: $routine.note).font(.fit(.body))
                }
                .listRowBackground(Ink.surface)
                Section {
                    ForEach($routine.exercises) { $p in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 12) {
                                ExerciseThumb(exercise: ExerciseRepo.shared[p.exerciseId], size: 44)
                                Text(ExerciseRepo.shared[p.exerciseId]?.title ?? p.exerciseId).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                            }
                            HStack(spacing: 8) {
                                StepperBox(label: L("Sätze", "Sets"), value: "\(p.sets)", minus: { p.sets = max(1, p.sets - 1) }, plus: { p.sets = min(10, p.sets + 1) })
                                StepperBox(label: L("Wdh.", "Reps"), value: "\(p.reps)", minus: { p.reps = max(1, p.reps - 1) }, plus: { p.reps = min(50, p.reps + 1) })
                                StepperBox(label: L("Pause", "Rest"), value: Fmt.clock(p.restSec), minus: { p.restSec = max(0, p.restSec - 15) }, plus: { p.restSec = min(600, p.restSec + 15) })
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onDelete { routine.exercises.remove(atOffsets: $0) }
                    .onMove { routine.exercises.move(fromOffsets: $0, toOffset: $1) }
                    Button { picking = true } label: { Label(L("Übung hinzufügen", "Add exercise"), systemImage: "plus").font(.fit(.titleMedium)) }
                } header: {
                    Text(Fmt.exercises(routine.exercises.count)).font(.fit(.titleSmall)).foregroundStyle(Ink.muted).textCase(nil)
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
                    .fontWeight(.semibold)
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

/** "− value +" with a caption, for plan targets. */
struct StepperBox: View {
    let label: String
    let value: String
    let minus: () -> Void
    let plus: () -> Void

    var body: some View {
        VStack(spacing: 4) {
            Text(label).font(.fit(.labelSmall)).foregroundStyle(Ink.muted).lineLimit(1)
            Text(value).font(.fit(.titleLarge)).foregroundStyle(Ink.text).lineLimit(1).minimumScaleFactor(0.7)
            HStack(spacing: 6) {
                step("minus", L("Weniger", "Less"), minus)
                step("plus", L("Mehr", "More"), plus)
            }
        }
        .padding(.top, 10).padding(.bottom, 8).padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
        .background(Ink.raised, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func step(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 14, weight: .bold)).foregroundStyle(Ink.text)
                .frame(maxWidth: .infinity).frame(height: 34)
                .background(Capsule().fill(Ink.line))
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(label)
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
        ExerciseList(onPick: nil)
            .navigationTitle(L("Übungen", "Exercises"))
            .navigationBarTitleDisplayMode(.inline)
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
        let found = repo.search(query, category: category, favorites: store.data.favorites, favoritesOnly: favoritesOnly)
        List {
            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Chip(text: L("Favoriten", "Favourites"), on: favoritesOnly, icon: "star.fill") { favoritesOnly.toggle() }
                        Chip(text: L("Alle", "All"), on: category == nil) { category = nil }
                        ForEach(repo.categories, id: \.self) { c in
                            Chip(text: Labels.category(c), on: category == c) { category = category == c ? nil : c }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
            }
            Section {
                ForEach(found.prefix(300)) { e in
                    if let onPick {
                        Button { onPick(e) } label: { row(e) }
                    } else {
                        NavigationLink { ExerciseDetailView(exercise: e) } label: { row(e) }
                            .accessibilityIdentifier("exercise-\(e.id)")
                    }
                }
            } header: {
                Text(Fmt.exercises(found.count)).font(.fit(.titleSmall)).foregroundStyle(Ink.muted).textCase(nil)
            }
            .listRowBackground(Ink.surface)
        }
        .screen()
        .searchable(text: $query, prompt: L("Übung, Muskel oder Gerät", "Exercise, muscle or equipment"))
    }

    private func row(_ e: Exercise) -> some View {
        HStack(spacing: 12) {
            ExerciseThumb(exercise: e, size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(e.title).font(.fit(.titleMedium)).foregroundStyle(Ink.text).lineLimit(2)
                Text("\(Labels.muscle(e.target)) · \(Labels.equipment(e.equipment))").font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
            }
            Spacer()
            if store.data.favorites.contains(e.id) { Image(systemName: "star.fill").font(.caption).foregroundStyle(Ink.accent) }
        }
    }
}

/** The how-to of one exercise: still, muscles, equipment, the best set and steps. */
struct ExerciseDetailView: View {
    @Environment(Store.self) private var store
    let exercise: Exercise

    var body: some View {
        let fav = store.data.favorites.contains(exercise.id)
        let record = Stats.records(store.data.history).first { $0.exerciseId == exercise.id }
        let last = Stats.lastSets(store.data.history, exercise.id)
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ExerciseMedia(exercise: exercise, radius: Radius.panel)
                    .aspectRatio(1, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                Text(Exercise.attribution).font(.fit(.labelSmall)).foregroundStyle(Ink.faint)
                Text(exercise.title).font(.fit(.headline)).foregroundStyle(Ink.text)
                HStack(spacing: 6) {
                    Tag(text: Labels.muscle(exercise.target), color: Ink.accent)
                    Tag(text: Labels.equipment(exercise.equipment))
                    Tag(text: Labels.category(exercise.category))
                }
                if !exercise.secondary.isEmpty {
                    Text(L("Mit dabei: ", "Also works: ") + exercise.secondary.map(Labels.muscle).joined(separator: ", "))
                        .font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
                }
                if let record {
                    HStack(spacing: 10) {
                        StatTile(value: Fmt.set(record.best, bodyweight: exercise.bodyweight), label: L("Bester Satz", "Best set"), color: Ink.accent)
                        if record.best.weight > 0 {
                            StatTile(value: Fmt.kg(record.e1rm.rounded()) + " kg", label: L("Geschätztes 1RM", "Estimated 1RM"))
                        }
                    }
                }
                if !last.isEmpty {
                    Card(padding: 16) {
                        SectionTitle(text: L("Letztes Mal", "Last time")).padding(.top, -10)
                        Text(last.map { Fmt.set($0, bodyweight: exercise.bodyweight) }.joined(separator: "   "))
                            .font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                    }
                }
                SectionTitle(text: L("Ausführung", "How to"))
                ForEach(Array(ExerciseRepo.shared.steps(exercise).enumerated()), id: \.offset) { i, s in
                    HStack(alignment: .top, spacing: 12) {
                        Text("\(i + 1)").font(.fit(.label)).foregroundStyle(Ink.bg)
                            .frame(width: 26, height: 26).background(Circle().fill(Ink.accent))
                        Text(s).font(.fit(.body)).foregroundStyle(Ink.text).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .screen()
        .accessibilityIdentifier("exerciseDetail")
        .navigationTitle(exercise.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { store.toggleFavorite(exercise.id) } label: { Image(systemName: fav ? "star.fill" : "star") }
                    .accessibilityLabel(fav ? L("Kein Favorit mehr", "Remove favourite") : L("Als Favorit", "Add favourite"))
            }
        }
    }
}
