import XCTest

/**
 * The main screens on other iPhones (#53): the CI runs this on a small iPhone and on the iPhone
 * Duo where the simulator has them, so short and wide screens get looked at too.
 */
final class DeviceScreens: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-AppleLanguages", "(de)", "-AppleLocale", "de_DE"] + extra
        app.launch()
        return app
    }

    private func snap(_ app: XCUIApplication, _ name: String) {
        sleep(2)
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

    func testScreens() {
        var app = launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 20))
        snap(app, "1-heute")
        tab(app, 1)
        snap(app, "2-training")
        tab(app, 2)
        snap(app, "3-essen")
        tab(app, 3)
        snap(app, "4-fortschritt")
        app.terminate()

        app = launch(["-demoWorkout"])
        XCTAssertTrue(app.otherElements["restBar"].waitForExistence(timeout: 20))
        snap(app, "5-training-laeuft")
        app.terminate()

        app = launch(["-demoSummary"])
        XCTAssertTrue(app.descendants(matching: .any)["summary"].firstMatch.waitForExistence(timeout: 20))
        snap(app, "6-zusammenfassung")
    }
}
