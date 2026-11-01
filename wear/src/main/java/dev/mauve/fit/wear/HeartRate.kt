package dev.mauve.fit.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** Wear OS 6 replaced BODY_SENSORS with a granular health permission. */
val heartPermission: String =
    if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

/** Live heart rate from the watch sensor while a workout is open; 0 when there is no fresh reading. */
class HeartRate(context: Context) {
    private val app = context.applicationContext
    private val client = HealthServices.getClient(app).measureClient
    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            // Off the wrist or still acquiring: hide the old value instead of showing it as live.
            if (availability is DataTypeAvailability && availability != DataTypeAvailability.AVAILABLE) _bpm.value = 0
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.takeIf { it > 0 }?.let { _bpm.value = it.roundToInt() }
        }

        // Registration errors (no sensor, permission revoked) arrive here, not as an exception.
        override fun onRegistrationFailed(throwable: Throwable) {
            registered = false
            _bpm.value = 0
        }
    }

    fun granted() = ContextCompat.checkSelfPermission(app, heartPermission) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (registered || !granted()) return
        registered = runCatching { client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback) }.isSuccess
    }

    fun stop() {
        if (!registered) return
        runCatching { client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
        registered = false
        _bpm.value = 0
    }
}
