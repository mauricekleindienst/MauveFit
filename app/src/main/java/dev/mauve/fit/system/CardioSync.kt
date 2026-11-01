package dev.mauve.fit.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.mauve.fit.data.CardioKind
import dev.mauve.fit.data.CardioSession
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Cardio from the watch: runs, rides, rows and the like that Samsung Health (or any other app)
 * shares with Health Connect. Only asked for once the readiness permissions of [Recovery] are in
 * place; strength sessions are skipped, those are logged in the app itself.
 */
object CardioSync {
    /** Needed for the import at all. */
    private val required = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    /** Nice to have: the import still works without distance or pulse. */
    private val optional = setOf(
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    val permissions = required + optional

    /** Health Connect exercise types that count as cardio, mapped to the app's kinds. */
    private val kinds = mapOf(
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to CardioKind.Run,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to CardioKind.Run,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING to CardioKind.Bike,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to CardioKind.Bike,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING to CardioKind.Row,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to CardioKind.Row,
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING to CardioKind.Walk,
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING to CardioKind.Walk,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to CardioKind.Swim,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to CardioKind.Swim,
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to CardioKind.Other,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING to CardioKind.Other,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to CardioKind.Other,
    )

    private suspend fun grantedSet(context: Context): Set<String> =
        if (!Recovery.available(context)) emptySet()
        else runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    suspend fun granted(context: Context): Boolean = grantedSet(context).containsAll(required)

    /**
     * Cardio sessions of the last [days] days, oldest first, or null without access. Sessions
     * shorter than five minutes are dropped as accidental starts.
     */
    suspend fun recent(context: Context, days: Long = 14): List<CardioSession>? {
        val have = grantedSet(context)
        if (!have.containsAll(required)) return null
        val client = HealthConnectClient.getOrCreate(context)
        val now = Instant.now()
        return runCatching {
            val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now))).records
            sessions.mapNotNull { r ->
                val kind = kinds[r.exerciseType] ?: return@mapNotNull null
                val minutes = Duration.between(r.startTime, r.endTime).toMinutes().toInt()
                if (minutes < 5) return@mapNotNull null
                val range = TimeRangeFilter.between(r.startTime, r.endTime)
                val km = if (HealthPermission.getReadPermission(DistanceRecord::class) !in have) null
                else runCatching {
                    client.readRecords(ReadRecordsRequest(DistanceRecord::class, range)).records.sumOf { it.distance.inKilometers }
                }.getOrNull()?.takeIf { it > 0.05 }
                val hr = if (HealthPermission.getReadPermission(HeartRateRecord::class) !in have) null
                else runCatching {
                    client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records
                        .flatMap { it.samples }.map { it.beatsPerMinute }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                }.getOrNull()
                CardioSession(at = r.startTime.toEpochMilli(), kind = kind, minutes = minutes, distanceKm = km, avgHr = hr, source = "health", note = r.title.orEmpty())
            }.sortedBy { it.at }
        }.getOrNull()
    }
}
