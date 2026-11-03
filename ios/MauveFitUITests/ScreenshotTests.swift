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

    /** Waits a moment for the exercise stills to load from the CDN, then keeps the screen. */
    private func snap(_ app: XCUIApplication, _ name: String) {
        sleep(3)
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

    func testTabs() {
        let app = launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 15))
        snap(app, "01-heute")

        tab(app, 1)
        snap(app, "02-training")

        let library = app.buttons["library"]
        XCTAssertTrue(library.waitForExistence(timeout: 10))
        library.tap()
        snap(app, "06-uebungen")

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
}
