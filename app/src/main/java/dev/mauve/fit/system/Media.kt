package dev.mauve.fit.system

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dev.mauve.fit.App
import dev.mauve.fit.data.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Keeps exercise media available offline: plan exercises are fetched in the background (on any
 * network, they are only a few MB), and the whole library can be downloaded on request. Files land
 * in Coil's disk cache, which the media views read from; the settings can show and clear it.
 */
object Media {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Progress of a full download: done to total, or null when none is running. */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress

    private fun urls(e: Exercise) = listOf(e.imageUrl, e.gifUrl)

    private suspend fun fetch(context: Context, url: String): Boolean {
        val request = ImageRequest.Builder(context).data(url)
            .memoryCachePolicy(CachePolicy.DISABLED) // only the disk copy is wanted
            .build()
        return SingletonImageLoader.get(context).execute(request) is SuccessResult
    }

    private suspend fun fetchAll(context: Context, exercises: List<Exercise>, report: Boolean) {
        val gate = Semaphore(6)
        var done = 0
        val total = exercises.size
        if (report) _progress.value = 0 to total
        // Children of this call, not of the object's scope: cancelling the download stops them all.
        try {
            coroutineScope {
                exercises.forEach { e ->
                    launch {
                        gate.withPermit { urls(e).forEach { fetch(context, it) } }
                        synchronized(this@Media) { done++ }
                        if (report) _progress.value = done to total
                    }
                }
            }
        } finally {
            if (report) _progress.value = null
        }
    }

    /** At start: every exercise in the plans, so workouts run without a connection. */
    fun prefetchPlans(context: Context) {
        scope.launch {
            val d = App.store.data.value
            val ids = d.routines.flatMap { r -> r.exercises.map { it.exerciseId } }.distinct()
            fetchAll(context, ids.mapNotNull { App.repo[it] }, report = false)
        }
    }

    /** Downloads every animation and still; started from the settings. */
    fun downloadAll(context: Context) {
        if (job?.isActive == true) return
        job = scope.launch { fetchAll(context, App.repo.all, report = true) }
    }

    /** Bytes currently cached. */
    fun cacheSize(context: Context): Long = SingletonImageLoader.get(context).diskCache?.size ?: 0L

    /** Deletes every cached animation and still; they load again when next shown. */
    fun clear(context: Context) {
        cancel()
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun cancel() {
        job?.cancel()
        _progress.value = null
    }
}
