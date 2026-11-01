package dev.mauve.fit.wear

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MobileOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

private object Ink {
    val bg = Color(0xFF0B0B0F)
    val surface = Color(0xFF1E1E26)
    val text = Color(0xFFF3F3F6)
    val muted = Color(0xFF8C8C9C)
    val copper = Color(0xFFDB9062)
    val red = Color(0xFFFF5C7A)
}

private val colors = Colors(
    primary = Ink.copper, primaryVariant = Ink.copper, onPrimary = Ink.bg,
    secondary = Ink.copper, onSecondary = Ink.bg,
    background = Ink.bg, onBackground = Ink.text,
    surface = Ink.surface, onSurface = Ink.text, onSurfaceVariant = Ink.muted,
    error = Ink.red, onError = Ink.bg,
)

/** Ambient (always-on) flag plus a tick the system sends about once a minute while dimmed. */
class Ambient {
    var on by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)
}

class WatchActivity : ComponentActivity() {
    private lateinit var link: PhoneLink
    private lateinit var heart: HeartRate
    private val ambient = Ambient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = PhoneLink(this)
        heart = HeartRate(this)

        // Stay on screen in low power between sets instead of falling back to the watch face.
        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.tick = System.currentTimeMillis()
                ambient.on = true
            }
            override fun onUpdateAmbient() { ambient.tick = System.currentTimeMillis() }
            override fun onExitAmbient() { ambient.on = false }
        }))

        // The sensor only runs while a workout is open on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    link.state.map { it.active }.distinctUntilChanged().collect { if (it) heart.start() else heart.stop() }
                } finally {
                    heart.stop()
                }
            }
        }

        setContent {
            MaterialTheme(colors = colors) {
                WatchApp(link, heart, ambient, onHeartGranted = { if (link.state.value.active) heart.start() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        link.connect()
    }

    override fun onStop() {
        link.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        link.close()
        super.onDestroy()
    }
}

@Composable
private fun WatchApp(link: PhoneLink, heart: HeartRate, ambient: Ambient, onHeartGranted: () -> Unit) {
    val state by link.state.collectAsStateWithLifecycle()
    val status by link.link.collectAsStateWithLifecycle()
    val bpm by heart.bpm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    // Ask for the heart rate permission when it is first useful: once a workout runs.
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) onHeartGranted() }
    LaunchedEffect(state.active) {
        if (state.active && !asked && !heart.granted()) {
            asked = true
            ask.launch(heartPermission)
        }
    }

    fun send(cmd: String, feedback: Int = HapticFeedbackConstants.CLOCK_TICK) {
        view.performHapticFeedback(feedback)
        scope.launch { if (!link.send(cmd)) view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
    }

    // Seconds only tick while interactive and visible; ambient redraws on the system's minute tick.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ambient.on) {
        if (ambient.on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(250) }
        }
    }
    LaunchedEffect(ambient.tick) { now = System.currentTimeMillis() }

    // Buzz when the rest timer ends on the watch, independent of the phone. A rest that already
    // ended before the app opened stays silent.
    LaunchedEffect(state.restEndsAt, state.active) {
        val wait = state.restEndsAt - System.currentTimeMillis()
        if (!state.active || wait <= 0) return@LaunchedEffect
        delay(wait)
        now = System.currentTimeMillis()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) buzz(context)
    }

    val resting = state.active && state.restEndsAt > now
    // The countdown should stay readable; between sets the watch may dim to ambient.
    DisposableEffect(resting) {
        view.keepScreenOn = resting
        onDispose { view.keepScreenOn = false }
    }

    // Workout opens centred on "Set done", the idle screen on its title.
    val listState = key(state.active) { rememberScalingLazyListState(initialCenterItemIndex = if (state.active) 3 else 1) }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(if (ambient.on) Color.Black else Ink.bg),
        timeText = { if (!resting) TimeText() },
        vignette = { if (!ambient.on) Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { if (!ambient.on && !resting) PositionIndicator(scalingLazyListState = listState) },
    ) {
        when {
            state.active && resting -> Rest(state, now, bpm, ambient.on, onPlus = { send("plus15") }, onSkip = { send("skip") })
            state.active -> Screen(listState) { active(state, now, bpm, status != Link.Ready, ambient.on, ::send) }
            status == Link.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(indicatorColor = Ink.copper, trackColor = Ink.surface, modifier = Modifier.size(36.dp))
            }
            status == Link.NoPhone -> Message(Icons.Rounded.MobileOff, L("Handy nicht verbunden", "Phone not connected"),
                L("Bluetooth und Wear OS App am Handy prüfen", "Check Bluetooth and the Wear OS app on your phone"))
            status == Link.NoApp -> Message(Icons.Rounded.Smartphone, L("Mauve Fit am Handy öffnen", "Open Mauve Fit on your phone"),
                L("Trainings und Pläne kommen vom Handy", "Workouts and plans come from your phone"))
            else -> Screen(listState) { idle(state.suggestion, ambient.on) { send("start", HapticFeedbackConstants.CONFIRM) } }
        }
    }
}

private fun buzz(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1))
}

