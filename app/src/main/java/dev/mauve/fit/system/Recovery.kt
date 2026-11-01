package dev.mauve.fit.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.mauve.fit.data.L
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Readiness from watch data. A Galaxy Watch records sleep, resting heart rate and HRV in Samsung
 * Health, which shares them with Health Connect; this reads the last nights and compares today
 * with the person's own two-week baseline. Nothing leaves the phone.
 */
object Recovery {
    val permissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    /** Today's picture: each value is null when the watch has not recorded it. */
    data class Today(
        val sleepMinutes: Int?,
        val restingHr: Int?,
        val restingBase: Int?,
        val hrv: Int?,
        val hrvBase: Int?,
        val score: Int,
    ) {
        val advice: String get() = when {
            score >= 75 -> L("Gut erholt. Heute darf es schwer werden.", "Well recovered. Today can be a hard one.")
            score >= 50 -> L("Normal erholt. Trainiere wie geplant und hör auf deinen Körper.", "Normally recovered. Train as planned and listen to your body.")
            else -> L("Wenig erholt. Heute lieber die Kurzversion oder ein Ruhetag.", "Poorly recovered. Better take the short version or a rest day today.")
        }
    }

    private val state = MutableStateFlow<Today?>(null)
    val today: StateFlow<Today?> = state

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean =
        available(context) && runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)

    /** Reads the last two weeks and updates [today]; quietly does nothing without access. */
    suspend fun refresh(context: Context) {
        if (!granted(context)) { state.value = null; return }
        val client = HealthConnectClient.getOrCreate(context)
        val now = Instant.now()
        val since = now.minus(Duration.ofDays(15))
        val range = TimeRangeFilter.between(since, now)
        runCatching {
            val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
            val resting = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range)).records
            val hrv = client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records

            // Last night: sessions that ended in the past 18 hours (naps included).
            val night = sleep.filter { it.endTime.isAfter(now.minus(Duration.ofHours(18))) }
            val sleepMin = night.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt().takeIf { night.isNotEmpty() }

            val dayAgo = now.minus(Duration.ofHours(24))
            val hrToday = resting.filter { it.time.isAfter(dayAgo) }.map { it.beatsPerMinute.toDouble() }.averageOrNull()
            val hrBase = resting.filter { !it.time.isAfter(dayAgo) }.map { it.beatsPerMinute.toDouble() }.averageOrNull()
            val hrvToday = hrv.filter { it.time.isAfter(dayAgo) }.map { it.heartRateVariabilityMillis }.averageOrNull()
            val hrvBase = hrv.filter { !it.time.isAfter(dayAgo) }.map { it.heartRateVariabilityMillis }.averageOrNull()

            if (sleepMin == null && hrToday == null && hrvToday == null) { state.value = null; return }
            state.value = Today(
                sleepMin, hrToday?.roundToInt(), hrBase?.roundToInt(), hrvToday?.roundToInt(), hrvBase?.roundToInt(),
                score(sleepMin, hrToday, hrBase, hrvToday, hrvBase),
            )
        }
    }

    /**
     * 100 minus penalties: short sleep (under 7 h), a resting pulse above the baseline and an HRV
     * below it. Each part is capped so one bad value alone never reads as "do not train".
     */
    fun score(sleepMin: Int?, hr: Double?, hrBase: Double?, hrv: Double?, hrvBase: Double?): Int {
        var s = 100.0
        if (sleepMin != null) s -= ((420 - sleepMin) / 60.0 * 12).coerceIn(0.0, 40.0)
        if (hr != null && hrBase != null) s -= ((hr - hrBase) * 4).coerceIn(0.0, 30.0)
        if (hrv != null && hrvBase != null && hrvBase > 0) s -= ((1 - hrv / hrvBase) * 100).coerceIn(0.0, 30.0)
        return s.roundToInt().coerceIn(0, 100)
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
}
