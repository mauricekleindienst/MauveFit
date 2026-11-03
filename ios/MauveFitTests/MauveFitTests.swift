import XCTest
@testable import MauveFit

@MainActor
final class MauveFitTests: XCTestCase {
    private var dir: URL!

    override func setUp() async throws {
        dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        I18n.apply("en")
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: dir)
    }

    func testExerciseDatabaseIsBundled() {
        let repo = ExerciseRepo.shared
        XCTAssertGreaterThan(repo.all.count, 1000)
        XCTAssertEqual(repo["0025"]?.name, "barbell bench press")
        XCTAssertFalse(repo.namesDe.isEmpty)
        // Every exercise of the starter plans exists in the database.
        for r in Templates.routines { for p in r.exercises { XCTAssertNotNil(repo[p.exerciseId], p.exerciseId) } }
    }

    func testGermanNamesAndSteps() {
        I18n.apply("de")
        defer { I18n.apply("en") }
        let e = ExerciseRepo.shared["0001"]!
        XCTAssertEqual(e.title, "3/4 Sit-up")
        XCTAssertTrue(ExerciseRepo.shared.steps(e).first?.hasPrefix("Lege dich") ?? false)
        XCTAssertEqual(Labels.muscle("pectorals"), "Brust")
    }

    /** A data.json as the Android app writes it, with fields this app does not know. */
    func testReadsAndroidData() throws {
        let json = """
        {"routines":[{"id":"r1","name":"Push","note":"","exercises":[{"exerciseId":"0025","sets":4,"reps":8,"restSec":150,"superset":null}]}],
         "history":[{"id":"w1","name":"Push","routineId":"r1","startedAt":1760000000000,"finishedAt":1760003600000,
           "entries":[{"exerciseId":"0025","restSec":150,"sets":[{"weight":80.0,"reps":8,"done":true,"warmup":false,"target":8,"type":"Drop","rpe":0}],"load":1.0,"fullTop":0.0}],
           "readinessCut":0,"lastSet":null}],
         "settings":{"weeklyGoal":4,"accent":"sage","language":"de","unknown":true},
         "profile":{"goal":"Strength","kit":["machines"]},
         "coins":120,"quests":[]}
        """
        let file = dir.appendingPathComponent("data.json")
        try Data(json.utf8).write(to: file)
        let store = Store(file: file)
        XCTAssertEqual(store.data.routines.first?.exercises.first?.sets, 4)
        XCTAssertEqual(store.data.history.first?.volume, 640)
        XCTAssertEqual(store.data.history.first?.entries.first?.sets.first?.type, .Drop)
        XCTAssertEqual(store.data.settings.weeklyGoal, 4)
        XCTAssertEqual(store.data.profile.goal, .Strength)
        I18n.apply("en")
    }

    func testBrokenFileFallsBackToPreviousCopy() throws {
        let file = dir.appendingPathComponent("data.json")
        let first = Store(file: file)
        first.updateSettings { $0.weeklyGoal = 5 }
        first.flush()
        _ = Store(file: file) // reads it and keeps a known-good copy
        try Data("{not json".utf8).write(to: file)
        let store = Store(file: file)
        XCTAssertEqual(store.data.settings.weeklyGoal, 5)
    }

    func testRecordsAWorkout() throws {
        let file = dir.appendingPathComponent("data.json")
        let store = Store(file: file)
        let plan = store.data.routines.first!
        store.startWorkout(plan)
        XCTAssertEqual(store.data.active?.entries.count, plan.exercises.count)
        store.setSet(0, 0) { $0.weight = 60; $0.reps = 8 }
        store.toggleDone(0, 0)
        XCTAssertNotNil(store.data.active?.restEndsAt)
        store.addSet(0)
        XCTAssertEqual(store.data.active?.entries[0].sets.count, plan.exercises[0].sets + 1)
        let id = store.finishWorkout()
        XCTAssertNotNil(id)
        XCTAssertNil(store.data.active)
        let w = try XCTUnwrap(store.data.history.first)
        // Only the ticked set is kept, and only the exercise it belongs to.
        XCTAssertEqual(w.entries.count, 1)
        XCTAssertEqual(w.volume, 480)
        store.flush()
        XCTAssertEqual(Store(file: file).data.history.first?.id, id)
    }

    func testEmptyWorkoutIsNotSaved() {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        store.startWorkout(nil)
        XCTAssertNil(store.finishWorkout())
        XCTAssertTrue(store.data.history.isEmpty)
    }

    func testNextWorkoutSuggestsOneStepMore() {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        let plan = Routine(name: "A", exercises: [PlanExercise(exerciseId: "0025", sets: 2, reps: 8)])
        store.startWorkout(plan)
        for i in 0..<2 {
            store.setSet(0, i) { $0.weight = 80; $0.reps = 8 }
            store.toggleDone(0, i)
        }
        store.finishWorkout()
        store.startWorkout(plan)
        XCTAssertEqual(store.data.active?.entries.first?.sets.first?.weight, 82.5)
    }

    func testStats() {
        XCTAssertEqual(Stats.e1rm(SetEntry(weight: 100, reps: 1)), 100)
        XCTAssertEqual(Stats.e1rm(SetEntry(weight: 90, reps: 10)), 120, accuracy: 0.001)
        var w = Workout(name: "x")
        w.startedAt = nowMs()
        XCTAssertEqual(Stats.workoutsThisWeek([w]), 1)
        XCTAssertEqual(Stats.weekStreak([w], goal: 1), 1)
    }

    func testNutritionTargets() {
        let t = Nutrition.targets(kg: 80, goal: .Muscle)
        XCTAssertEqual(t.kcal, 2890)
        XCTAssertEqual(t.protein, 160)
        XCTAssertEqual(t.fat, 72)
        XCTAssertEqual(t.waterMl, 2750)
    }
}
