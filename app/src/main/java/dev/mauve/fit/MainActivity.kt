package dev.mauve.fit

import dev.mauve.fit.system.Recovery
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import dev.mauve.fit.data.L
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.system.Foreground
import dev.mauve.fit.system.WorkoutService
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mauve.fit.ui.ExerciseScreen
import dev.mauve.fit.ui.FitTheme
import dev.mauve.fit.ui.HistoryScreen
import dev.mauve.fit.ui.HomeScreen
import dev.mauve.fit.ui.Accent
import dev.mauve.fit.ui.Ink
import dev.mauve.fit.ui.Pill
import dev.mauve.fit.ui.NutritionScreen
import dev.mauve.fit.ui.RoutineEditScreen
import dev.mauve.fit.ui.RoutinesScreen
import dev.mauve.fit.ui.IntroScreen
import dev.mauve.fit.ui.introAskedNotifications
import dev.mauve.fit.ui.QuickWorkoutScreen
import dev.mauve.fit.ui.SettingsScreen
import dev.mauve.fit.ui.SummaryScreen
import dev.mauve.fit.ui.ToolsScreen
import dev.mauve.fit.ui.InsightsScreen
import dev.mauve.fit.data.Stats
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import dev.mauve.fit.ui.Centered
import dev.mauve.fit.ui.GridWidth
import dev.mauve.fit.ui.LibraryPanes
import dev.mauve.fit.ui.LibraryScreen
import dev.mauve.fit.ui.ListWidth
import dev.mauve.fit.ui.LocalWidth
import dev.mauve.fit.ui.WindowWidth
import dev.mauve.fit.ui.WorkoutScreen

private enum class Tab(val route: String, private val de: String, private val en: String, val icon: ImageVector) {
    Home("home", "Heute", "Today", Icons.Rounded.Bolt),
    Plans("plans", "Training", "Training", Icons.Rounded.FitnessCenter),
    Nutrition("nutrition", "Essen", "Food", Icons.Rounded.Restaurant),
    History("history", "Fortschritt", "Progress", Icons.Rounded.BarChart);

    val label: String get() = L(de, en)
}

class MainActivity : ComponentActivity() {
    /** Screen asked for by a notification, launcher shortcut or widget ([EXTRA_OPEN]); AppRoot opens it and clears it. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(EXTRA_OPEN)
        // Theme and accent are fixed per activity; changing them in the settings recreates it.
        val settings = App.store.data.value.settings
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> systemDark }
        val accent = Accent.of(settings.accent)
        // Android 12+ derives tones from the wallpaper; the primary of its scheme is the accent.
        val wallpaperTone = if (accent == Accent.Wallpaper && Build.VERSION.SDK_INT >= 31) {
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(this).primary else androidx.compose.material3.dynamicLightColorScheme(this).primary
        } else null
        Ink.apply(dark, accent, wallpaperTone)
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent {
            FitTheme {
                // Surface sets the default content colour, so plain Text is light on the dark background.
                Surface(color = Ink.bg, contentColor = Ink.text) { AppRoot(openRequest.value) { openRequest.value = null } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        Foreground.visible = true
        // A workout started from the watch in the background only got a stand-in notice; now that
        // the app is in front, the real foreground service may start.
        WorkoutService.start(this)
        // Fresh watch data each time the app comes to the front.
        lifecycleScope.launch { Recovery.refresh(this@MainActivity) }
    }

    override fun onStop() {
        super.onStop()
        Foreground.visible = false
        App.store.flush()
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}

@Composable
private fun AppRoot(open: String?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val data by App.store.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Tapping the workout notification lands on the running workout; launcher shortcuts and the
    // widget ask for the other screens.
    LaunchedEffect(open, data.profile.onboarded) {
        if (open == null || !data.profile.onboarded) return@LaunchedEffect
        val d = App.store.data.value
        when (open) {
            "workout" -> if (d.active != null && nav.currentDestination?.route != "workout") nav.navigate("workout") { launchSingleTop = true }
            // Starts the coach's next plan, or just opens the workout that is already running.
            "start-next" -> {
                if (d.active == null) App.store.startWorkout(Stats.nextRoutine(d.routines, d.history, App.repo))
                if (nav.currentDestination?.route != "workout") nav.navigate("workout") { launchSingleTop = true }
            }
            "quick", "tools" -> nav.navigate(open) { launchSingleTop = true }
            "nutrition" -> nav.navigate(Tab.Nutrition.route) {
                popUpTo(Tab.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // A glass of water is logged in place; a short notice is all the feedback it needs.
            "water" -> {
                val today = LocalDate.now().toString()
                App.store.addWater(today, 250)
                Haptics.click()
                val total = App.store.data.value.water[today] ?: 0
                Toast.makeText(context, L("250 ml Wasser eingetragen, heute $total ml", "250 ml of water logged, $total ml today"), Toast.LENGTH_SHORT).show()
            }
        }
        onOpened()
    }
    // Ask for notifications once the intro is done (unless it just asked): needed for the rest timer in the background.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(data.profile.onboarded) {
        if (data.profile.onboarded && Build.VERSION.SDK_INT >= 33 && !introAskedNotifications) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Window width decides the navigation: the floating pill below on phones, a rail on the left
    // from 600 dp (tablets, unfolded foldables, phones on their side). Only the width counts, so a
    // fold or a resized window just lands in the matching layout.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = WindowWidth.of(maxWidth)
        val showNav = data.profile.onboarded && Tab.entries.any { it.route == route }
        CompositionLocalProvider(LocalWidth provides width) {
            Box(Modifier.fillMaxSize()) {
                // The host stays at the same place in both layouts, so switching keeps every screen's state.
                Row(Modifier.fillMaxSize()) {
                    if (width.wide) SideBar(route, nav, showNav)
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .then(if (width.wide) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)) else Modifier)
                    ) {
                        NavHost(
                            nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize(),
                            // Tabs: the new tab fades in over the old one, which stays put until it is covered.
                            // Pushed screens move edge to edge like pages, so they never overlap; that keeps it
                            // clean whichever of the two is drawn on top (also while the back gesture scrubs).
                            enterTransition = { if (isTabSwitch()) fadeIn(tween(180)) else slideInHorizontally(tween(280)) { it } },
                            exitTransition = { if (isTabSwitch()) ExitTransition.KeepUntilTransitionsFinished else slideOutHorizontally(tween(280)) { -it } },
                            popEnterTransition = { if (isTabSwitch()) fadeIn(tween(180)) else slideInHorizontally(tween(280)) { -it } },
                            popExitTransition = { if (isTabSwitch()) ExitTransition.KeepUntilTransitionsFinished else slideOutHorizontally(tween(280)) { it } },
                        ) {
                            screen(Tab.Home.route, GridWidth) { HomeScreen(data, nav) }
                            screen("library", GridWidth) { LibraryPanes(data, nav) }
                            screen(Tab.Plans.route) { RoutinesScreen(data, nav) }
                            screen(Tab.History.route, GridWidth) { HistoryScreen(data, nav) }
                            screen(Tab.Nutrition.route) { NutritionScreen(data, nav) }
                            screen("exercise/{id}") { ExerciseScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
                            screen("routine/{id}") { RoutineEditScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
                            screen("pick/{target}") { LibraryScreen(data, nav, pickFor = it.arguments?.getString("target")) }
                            screen("workout", GridWidth) { WorkoutScreen(data, nav) }
                            screen("summary/{id}") { SummaryScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
                            screen("quick") { QuickWorkoutScreen(data, nav) }
                            screen("tools") { ToolsScreen(data, nav) }
                            screen("insights", GridWidth) { InsightsScreen(data, nav) }
                            screen("settings") { SettingsScreen(data, nav) }
                            screen("intro") { IntroScreen(data.profile, redo = true) { nav.popBackStack() } }
                        }
                    }
                }

                // First run: the intro covers everything until it is finished.
                if (!data.profile.onboarded) Centered(ListWidth) { IntroScreen(data.profile) {} }

                if (!width.wide) AnimatedVisibility(
                    visible = showNav,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BottomBar(route, nav)
                }
            }
        }
    }
}

private val tabRoutes = Tab.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

/**
 * A destination with its own opaque background, so two screens never show through each other
 * while one slides over the other.
 */
private fun NavGraphBuilder.screen(route: String, maxWidth: Dp = ListWidth, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route) { entry ->
        // Wide windows: the screen keeps a readable width, centred in the space next to the rail.
        if (LocalWidth.current.wide) Box(Modifier.fillMaxSize().background(Ink.bg), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content(entry) }
        }
        else Box(Modifier.fillMaxSize().background(Ink.bg)) { content(entry) }
    }

