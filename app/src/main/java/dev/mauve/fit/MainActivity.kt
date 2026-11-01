package dev.mauve.fit

import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import android.Manifest
import android.os.Build
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import dev.mauve.fit.data.L
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.system.Foreground
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import dev.mauve.fit.ui.LibraryScreen
import dev.mauve.fit.ui.NutritionScreen
import dev.mauve.fit.ui.RoutineEditScreen
import dev.mauve.fit.ui.RoutinesScreen
import dev.mauve.fit.ui.IntroScreen
import dev.mauve.fit.ui.QuickWorkoutScreen
import dev.mauve.fit.ui.SettingsScreen
import dev.mauve.fit.ui.SummaryScreen
import dev.mauve.fit.ui.WorkoutScreen

private enum class Tab(val route: String, private val de: String, private val en: String, val icon: ImageVector) {
    Home("home", "Heute", "Today", Icons.Rounded.Bolt),
    Plans("plans", "Training", "Training", Icons.Rounded.FitnessCenter),
    Nutrition("nutrition", "Essen", "Food", Icons.Rounded.Restaurant),
    History("history", "Fortschritt", "Progress", Icons.Rounded.BarChart);

    val label: String get() = L(de, en)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme and accent are fixed per activity; changing them in the settings recreates it.
        val settings = App.store.data.value.settings
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> systemDark }
        Ink.apply(dark, Accent.of(settings.accent))
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent {
            FitTheme {
                // Surface sets the default content colour, so plain Text is light on the dark background.
                Surface(color = Ink.bg, contentColor = Ink.text) { AppRoot() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Foreground.visible = true
    }

    override fun onStop() {
        super.onStop()
        Foreground.visible = false
        App.store.flush()
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val data by App.store.data.collectAsStateWithLifecycle()
    // Ask for notifications once the intro is done: needed for the rest timer in the background.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(data.profile.onboarded) {
        if (data.profile.onboarded && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize()) {
            composable(Tab.Home.route) { HomeScreen(data, nav) }
            composable("library") { LibraryScreen(data, nav, pickFor = null) }
            composable(Tab.Plans.route) { RoutinesScreen(data, nav) }
            composable(Tab.History.route) { HistoryScreen(data, nav) }
            composable(Tab.Nutrition.route) { NutritionScreen(data, nav) }
            composable("exercise/{id}") { ExerciseScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
            composable("routine/{id}") { RoutineEditScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
            composable("pick/{target}") { LibraryScreen(data, nav, pickFor = it.arguments?.getString("target")) }
            composable("workout") { WorkoutScreen(data, nav) }
            composable("summary/{id}") { SummaryScreen(it.arguments?.getString("id").orEmpty(), data, nav) }
            composable("quick") { QuickWorkoutScreen(data, nav) }
            composable("settings") { SettingsScreen(data, nav) }
            composable("intro") { IntroScreen(data.profile) { nav.popBackStack() } }
        }

        // First run: the intro covers everything until it is finished.
        if (!data.profile.onboarded) IntroScreen(data.profile) {}

        AnimatedVisibility(
            visible = data.profile.onboarded && Tab.entries.any { it.route == route },
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(route, nav)
        }
    }
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
