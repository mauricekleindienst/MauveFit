package dev.mauve.fit

import dev.mauve.fit.system.CrashLog
import dev.mauve.fit.system.WeeklyReports
import android.app.Application
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.data.ExerciseRepo
import dev.mauve.fit.data.I18n
import dev.mauve.fit.data.Store
import dev.mauve.fit.system.Reminders
import dev.mauve.fit.system.WorkoutService
import dev.mauve.fit.system.createChannels
import dev.mauve.fit.watch.WatchSync
import dev.mauve.fit.widget.NextWidget
import kotlin.concurrent.thread

class App : Application() {
    /** The phone's language changed: with the setting on "system" the app follows, notifications too. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        I18n.apply(store.data.value.settings.language)
        dev.mauve.fit.system.createChannels(this)
        store.relocalizePlans()
    }

    override fun onCreate() {
        super.onCreate()
        // First, so even a crash while loading the store is kept for the settings.
        CrashLog.install(this)
        store = Store(this)
        I18n.apply(store.data.value.settings.language)
        // Generated plans named in another language (made before a language switch) follow along.
        store.relocalizePlans()
        repo = ExerciseRepo(this)
        // Parse the exercise list now so the first screen that needs it doesn't stall.
        createChannels(this)
        Haptics.init(this)
        // A workout forgotten while the app was closed ends here, before anything shows it.
        if (store.closeIdleWorkout()) WorkoutService.closedNotice(this)
        // A missed week spends a freeze before the streak is shown anywhere.
        store.settleStreak()
        WorkoutService.follow(this)
        Reminders.follow(this)
        WeeklyReports.follow(this)
        dev.mauve.fit.system.HealthSync.follow(this)
        NextWidget.follow(this)
        thread(name = "exercise-db") {
            repo.warm()
            WatchSync.start(this)
        }
    }


    companion object {
        lateinit var store: Store
            private set
        lateinit var repo: ExerciseRepo
            private set
    }
}
