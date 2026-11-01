package dev.mauve.fit.system

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.mauve.fit.App

/**
 * One place for all haptic feedback, graded by how much an action matters. Everything is skipped
 * when vibration is switched off in the settings.
 */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }

    private val enabled get() = App.store.data.value.settings.vibrate

    private fun predefined(effect: Int) {
        if (enabled) runCatching { vibrator?.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    private fun pattern(timings: LongArray, amplitudes: IntArray) {
        if (enabled) runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
    }

    /** Steppers, tabs, filters, page changes. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK)

    /** Buttons, choices, switches. */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK)

    /** A set ticked off. */
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    /** An exercise or the whole workout completed. */
    fun success() = predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** Rest is over: three firm pulses. */
    fun restOver() = pattern(longArrayOf(0, 120, 90, 120, 90, 220), intArrayOf(0, 255, 0, 255, 0, 255))

    /** A new personal record: a rising build-up ending in a strong hit. */
    fun record() = pattern(longArrayOf(0, 40, 60, 60, 60, 90, 80, 260), intArrayOf(0, 80, 0, 150, 0, 210, 0, 255))

    /** Something destructive was confirmed. */
    fun warn() = pattern(longArrayOf(0, 60, 70, 60), intArrayOf(0, 200, 0, 200))
}
