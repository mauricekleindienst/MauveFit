package dev.mauve.fit.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Ink {
    val bg = Color(0xFF0B0B0F)
    val surface = Color(0xFF1E1E26)
    val text = Color(0xFFF3F3F6)
    val muted = Color(0xFF8C8C9C)
    val lime = Color(0xFFD4FF3A)
    val mauve = Color(0xFFCBA6F7)
    val red = Color(0xFFFF5C7A)
}

class WatchActivity : ComponentActivity() {
    private lateinit var link: PhoneLink
    private lateinit var heart: HeartRate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = PhoneLink(this)
        heart = HeartRate(this)
        setContent { MaterialTheme { WatchApp(link, heart) } }
    }

    override fun onResume() {
        super.onResume()
        link.connect()
        heart.start()
    }

    override fun onPause() {
        super.onPause()
        link.disconnect()
        heart.stop()
    }
}

private val heartPermission =
    if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

@Composable
private fun WatchApp(link: PhoneLink, heart: HeartRate) {
    val state by link.state.collectAsStateWithLifecycle()
    val bpm by heart.bpm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) heart.start() }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, heartPermission) != PackageManager.PERMISSION_GRANTED) ask.launch(heartPermission)
    }
    fun send(cmd: String) = scope.launch { link.send(cmd) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    // Buzz when the rest timer ends on the watch, independent of the phone.
    LaunchedEffect(state.restEndsAt) {
        if (state.restEndsAt <= 0) return@LaunchedEffect
        delay((state.restEndsAt - System.currentTimeMillis()).coerceAtLeast(0))
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
    }

    val view = LocalView.current
    DisposableEffect(state.active) {
        view.keepScreenOn = state.active
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Ink.bg), contentAlignment = Alignment.Center) {
        val resting = state.active && state.restEndsAt > now
        when {
            !link.connected.collectAsStateWithLifecycle().value && !state.active -> Message("Mauve Fit", L("Öffne die App auf dem Handy", "Open the app on your phone"))
            !state.active -> Idle(state.suggestion) { send("start") }
            resting -> Rest(state, now, bpm, onPlus = { send("plus15") }, onSkip = { send("skip") })
            else -> Active(state, now, bpm, ::send)
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(title, color = Ink.lime, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ink.muted, textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

@Composable
private fun Idle(suggestion: String, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
        Text("MAUVE FIT", color = Ink.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (suggestion.isBlank()) Text(L("Kein Training aktiv", "No active workout"), color = Ink.text, fontSize = 15.sp, textAlign = TextAlign.Center)
        else {
            Text(L("Nächstes Training", "Next workout"), color = Ink.muted, fontSize = 12.sp)
            Text(suggestion, color = Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            RoundAction(Icons.Rounded.PlayArrow, L("Starten", "Start"), Ink.lime, Ink.bg, 56, onStart)
        }
    }
}

@Composable
private fun Active(s: WatchState, now: Long, bpm: Int, send: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clock(now - s.startedAt), color = Ink.muted, fontSize = 12.sp)
            if (bpm > 0) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Favorite, null, tint = Ink.red, modifier = Modifier.size(12.dp))
                Text(" $bpm", color = Ink.text, fontSize = 12.sp)
            }
        }
        Text(s.exercise, color = Ink.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            if (s.warmup) L("Aufwärmen", "Warm-up") else L("Satz ${s.setNumber} von ${s.setCount}", "Set ${s.setNumber} of ${s.setCount}"),
            color = if (s.warmup) Ink.mauve else Ink.lime, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        if (!s.bodyweight) Stepper("${fmt(s.weight)} kg", { send("w-") }, { send("w+") })
        Stepper(L("${s.reps} Wdh.", "${s.reps} reps"), { send("r-") }, { send("r+") })
        Spacer(Modifier.height(4.dp))
        RoundAction(Icons.Rounded.Check, L("Satz erledigt", "Set done"), Ink.lime, Ink.bg, 48) { send("done") }
    }
}

@Composable
private fun Rest(s: WatchState, now: Long, bpm: Int, onPlus: () -> Unit, onSkip: () -> Unit) {
    val left = (s.restEndsAt - now).coerceAtLeast(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = left / (s.restTotalSec * 1000f).coerceAtLeast(1f),
            modifier = Modifier.fillMaxSize().padding(4.dp),
            indicatorColor = Ink.lime,
            trackColor = Ink.surface,
            strokeWidth = 6.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(L("PAUSE", "REST"), color = Ink.lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(clock(left), color = Ink.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            if (bpm > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = Ink.red, modifier = Modifier.size(12.dp))
                Text(" $bpm bpm", color = Ink.muted, fontSize = 12.sp)
            }
            Text(L("Nächster: ${s.exercise}", "Up next: ${s.exercise}"), color = Ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 30.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Ink.surface).clickable(onClick = onPlus),
                    contentAlignment = Alignment.Center,
                ) { Text("+15", color = Ink.text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                RoundAction(Icons.Rounded.SkipNext, L("Überspringen", "Skip"), Ink.surface, Ink.text, 44, onSkip)
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundAction(Icons.Rounded.Remove, L("Weniger", "Less"), Ink.surface, Ink.text, 30, onMinus)
        Text(value, color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.width(92.dp))
        RoundAction(Icons.Rounded.Add, L("Mehr", "More"), Ink.surface, Ink.text, 30, onPlus)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, bg: Color, fg: Color, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size((size * 0.5).dp)) }
}

/** German or English, following the watch language. */
private fun L(de: String, en: String) = if (java.util.Locale.getDefault().language == "de") de else en

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v).replace('.', ',')

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
