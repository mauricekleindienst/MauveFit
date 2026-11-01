package dev.mauve.fit.wear

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** Live heart rate from the watch sensor while the app is open; 0 until a reading arrives. */
class HeartRate(context: Context) {
    private val client = HealthServices.getClient(context).measureClient
    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { _bpm.value = it.value.roundToInt() }
        }
    }

    fun start() {
        if (registered) return
        // Fails without the sensor permission; the activity asks for it and calls start() again.
        registered = runCatching { client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback) }.isSuccess
    }

    fun stop() {
        if (!registered) return
        runCatching { client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
        registered = false
    }
}
