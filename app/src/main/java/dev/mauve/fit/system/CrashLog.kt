package dev.mauve.fit.system

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the last crashes on the device, so a problem can be shared from the settings. Nothing is
 * sent anywhere by itself; the previous handler still runs, so Android shows its usual dialog.
 */
object CrashLog {
    private const val KEEP = 10

    private fun dir(context: Context) = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine("Mauve Fit $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("${LocalDateTime.now()} · thread ${thread.name}")
            appendLine()
            append(trace)
        }
        File(dir(context), "${System.currentTimeMillis()}.txt").writeText(text)
        list(context).drop(KEEP).forEach { it.delete() }
    }

    /** Newest first. */
    fun list(context: Context): List<File> = dir(context).listFiles { f -> f.extension == "txt" }.orEmpty().sortedByDescending { it.name }

    /** All reports in one text, newest first, for sharing. */
    fun text(context: Context): String = list(context).joinToString("\n\n----------\n\n") { it.readText() }

    fun clear(context: Context) = list(context).forEach { it.delete() }
}
