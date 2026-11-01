package dev.mauve.fit.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.mauve.fit.App
import dev.mauve.fit.data.Cycle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Period dates from Health Connect, only when the user switched on cycle-based training and chose
 * it as the source. Its own permission, asked separately from the recovery ones, so turning on the
 * readiness score never touches cycle data. Only the last start and the usual length are kept.
 */
object CycleHealth {
    val permissions = setOf(HealthPermission.getReadPermission(MenstruationPeriodRecord::class))

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean =
        available(context) && runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)

    /**
     * Reads about half a year of periods and stores the latest start (and the usual length, when
     * there are enough of them). Returns false when there was nothing to read or no access.
     */
    suspend fun sync(context: Context): Boolean {
        val c = App.store.cycle.value
        if (!c.on || c.source != "health" || !granted(context)) return false
        val now = Instant.now()
        val range = TimeRangeFilter.between(now.minus(Duration.ofDays(200)), now)
        val starts = runCatching {
            HealthConnectClient.getOrCreate(context).readRecords(ReadRecordsRequest(MenstruationPeriodRecord::class, range)).records
                .map { r -> r.startTime.atZone(r.startZoneOffset ?: ZoneId.systemDefault()).toLocalDate() }
        }.getOrNull().orEmpty()
        val last = starts.maxOrNull() ?: return false
        val length = Cycle.lengthFrom(starts)
        // Switched off or back to manual meanwhile: keep nothing.
        App.store.updateCycle { if (it.on && it.source == "health") it.copy(lastStart = last.toString(), length = length ?: it.length) else it }
        return true
    }
}
