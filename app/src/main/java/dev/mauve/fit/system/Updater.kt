package dev.mauve.fit.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.mauve.fit.data.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the GitHub releases of the app's repository: checks the latest release, downloads
 * its phone APK and hands it to the system installer. Same signing key, so it installs over the
 * running app and keeps all data.
 */
object Updater {
    const val REPO = "mauricekleindienst/MauveFit"
    private const val PREFS = "updater"

    data class Release(val version: String, val apkUrl: String, val size: Long, val notes: String, val page: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun current(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** "1.20.0" newer than "1.19.17": compared part by part as numbers. */
    fun newer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Asks GitHub for the latest release; [quiet] keeps errors out of the state (background checks). */
    suspend fun check(context: Context, quiet: Boolean = false) {
        if (_state.value is State.Downloading) return
        if (!quiet) _state.value = State.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val o = json.parseToJsonElement(body).jsonObject
                val tag = o["tag_name"]?.jsonPrimitive?.content ?: error("no tag")
                val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { a ->
                    val name = a["name"]?.jsonPrimitive?.content.orEmpty()
                    name.startsWith("MauveFit-") && name.endsWith(".apk") && !name.contains("Watch", ignoreCase = true)
                } ?: error("no apk")
                Release(
                    version = tag.trimStart('v', 'V'),
                    apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
                    size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    notes = o["body"]?.jsonPrimitive?.content.orEmpty(),
                    page = o["html_url"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }
        result.onSuccess { r ->
            // Only a check that got an answer counts; offline, the next start tries again.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("checked", System.currentTimeMillis()).apply()
            _state.value = if (newer(r.version, current(context))) State.Available(r) else if (quiet) State.Idle else State.UpToDate
        }.onFailure {
            if (!quiet) _state.value = State.Failed(L("Keine Verbindung zu GitHub", "Could not reach GitHub"))
        }
    }

    /** A quiet check at most once every 12 hours, e.g. when Home opens. */
    suspend fun autoCheck(context: Context) {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("checked", 0)
        if (System.currentTimeMillis() - last > 12 * 3_600_000L && _state.value == State.Idle) check(context, quiet = true)
    }

    /** Downloads the release's APK into the cache, reporting progress, then opens the installer. */
    suspend fun download(context: Context, r: Release) {
        _state.value = State.Downloading(r, 0f)
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "MauveFit-${r.version}.apk")
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.value = State.Downloading(r, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                out
            }.getOrNull()
        }
        if (file == null) { _state.value = State.Failed(L("Download fehlgeschlagen", "Download failed")); return }
        _state.value = State.Ready(r, file)
        install(context, file)
    }

    /**
     * Opens the system installer. Android asks once to allow installs from this app; until then
     * its settings page opens instead, and "Install" can be tapped again after.
     */
    fun install(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
