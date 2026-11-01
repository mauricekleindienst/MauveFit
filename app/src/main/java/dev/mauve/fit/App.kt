package dev.mauve.fit

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.system.Media
import dev.mauve.fit.data.ExerciseRepo
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.Store
import dev.mauve.fit.system.Reminders
import dev.mauve.fit.system.WorkoutService
import dev.mauve.fit.system.createChannels
import dev.mauve.fit.watch.WatchSync
import kotlin.concurrent.thread

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        I18n.apply(store.data.value.settings.language)
        repo = ExerciseRepo(this)
        // Parse the exercise list now so the first screen that needs it doesn't stall.
        createChannels(this)
        Haptics.init(this)
        WorkoutService.follow(this)
        Reminders.follow(this)
        thread(name = "exercise-db") {
            repo.all
            WatchSync.start(this)
            Media.prefetchPlans(this)
        }
    }

    // GIFs need the animated decoder; the disk cache holds the downloaded media for offline use.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(dev.mauve.fit.system.SharpGifDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("media")).maxSizeBytes(700L * 1024 * 1024).build() }
            .crossfade(true)
            .build()

    companion object {
        lateinit var store: Store
            private set
        lateinit var repo: ExerciseRepo
            private set
    }
}