/**
 * Floating pill navigation: the active tab grows into a lime capsule with its label, the others
 * stay as icons. Content fades out underneath instead of ending at a hard edge.
 */
@Composable
private fun BottomBar(route: String?, nav: NavHostController) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink.bg.copy(alpha = 0f), Ink.bg.copy(alpha = 0.92f), Ink.bg)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).clip(Pill).background(Ink.raised)
                .border(1.dp, Ink.line, Pill).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = tab.route == route
                Row(
                    Modifier.height(52.dp).clip(Pill)
                        .background(if (active) Ink.accent else Ink.raised)
                        .clickable {
                            if (!active) Haptics.tick()
                            if (!active) nav.navigate(tab.route) {
                                popUpTo(Tab.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .animateContentSize()
                        .padding(horizontal = if (active) 20.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, tab.label, tint = if (active) Ink.bg else Ink.muted, modifier = Modifier.size(24.dp))
                    if (active) Text(
                        tab.label, color = Ink.bg, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The bottom bar's counterpart for wide windows: the same raised pill standing upright at the
 * left edge, the active tab's icon in an accent capsule with its label below. Like the bottom bar
 * it gives way on pushed screens, but keeps its width so the content never jumps.
 */
@Composable
private fun SideBar(route: String?, nav: NavHostController, visible: Boolean) {
    Box(
        Modifier.fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(104.dp).padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
            Column(
                Modifier.width(88.dp).clip(Pill).background(Ink.raised).border(1.dp, Ink.line, Pill)
                    // Scrolls only when a phone on its side is too short for all four tabs.
                    .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Tab.entries.forEach { tab ->
                    val active = tab.route == route
                    Column(
                        Modifier.width(80.dp).clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (!active) Haptics.tick()
                                if (!active) nav.navigate(tab.route) {
                                    popUpTo(Tab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 56.dp, height = 34.dp).clip(Pill).background(if (active) Ink.accent else Ink.raised),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.icon, null, tint = if (active) Ink.bg else Ink.muted, modifier = Modifier.size(24.dp)) }
                        Text(
                            tab.label, color = if (active) Ink.text else Ink.muted, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else null, modifier = Modifier.padding(top = 4.dp), maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}