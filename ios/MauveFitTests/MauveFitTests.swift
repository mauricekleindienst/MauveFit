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

    private func workout(_ name: String, routineId: String? = nil, hoursAgo: Double, _ entries: [ExerciseEntry]) -> Workout {
        var w = Workout(name: name, routineId: routineId, entries: entries)
        w.startedAt = nowMs() - Int64(hoursAgo * 3_600_000)
        w.finishedAt = w.startedAt + 3_600_000
        return w
    }

    private func entry(_ id: String, _ sets: [(Double, Int)], target: Int = 0) -> ExerciseEntry {
        ExerciseEntry(exerciseId: id, sets: sets.map { SetEntry(weight: $0.0, reps: $0.1, done: true, target: target) })
    }

    func testStreakWithZeroGoalEnds() {
        var w = Workout(name: "x")
        w.startedAt = nowMs()
        XCTAssertEqual(Stats.weekStreak([w], goal: 0), 1)
    }

    /** As on Android: any weighted set beats one without weight, and the best set is kept with its date. */
    func testRecordsRankWeightedSetsFirst() {
        let old = workout("A", hoursAgo: 48, [entry("0025", [(0, 30)])])
        let new = workout("B", hoursAgo: 2, [entry("0025", [(20, 8), (15, 12)]), entry("0652", [(0, 12)])])
        let records = Stats.records([new, old])
        let bench = try! XCTUnwrap(records.first { $0.exerciseId == "0025" })
        XCTAssertEqual(bench.best.weight, 20)
        XCTAssertEqual(bench.at, new.startedAt)
        XCTAssertEqual(records.first { $0.exerciseId == "0652" }?.best.reps, 12)
    }

    func testFirstTimeIsNoRecordButBeatingItIs() {
        let first = workout("A", hoursAgo: 48, [entry("0025", [(60, 8)])])
        XCTAssertTrue(Stats.newRecords(first, history: [first]).isEmpty)
        let second = workout("B", hoursAgo: 1, [entry("0025", [(62.5, 8)])])
        XCTAssertEqual(Stats.newRecords(second, history: [second, first]).map(\.0), ["0025"])
        let same = workout("C", hoursAgo: 0.5, [entry("0025", [(62.5, 8)])])
        XCTAssertTrue(Stats.newRecords(same, history: [same, second, first]).isEmpty)
    }

    /** The plan whose muscles rested longest comes next, not simply the oldest plan. */
    func testNextPlanFollowsRecovery() {
        var d = AppData()
        d.routines = [
            Routine(id: "chest", name: "Chest", exercises: [PlanExercise(exerciseId: "0025")]),
            Routine(id: "legs", name: "Legs", exercises: [PlanExercise(exerciseId: "0043")]),
        ]
        XCTAssertEqual(Stats.nextRoutine(d)?.id, "chest")
        d.history = [workout("Chest", routineId: "chest", hoursAgo: 5, [entry("0025", [(60, 8)])])]
        XCTAssertEqual(Stats.nextRoutine(d)?.id, "legs")
        // A free workout that trained the legs does not count as the legs plan, but its muscles do.
        d.history.insert(workout("Free", hoursAgo: 1, [entry("0043", [(80, 5)])]), at: 0)
        XCTAssertEqual(Stats.nextRoutine(d)?.id, "chest")
    }

    func testGermanAndEnglishFormatting() {
        I18n.apply("de")
        XCTAssertEqual(Fmt.kg(82.5), "82,5")
        XCTAssertEqual(Fmt.kg(1.25), "1,25")
        XCTAssertEqual(Fmt.kg(80), "80")
        XCTAssertEqual(Fmt.kg(81.37), "81,4")
        XCTAssertEqual(Fmt.volume(12_345), "12,3 t")
        XCTAssertEqual(Fmt.volume(1340), "1340 kg")
        XCTAssertEqual(Fmt.sets(1), "1 Satz")
        XCTAssertEqual(Fmt.sets(3), "3 Sätze")
        XCTAssertEqual(Fmt.exercises(1), "1 Übung")
        XCTAssertEqual(Fmt.set(SetEntry(weight: 62.5, reps: 8), bodyweight: false), "62,5×8")
        XCTAssertEqual(Fmt.set(SetEntry(weight: 0, reps: 12), bodyweight: true), "12 Wdh.")
        I18n.apply("en")
        XCTAssertEqual(Fmt.kg(82.5), "82.5")
        XCTAssertEqual(Fmt.volume(12_000), "12.0 t")
        XCTAssertEqual(Fmt.sets(1), "1 set")
        XCTAssertEqual(Fmt.duration(30_000), "< 1 min")
        XCTAssertEqual(Fmt.duration(65 * 60_000), "1 h 5 min")
        // Body-weight training shows reps, not "0 kg".
        let w = workout("Home", hoursAgo: 1, [entry("0652", [(0, 12), (0, 10)])])
        XCTAssertEqual(Stats.headline(w), "22 reps")
    }

    func testWaterStaysBetweenZeroAndTenLitres() {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        let day = Stats.isoDay()
        store.addWater(-250, day: day)
        XCTAssertNil(store.data.water[day])
        store.addWater(20_000, day: day)
        XCTAssertEqual(store.data.water[day], 10_000)
        store.addWater(-10_000, day: day)
        XCTAssertNil(store.data.water[day])
    }

    /** A workout left open for hours is saved with its last activity as the end. */
    func testIdleWorkoutIsClosed() throws {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        store.startWorkout(Routine(name: "A", exercises: [PlanExercise(exerciseId: "0025", sets: 2, reps: 8)]))
        store.setSet(0, 0) { $0.weight = 60 }
        store.toggleDone(0, 0)
        XCTAssertFalse(store.closeIdleWorkout())
        let last = nowMs() - 4 * 3_600_000
        store.update { $0.active?.lastActivityAt = last }
        XCTAssertTrue(store.closeIdleWorkout())
        XCTAssertNil(store.data.active)
        let w = try XCTUnwrap(store.data.history.first)
        XCTAssertEqual(w.finishedAt, last + 5 * 60_000)
        XCTAssertEqual(w.doneSets.count, 1)
    }

    /** +15 after the rest ran out counts from now; a finished rest can be cleared by its end time. */
    func testRestTimer() throws {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        store.startWorkout(Routine(name: "A", exercises: [PlanExercise(exerciseId: "0025", sets: 2, reps: 8)]))
        let past = nowMs() - 60_000
        store.update { $0.active?.restEndsAt = past }
        store.shiftRest(15)
        let end = try XCTUnwrap(store.data.active?.restEndsAt)
        XCTAssertGreaterThan(end, nowMs() + 13_000)
        store.endRest(end - 1)
        XCTAssertNotNil(store.data.active?.restEndsAt)
        store.endRest(end)
        XCTAssertNil(store.data.active?.restEndsAt)
        // Unticking the only ticked set takes back the rest it started.
        store.toggleDone(0, 0)
        XCTAssertNotNil(store.data.active?.restEndsAt)
        store.toggleDone(0, 0)
        XCTAssertNil(store.data.active?.restEndsAt)
    }

    func testBodyWeightProgressesByReps() {
        let store = Store(file: dir.appendingPathComponent("data.json"))
        store.update { $0.history = [self.workout("Home", hoursAgo: 30, [self.entry("0652", [(0, 12), (0, 11)], target: 10)])] }
        store.startWorkout(Routine(name: "Home", exercises: [PlanExercise(exerciseId: "0652", sets: 2, reps: 10)]))
        XCTAssertEqual(store.data.active?.entries.first?.sets.first?.reps, 13)
        XCTAssertEqual(store.data.active?.entries.first?.sets.first?.weight, 0)
    }

    /** A lift never logged starts from body weight and level, never below the empty bar. */
    func testFirstWeightFromBodyWeight() {
        var d = AppData()
        d.bodyWeights = [BodyWeight(at: nowMs(), kg: 80)]
        XCTAssertEqual(Coach.startWeight(d, .shared, "0025", reps: 8), 25)
        d.bodyWeights = [BodyWeight(at: nowMs(), kg: 50)]
        XCTAssertEqual(Coach.startWeight(d, .shared, "0025", reps: 8), 20)
        // Unknown equipment and no history: nothing to go on.
        XCTAssertEqual(Coach.startWeight(AppData(), .shared, "0025", reps: 8), 0)
    }

    /** The library sorts by the name in the app's language, favourites first, every word must match. */
    func testLibrarySearch() {
        I18n.apply("de")
        defer { I18n.apply("en") }
        let repo = ExerciseRepo.shared
        let titles = repo.search("").prefix(50).map(\.title)
        let locale = Locale(identifier: "de_DE")
        XCTAssertEqual(titles, titles.sorted { $0.compare($1, options: [.caseInsensitive, .diacriticInsensitive], locale: locale) == .orderedAscending })
        XCTAssertEqual(repo.search("", favorites: ["0652"]).first?.id, "0652")
        let found = repo.search("bankdrücken langhantel")
        XCTAssertTrue(found.contains { $0.id == "0025" })
        XCTAssertTrue(found.allSatisfy { $0.equipment == "barbell" || $0.title.lowercased().contains("langhantel") })
    }

    /** Every muscle in the exercise data maps to map regions, or explicitly to none (cardio). */
    func testEveryMuscleHasARegion() {
        let map = BodyMap.shared
        let slugs = Set(map.front.parts.keys).union(map.back.parts.keys)
        XCTAssertTrue(slugs.isSuperset(of: ["chest", "abs", "quadriceps", "upper-back", "gluteal", "hamstring"]))
        XCTAssertFalse(map.front.outline.isEmpty)
        XCTAssertFalse(map.back.outline.isEmpty)
        for e in ExerciseRepo.shared.all {
            for m in e.muscles { XCTAssertNotNil(MuscleRegions.map[m], "\(m) of \(e.id) has no entry") }
        }
        for region in MuscleRegions.map.values.flatMap({ $0 }) { XCTAssertTrue(slugs.contains(region), region) }
        XCTAssertEqual(MuscleRegions.map["cardiovascular system"], [])
        // Read from the shared muscle_regions.json, not built into the code.
        XCTAssertGreaterThan(MuscleRegions.map.count, 40)
    }

    /** The thumbnail shows the side that matters. */
    func testMuscleMapSide() {
        let repo = ExerciseRepo.shared
        XCTAssertEqual(MuscleRegions.side(for: repo["0025"]!), .front) // bench press: chest
        XCTAssertEqual(MuscleRegions.side(for: repo["0043"]!), .back) // full squat: glutes
        XCTAssertEqual(MuscleRegions.side(for: repo["0652"]!), .back) // pull-up: lats
        XCTAssertEqual(MuscleRegions.side(for: repo["0313"]!), .front) // hammer curl: biceps
    }

    /** Every drawing in the match table is bundled, and belongs to an exercise of the database. */
    func testDrawingsAreBundled() {
        let d = Drawings.shared
        XCTAssertGreaterThan(d.images.count, 150)
        for (id, files) in d.images {
            XCTAssertNotNil(ExerciseRepo.shared[id], id)
            XCTAssertFalse(files.isEmpty, id)
            for f in files { XCTAssertNotNil(d.load(f), f) }
        }
        XCTAssertEqual(d.images["0025"], ["ek-0042-relaxation", "ek-0042-tension"])
        XCTAssertNil(d.images["0001"])
    }

    func testResetStartsOver() {
        let file = dir.appendingPathComponent("data.json")
        let store = Store(file: file)
        store.addWater(500)
        store.deleteRoutine(store.data.routines[0].id)
        store.reset()
        XCTAssertTrue(store.data.water.isEmpty)
        XCTAssertEqual(store.data.routines.map(\.id), Templates.routines.map(\.id))
        XCTAssertEqual(Store(file: file).data.routines.count, Templates.routines.count)
    }

    /** Only finished workouts with a real time span go to Apple Health, and only plausible weights. */
    func testHealthMapping() {
        var w = Workout(name: "Push")
        w.startedAt = 1_760_000_000_000
        XCTAssertNil(HealthMapping.interval(w))
        w.finishedAt = w.startedAt
        XCTAssertNil(HealthMapping.interval(w))
        w.finishedAt = w.startedAt + 3_600_000
        let span = HealthMapping.interval(w)
        XCTAssertEqual(span?.duration, 3600)
        XCTAssertEqual(span?.start, Date(ms: 1_760_000_000_000))
        XCTAssertEqual(HealthMapping.metadata(w)["HKExternalUUID"] as? String, w.id)
        XCTAssertTrue(HealthMapping.validKg(82.5))
        XCTAssertFalse(HealthMapping.validKg(5))
        XCTAssertFalse(HealthMapping.validKg(.nan))
    }

    /** The setting keeps Android's key, and test runs never switch Health on. */
    func testHealthSetting() async throws {
        let json = #"{"settings":{"healthWrite":true}}"#
        let d = try JSONDecoder().decode(AppData.self, from: Data(json.utf8))
        XCTAssertTrue(d.settings.healthWrite)
        XCTAssertFalse(AppData().settings.healthWrite)
        let store = Store(file: dir.appendingPathComponent("data.json"))
        await store.setHealth(true)
        XCTAssertFalse(store.data.settings.healthWrite)
        XCTAssertNil(store.bodyWeight)
        store.logWeight(80)
        XCTAssertEqual(store.bodyWeight, 80)
    }

    // MARK: - Quests, XP and the shop (#11)

    /** The same numbers as Kotlin's Random(seed), taken from kotlin-stdlib, so quests draw as on Android. */
    func testKotlinRandomMatchesKotlin() {
        let bounds = [1, 3, 4, 7, 10, 16, 100]
        let expected: [Int64: [Int]] = [
            0: [0, 0, 3, 3, 2, 2, 61, 0, 0, 3, 0, 4],
            1: [0, 0, 3, 5, 9, 4, 1, 0, 2, 0, 4, 1],
            161_508_005: [0, 1, 3, 0, 6, 3, 4, 0, 2, 3, 1, 6],
            161_508_007: [0, 1, 3, 6, 7, 12, 58, 0, 2, 2, 4, 8],
            -5: [0, 2, 3, 2, 3, 2, 28, 0, 1, 3, 0, 8],
            123_456_789_012: [0, 1, 1, 1, 5, 3, 13, 0, 2, 3, 2, 0],
        ]
        for (seed, values) in expected {
            var r = KotlinRandom(seed: seed)
            XCTAssertEqual((0..<12).map { r.nextInt(bounds[$0 % bounds.count]) }, values, "seed \(seed)")
        }
    }

    func testLevelsAndXp() {
        XCTAssertEqual(Quests.level(0).level, 1)
        XCTAssertEqual(Quests.level(0).need, 150)
        XCTAssertEqual(Quests.level(149).level, 1)
        XCTAssertEqual(Quests.level(150).level, 2)
        XCTAssertEqual(Quests.level(150 + 200).level, 3)
        var w = Workout(name: "x")
        w.entries = [entry("0025", Array(repeating: (60.0, 8), count: 30))]
        XCTAssertEqual(Quests.workoutXp(w), 100) // 40 + capped 60
        XCTAssertEqual(Shop.workoutCoins(w), 50) // 20 + capped 30
        XCTAssertEqual(Shop.skillLevel(9).level, 0)
        XCTAssertEqual(Shop.skillLevel(10).level, 1)
        XCTAssertEqual(Shop.skillLevel(25).level, 2)
    }

    private func day(_ y: Int, _ m: Int, _ d: Int, hour: Int = 18) -> Date {
        Stats.calendar.date(from: DateComponents(year: y, month: m, day: d, hour: hour))!
    }

    /** A workout finished today completes "Finish a workout"; claiming banks it once, with coins. */
    func testClaimBanksQuestsOnce() {
        let today = Stats.calendar.startOfDay(for: day(2026, 11, 4)) // a Wednesday
        var w = workout("Push", hoursAgo: 0, [entry("0025", [(60, 8), (60, 8)])])
        w.startedAt = day(2026, 11, 4).ms
        w.finishedAt = w.startedAt + 3_600_000
        var d = AppData()
        d.history = [w]
        let quests = Quests.daily(d, day: today)
        XCTAssertEqual(quests.count, 3)
        XCTAssertEqual(quests.first?.key, "d:2026-11-04:Train")
        XCTAssertTrue(quests[0].done)
        // The same day draws the same quests.
        XCTAssertEqual(Quests.daily(d, day: today).map(\.key), quests.map(\.key))
        let claimed = Quests.claim(d, today: today, now: 0)
        XCTAssertTrue(claimed.questsClaimed.contains("d:2026-11-04:Train"))
        XCTAssertGreaterThanOrEqual(claimed.questXp, 50)
        XCTAssertEqual(claimed.questCoins, claimed.questXp / 2)
        XCTAssertEqual(Quests.claim(claimed, today: today, now: 0).questXp, claimed.questXp)
        // A running booster doubles quest XP, not the coins.
        let boosted = Quests.claim({ var x = d; x.boostUntil = 10; return x }(), today: today, now: 0)
        XCTAssertEqual(boosted.questXp, claimed.questXp * 2)
        XCTAssertEqual(boosted.questCoins, claimed.questCoins)
    }

    /** Every fourth goal week earns a freeze; a missed week spends it and keeps the streak. */
    func testStreakFreezes() {
        let today = day(2026, 11, 4)
        let thisWeek = Stats.weekStart(today)
        var history: [Workout] = []
        // Goal weeks five to two weeks ago, nothing last week.
        for back in [5, 4, 3, 2] {
            var w = Workout(name: "A")
            w.startedAt = Stats.calendar.date(byAdding: .weekOfYear, value: -back, to: thisWeek)!.addingTimeInterval(3600 * 18).ms
            w.finishedAt = w.startedAt + 3_600_000
            history.append(w)
        }
        history.reverse()
        let s = Motivation.settle(history, goal: 1, Motivation.FreezeState(freezes: 0, frozen: [], settled: "", run: 0), today: today)
        let lastWeek = Stats.calendar.date(byAdding: .weekOfYear, value: -1, to: thisWeek)!
        XCTAssertEqual(s.freezes, 0) // earned after week four, spent on last week
        XCTAssertEqual(s.frozen, [Stats.weekKey(lastWeek)])
        XCTAssertEqual(s.run, 4)
        XCTAssertEqual(s.settled, Stats.weekKey(lastWeek))
        XCTAssertEqual(Stats.weekStreak(history, goal: 1, frozen: s.frozen, now: today), 4)
        XCTAssertEqual(Stats.weekStreak(history, goal: 1, now: today), 0)
        // Settling again changes nothing.
        XCTAssertEqual(Motivation.settle(history, goal: 1, s, today: today), s)
        XCTAssertEqual(Stats.weekOf(Stats.weekKey(lastWeek)), lastWeek)
    }

    /** Buying needs the coins and the level; a reroll draws the day anew. */
    func testShop() throws {
        let today = Stats.calendar.startOfDay(for: day(2026, 11, 4))
        var d = AppData()
        XCTAssertNil(Shop.buy(d, Shop.items().first { $0.key == "reroll" }!, today: today, now: 0))
        var w = workout("Push", hoursAgo: 0, [entry("0025", Array(repeating: (60.0, 8), count: 10))])
        w.startedAt = day(2026, 11, 3).ms
        w.finishedAt = w.startedAt + 3_600_000
        d.history = [w]
        // 30 for the workout, 50 for "First step", 10 for the chest skill level, 25 for collection tier I.
        XCTAssertEqual(Shop.balance(d), 115)
        let reroll = Shop.items().first { $0.key == "reroll" }!
        let after = try XCTUnwrap(Shop.buy(d, reroll, today: today, now: 0))
        XCTAssertEqual(after.coinsSpent, 40)
        XCTAssertEqual(after.rerollCount, 1)
        XCTAssertEqual(after.rerollDay, "2026-11-04")
        XCTAssertEqual(Shop.balance(after), 75)
        // Colours and titles wait for their level.
        let gold = Shop.items().first { $0.key == "accent:gold" }!
        XCTAssertFalse(Shop.unlocked(d, gold))
        XCTAssertNil(Shop.buy(d, gold, today: today, now: 0))
        XCTAssertEqual(Shop.items().filter { $0.kind == .title }.count, 16)
        XCTAssertEqual(Accent.of("gold"), .gold)
        XCTAssertTrue(Accent.gold.premium)
    }

    /** The weekly challenge rotates by week; the side quests are always of another kind. */
    func testChallengeRotation() {
        let d = AppData()
        var kinds: [Motivation.Kind] = []
        for back in 0..<6 {
            let t = Stats.calendar.date(byAdding: .weekOfYear, value: -back, to: day(2026, 11, 4))!
            let main = Motivation.challenge(d, today: t)
            let side = [1, 2].map { Motivation.challenge(d, today: t, slot: $0).kind }
            XCTAssertFalse(side.contains(main.kind))
            XCTAssertEqual(main.week, Stats.weekKey(t))
            kinds.append(main.kind)
        }
        XCTAssertEqual(Set(kinds).count, 6)
    }

    func testNutritionTargets() {
        let t = Nutrition.targets(kg: 80, goal: .Muscle)
        XCTAssertEqual(t.kcal, 2890)
        XCTAssertEqual(t.protein, 160)
        XCTAssertEqual(t.fat, 72)
        XCTAssertEqual(t.waterMl, 2750)
    }
}
