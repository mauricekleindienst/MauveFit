import SwiftUI

@main
struct MauveFitApp: App {
    @State private var store: Store
    @State private var router = Router(showWorkout: Demo.workout)
    @Environment(\.scenePhase) private var phase

    init() {
        let store = Demo.enabled ? Demo.store() : Store()
        // A workout left open for hours is saved (or dropped when empty) instead of resumed.
        if !Demo.enabled { store.closeIdleWorkout() }
        Ink.accentChoice = Accent.of(store.data.settings.accent)
        _store = State(initialValue: store)
        MauveFitApp.styleBars()
        // Test and screenshot runs must not be covered by the permission prompt.
        if !Demo.enabled && ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil { RestAlarm.requestPermission() }
    }

    /** UIKit bars in the palette and in Archivo; their colours follow light and dark by themselves. */
    static func styleBars() {
        let tabs = UITabBarAppearance()
        tabs.configureWithOpaqueBackground()
        tabs.backgroundColor = Ink.surfaceUI
        tabs.shadowColor = .clear
        let item = UITabBarItemAppearance()
        item.normal.iconColor = Ink.mutedUI
        item.normal.titleTextAttributes = [.foregroundColor: Ink.mutedUI, .font: Archivo.ui(11, weight: 600)]
        item.selected.titleTextAttributes = [.foregroundColor: Ink.textUI, .font: Archivo.ui(11, weight: 700)]
        tabs.stackedLayoutAppearance = item
        tabs.inlineLayoutAppearance = item
        tabs.compactInlineLayoutAppearance = item
        UITabBar.appearance().standardAppearance = tabs
        UITabBar.appearance().scrollEdgeAppearance = tabs
        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = Ink.bgUI
        nav.shadowColor = .clear
        nav.titleTextAttributes = [.foregroundColor: Ink.textUI, .font: Archivo.ui(17, weight: 700, width: 114)]
        nav.largeTitleTextAttributes = [.foregroundColor: Ink.textUI, .font: Archivo.ui(32, weight: 800, width: 125)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(router)
                .onChange(of: phase) { _, p in
                    if p != .active { store.flush() } else if !Demo.enabled { store.closeIdleWorkout() }
                }
        }
    }
}

/** UI state shared across tabs: the selected tab and whether the workout screen is open. */
@Observable
final class Router {
    var tab: AppTab = .today
    var showWorkout: Bool

    init(showWorkout: Bool = false) { self.showWorkout = showWorkout }
}

enum AppTab: Hashable { case today, training, food, progress }

struct RootView: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router

    var body: some View {
        @Bindable var router = router
        let s = store.data.settings
        TabView(selection: $router.tab) {
            TodayView()
                .tabItem { Label(L("Heute", "Today"), systemImage: "bolt.fill") }
                .tag(AppTab.today)
            TrainingView()
                .tabItem { Label(L("Training", "Training"), systemImage: "dumbbell.fill") }
                .tag(AppTab.training)
            FoodView()
                .tabItem { Label(L("Essen", "Food"), systemImage: "fork.knife") }
                .tag(AppTab.food)
            ProgressTab()
                .tabItem { Label(L("Fortschritt", "Progress"), systemImage: "chart.bar.fill") }
                .tag(AppTab.progress)
        }
        // Re-renders every label when the language changes; colours follow the store by themselves.
        .id(s.language)
        .tint(Ink.accent)
        .preferredColorScheme(s.theme == "light" ? .light : s.theme == "dark" ? .dark : nil)
        .fullScreenCover(isPresented: $router.showWorkout) {
            WorkoutView()
                .environment(store).environment(router)
                .tint(Ink.accent)
                .preferredColorScheme(s.theme == "light" ? .light : s.theme == "dark" ? .dark : nil)
        }
    }
}
