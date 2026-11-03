import SwiftUI

@main
struct MauveFitApp: App {
    @State private var store = Demo.enabled ? Demo.store() : Store()
    @State private var router = Router(showWorkout: Demo.workout)
    @Environment(\.scenePhase) private var phase

    init() {
        let tabs = UITabBarAppearance()
        tabs.configureWithOpaqueBackground()
        tabs.backgroundColor = UIColor(Ink.surface)
        UITabBar.appearance().standardAppearance = tabs
        UITabBar.appearance().scrollEdgeAppearance = tabs
        let nav = UINavigationBarAppearance()
        nav.configureWithTransparentBackground()
        nav.backgroundColor = UIColor(Ink.bg)
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor(Ink.text)]
        nav.titleTextAttributes = [.foregroundColor: UIColor(Ink.text)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        // Screenshot runs must not be covered by the permission prompt.
        if !Demo.enabled { RestAlarm.requestPermission() }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(router)
                .preferredColorScheme(.dark)
                .tint(Ink.accent)
                .onChange(of: phase) { _, p in if p != .active { store.flush() } }
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
        // Re-renders every label when the language setting changes.
        .id(store.data.settings.language)
        .fullScreenCover(isPresented: $router.showWorkout) {
            WorkoutView().environment(store).environment(router)
        }
    }
}
