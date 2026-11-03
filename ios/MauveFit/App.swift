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
        // On iOS 26 the tab bar and toolbars are Liquid Glass by themselves; any appearance set
        // here would paint over the glass, so only earlier systems get the palette.
        if #unavailable(iOS 26) { MauveFitApp.styleBars() }
        // Test and screenshot runs must not be covered by the permission prompt.
        if !Demo.enabled && ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil { RestAlarm.requestPermission() }
    }

    /** UIKit bars in the palette before iOS 26; their colours follow light and dark by themselves. */
    static func styleBars() {
        let tabs = UITabBarAppearance()
        tabs.configureWithDefaultBackground()
        let nav = UINavigationBarAppearance()
        nav.configureWithDefaultBackground()
        nav.titleTextAttributes = [.foregroundColor: Ink.textUI]
        nav.largeTitleTextAttributes = [.foregroundColor: Ink.textUI]
        UITabBar.appearance().standardAppearance = tabs
        UINavigationBar.appearance().standardAppearance = nav
        let edge = UINavigationBarAppearance()
        edge.configureWithTransparentBackground()
        edge.largeTitleTextAttributes = [.foregroundColor: Ink.textUI]
        UINavigationBar.appearance().scrollEdgeAppearance = edge
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
        let scheme: ColorScheme? = s.theme == "light" ? .light : s.theme == "dark" ? .dark : nil
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
        .workoutAccessory()
        // Re-renders every label when the language changes; colours follow the store by themselves.
        .id(s.language)
        .tint(Ink.accent)
        .preferredColorScheme(scheme)
        .fullScreenCover(isPresented: $router.showWorkout) {
            WorkoutView()
                .environment(store).environment(router)
                .tint(Ink.accent)
                .preferredColorScheme(scheme)
        }
    }
}

private extension View {
    /** iOS 26: the running workout (or the next plan) floats above the tab bar in glass. */
    @ViewBuilder
    func workoutAccessory() -> some View {
        if #available(iOS 26, *) {
            self.tabViewBottomAccessory { WorkoutAccessory() }
                .tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}

/** The bar above the tab bar: resume the running workout, or start the next plan. */
private struct WorkoutAccessory: View {
    @Environment(Store.self) private var store
    @Environment(Router.self) private var router

    var body: some View {
        let d = store.data
        if let w = d.active {
            let total = w.entries.reduce(0) { $0 + $1.sets.filter { !$0.warmup }.count }
            Button { router.showWorkout = true } label: {
                HStack(spacing: 10) {
                    Image(systemName: w.restEndsAt != nil ? "timer" : "figure.strengthtraining.traditional")
                        .foregroundStyle(Ink.accent)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(w.name).font(.fit(.label)).lineLimit(1)
                        Text(L("\(w.doneSets.count) von \(Fmt.sets(total))", "\(w.doneSets.count) of \(Fmt.sets(total))"))
                            .font(.fit(.labelSmall)).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(L("Fortsetzen", "Resume")).font(.fit(.label)).foregroundStyle(Ink.accent)
                }
                .padding(.horizontal, 16)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("workoutAccessory")
        } else if let r = Stats.nextRoutine(d) {
            Button {
                store.startWorkout(r)
                router.showWorkout = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "play.fill").foregroundStyle(Ink.accent)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(L("Als Nächstes", "Up next")).font(.fit(.labelSmall)).foregroundStyle(.secondary)
                        Text(r.name).font(.fit(.label)).lineLimit(1)
                    }
                    Spacer()
                    Text(L("Starten", "Start")).font(.fit(.label)).foregroundStyle(Ink.accent)
                }
                .padding(.horizontal, 16)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("workoutAccessory")
        } else {
            Text(L("Lege unter Training einen Plan an.", "Add a plan under Training.")).font(.fit(.label)).foregroundStyle(.secondary)
        }
    }
}
