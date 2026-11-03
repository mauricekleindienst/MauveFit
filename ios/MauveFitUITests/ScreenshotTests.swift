import XCTest

/** Walks through the main screens with sample data and keeps a screenshot of each. */
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-AppleLanguages", "(de)", "-AppleLocale", "de_DE"] + extra
        app.launch()
        return app
    }

    /** A system alert left over from an earlier launch on this simulator is dismissed first. */
    private func dismissSystemAlerts() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["Don\u{2019}t Allow", "Nicht erlauben", "Allow", "Erlauben", "OK"] {
            let button = springboard.alerts.buttons[label]
            if button.exists { button.tap(); return }
        }
    }

    /** Waits a moment for the exercise stills to load from the CDN, then keeps the screen. */
    private func snap(_ app: XCUIApplication, _ name: String) {
        sleep(3)
        dismissSystemAlerts()
        sleep(1)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    private func tab(_ app: XCUIApplication, _ index: Int) {
        let button = app.tabBars.buttons.element(boundBy: index)
        XCTAssertTrue(button.waitForExistence(timeout: 10))
        button.tap()
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any)[id].firstMatch
    }

    func testTabs() {
        let app = launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        snap(app, "01-heute")

        let settings = element(app, "settings")
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        XCTAssertTrue(element(app, "settingsScreen").waitForExistence(timeout: 10))
        snap(app, "09-einstellungen")
        app.buttons["Fertig"].firstMatch.tap()

        tab(app, 1)
        snap(app, "02-training")

        let library = element(app, "library")
        XCTAssertTrue(library.waitForExistence(timeout: 10))
        library.tap()
        snap(app, "06-uebungen")

        // A favourite, so it is at the top of the list.
        let bench = element(app, "exercise-0025")
        XCTAssertTrue(bench.waitForExistence(timeout: 10))
        bench.tap()
        XCTAssertTrue(element(app, "exerciseDetail").waitForExistence(timeout: 10))
        snap(app, "08-uebung")

        tab(app, 2)
        snap(app, "04-essen")

        tab(app, 3)
        snap(app, "05-fortschritt")
    }

    func testRunningWorkout() {
        let app = launch(["-demoWorkout"])
        XCTAssertTrue(app.otherElements["restBar"].waitForExistence(timeout: 15))
        snap(app, "03-training-laeuft")
    }

    /** Finishing for real: the confirmation, then the summary of the saved workout. */
    func testFinishShowsSummary() {
        let app = launch(["-demoWorkout"])
        let finish = element(app, "finish")
        XCTAssertTrue(finish.waitForExistence(timeout: 15))
        finish.tap()
        let save = app.buttons["Speichern und beenden"].firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        XCTAssertTrue(element(app, "summary").waitForExistence(timeout: 10))
    }

    /** A finished workout with a new record, the state right after "Save and finish". */
    func testSummary() {
        let app = launch(["-demoSummary"])
        XCTAssertTrue(element(app, "summary").waitForExistence(timeout: 15))
        snap(app, "07-zusammenfassung")
    }

    func testLightTheme() {
        let app = launch(["-demoLight"])
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        snap(app, "10-heute-hell")
        tab(app, 3)
        snap(app, "11-fortschritt-hell")
    }

    /** A fresh install: every tab with no history, an exercise without a drawing, an empty workout. */
    func testEmptyState() {
        let app = launch(["-emptyData"])
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        snap(app, "13-leer-heute")
        tab(app, 2)
        snap(app, "15-leer-essen")
        tab(app, 3)
        snap(app, "16-leer-fortschritt")
        tab(app, 1)
        snap(app, "14-leer-training")
        let library = element(app, "library")
        XCTAssertTrue(library.waitForExistence(timeout: 10))
        library.tap()
        // 3/4 Sit-up: first in the list, and one of the exercises without a drawing.
        let situp = element(app, "exercise-0001")
        XCTAssertTrue(situp.waitForExistence(timeout: 10))
        situp.tap()
        XCTAssertTrue(element(app, "exerciseDetail").waitForExistence(timeout: 10))
        snap(app, "17-uebung-ohne-zeichnung")
        // Start the suggested plan from Heute and finish it with nothing ticked: it is not saved.
        tab(app, 0)
        let start = element(app, "startNext")
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        start.tap()
        let finish = element(app, "finish")
        XCTAssertTrue(finish.waitForExistence(timeout: 10))
        snap(app, "18-leer-training-laeuft")
        finish.tap()
        let save = app.buttons["Speichern und beenden"].firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 10))
    }

    /** The largest accessibility text size still fits. */
    func testLargeText() {
        let size = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
        var app = launch(size)
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        snap(app, "19-grosse-schrift-heute")
        app.terminate()
        app = launch(size + ["-demoWorkout"])
        XCTAssertTrue(element(app, "finish").waitForExistence(timeout: 15))
        snap(app, "20-grosse-schrift-training")
    }

    func testLightWorkout() {
        let app = launch(["-demoLight", "-demoWorkout"])
        XCTAssertTrue(app.otherElements["restBar"].waitForExistence(timeout: 15))
        snap(app, "12-training-laeuft-hell")
    }
}