@Composable
private fun Screen(state: ScalingLazyListState, content: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Ink.copper, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Ink.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Ink.muted, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

private fun ScalingLazyListScope.idle(suggestion: String, ambient: Boolean, onStart: () -> Unit) {
    item { Text("MAUVE FIT", color = Ink.copper, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
    item {
        Text(L("Kein Training läuft", "No workout running"), color = Ink.text, fontSize = 17.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    if (suggestion.isBlank()) item {
        Text(L("Starte ein Training am Handy", "Start a workout on your phone"), color = Ink.muted, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
    } else {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L("Nächstes Training", "Next workout"), color = Ink.muted, fontSize = 12.sp)
                Text(suggestion, color = Ink.text, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (!ambient) item {
            Chip(
                onClick = onStart,
                label = { Text(L("Starten", "Start"), fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text(L("Läuft am Handy", "Runs on your phone")) },
                icon = { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp)) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ScalingLazyListScope.active(
    s: WatchState, now: Long, bpm: Int, offline: Boolean, ambient: Boolean, send: (String, Int) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ambient) s.workout.ifBlank { "Mauve Fit" } else clock(now - s.startedAt), color = Ink.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (bpm > 0 && !ambient) HeartBadge(bpm, Modifier.padding(start = 8.dp))
        }
    }
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.exerciseCount > 1) Text(L("Übung ${s.exerciseIndex + 1} von ${s.exerciseCount}", "Exercise ${s.exerciseIndex + 1} of ${s.exerciseCount}"),
                color = Ink.muted, fontSize = 11.sp)
            Text(s.exercise, color = Ink.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (s.warmup) L("Aufwärmen", "Warm-up") else L("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}"),
                color = if (ambient) Ink.muted else Ink.copper, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(load(s), color = Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (offline) Text(L("Handy getrennt", "Phone disconnected"), color = Ink.red, fontSize = 11.sp)
        }
    }
    if (ambient) return
    item {
        Chip(
            onClick = { send("done", HapticFeedbackConstants.CONFIRM) },
            label = { Text(L("Satz erledigt", "Set done"), fontWeight = FontWeight.Bold) },
            icon = { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(24.dp)) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (!s.bodyweight) item {
        Stepper(L("Gewicht", "Weight"), "${fmt(s.weight)} kg", { send("w-", HapticFeedbackConstants.CLOCK_TICK) }, { send("w+", HapticFeedbackConstants.CLOCK_TICK) })
    }
    item {
        Stepper(L("Wiederholungen", "Reps"), "${s.reps}", { send("r-", HapticFeedbackConstants.CLOCK_TICK) }, { send("r+", HapticFeedbackConstants.CLOCK_TICK) })
    }
}

@Composable
private fun Rest(s: WatchState, now: Long, bpm: Int, ambient: Boolean, onPlus: () -> Unit, onSkip: () -> Unit) {
    val left = (s.restEndsAt - now).coerceAtLeast(0)
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!ambient) CircularProgressIndicator(
            progress = (left / (s.restTotalSec * 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize().padding(3.dp),
            indicatorColor = Ink.copper,
            trackColor = Ink.surface,
            strokeWidth = 6.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(L("PAUSE", "REST"), color = if (ambient) Ink.muted else Ink.copper, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            if (ambient) {
                // No per-second redraws while dimmed: show when the rest ends instead of a stale countdown.
                Text(L("bis", "until"), color = Ink.muted, fontSize = 12.sp)
                Text(DateFormat.getTimeFormat(context).format(Date(s.restEndsAt)), color = Ink.text, fontSize = 30.sp)
            } else {
                Text(clock(left + 999), color = Ink.text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            if (bpm > 0 && !ambient) HeartBadge(bpm)
            Text(L("Nächster: ${s.exercise}", "Up next: ${s.exercise}"), color = Ink.muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (!ambient) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlus, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)) {
                        Text("+15", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    RoundButton(Icons.Rounded.SkipNext, L("Pause überspringen", "Skip rest"), ButtonDefaults.DefaultButtonSize, onSkip)
                }
            }
        }
    }
}

@Composable
private fun HeartBadge(bpm: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(Icons.Rounded.Favorite, null, tint = Ink.red, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text("$bpm", color = Ink.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(" bpm", color = Ink.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RoundButton(Icons.Rounded.Remove, L("Weniger", "Less"), ButtonDefaults.DefaultButtonSize, onMinus)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(label, color = Ink.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = Ink.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        RoundButton(Icons.Rounded.Add, L("Mehr", "More"), ButtonDefaults.DefaultButtonSize, onPlus)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(size)) {
        Icon(icon, label, modifier = Modifier.size(size * 0.45f))
    }
}

/** German or English, following the watch language. */
private fun L(de: String, en: String) = if (java.util.Locale.getDefault().language == "de") de else en

private fun load(s: WatchState) = if (s.bodyweight) L("${s.reps} Wdh.", "${s.reps} reps") else "${fmt(s.weight)} kg x ${s.reps}"

/** Up to two decimals without trailing zeros; comma in German, dot otherwise. */
private fun fmt(v: Double): String {
    val t = "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
    return L(t.replace('.', ','), t)
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
