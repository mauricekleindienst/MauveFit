import XCTest

/**
 * The six App Store screenshots, in the language the runner passes as STORE_LANG ("de" or "en",
 * set with TEST_RUNNER_STORE_LANG on xcodebuild). Run by the App Store metadata workflow on a
 * 6.9-inch iPhone; the normal CI skips it.
 */
final class StoreScreenshots: XCTestCase {
    private let lang = ProcessInfo.processInfo.environment["STORE_LANG"] ?? "de"

    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-AppleLanguages", "(\(lang))", "-AppleLocale", lang == "de" ? "de_DE" : "en_US"] + extra
        app.launch()
        return app
    }

    /** Waits for the exercise stills from the CDN, then keeps the screen under [name]. */
    private func snap(_ app: XCUIApplication, _ name: String) {
        sleep(4)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["Don\u{2019}t Allow", "Nicht erlauben", "OK"] where springboard.alerts.buttons[label].exists {
            springboard.alerts.buttons[label].tap()
        }
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any)[id].firstMatch
    }

    private func tab(_ app: XCUIApplication, _ index: Int) {
        let button = app.tabBars.buttons.element(boundBy: index)
        XCTAssertTrue(button.waitForExistence(timeout: 10))
        button.tap()
    }

    func testStoreScreens() {
        var app = launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 20))
        snap(app, "1-today")
        tab(app, 3)
        snap(app, "5-progress")
        tab(app, 2)
        snap(app, "6-nutrition")
        tab(app, 1)
        let library = element(app, "library")
        XCTAssertTrue(library.waitForExistence(timeout: 10))
        library.tap()
        let bench = element(app, "exercise-0025")
        XCTAssertTrue(bench.waitForExistence(timeout: 10))
        bench.tap()
        XCTAssertTrue(element(app, "exerciseDetail").waitForExistence(timeout: 10))
        snap(app, "4-exercise")
        app.terminate()

        app = launch(["-demoWorkout"])
        XCTAssertTrue(app.otherElements["restBar"].waitForExistence(timeout: 20))
        snap(app, "2-workout")
        app.terminate()

        app = launch(["-demoSummary"])
        XCTAssertTrue(element(app, "summary").waitForExistence(timeout: 20))
        snap(app, "3-summary")
    }
}
