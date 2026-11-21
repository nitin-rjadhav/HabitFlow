package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.feature.misc.healthconnect.HealthConnectGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** No read is attempted without explicit mapping AND previously granted permission. */
sealed interface ExerciseSessionReadResult {
    data class Success(val sessions: List<ExerciseSession>) : ExerciseSessionReadResult
    data object PermissionRequired : ExerciseSessionReadResult
    data object Unavailable : ExerciseSessionReadResult
    data class Failure(val message: String) : ExerciseSessionReadResult
}

/**
 * Foreground, on-demand, read-only Health Connect exercise session reader.
 * Not invoked during startup or mapping configuration; callers must explicitly
 * initiate a mapped date read, then apply matching rules before writing anything.
 */
internal const val GENERIC_EXERCISE_READ_ERROR = "Unable to read exercise sessions. Try again or check Health Connect access."

class HealthConnectExerciseReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: HealthConnectGateway
) {
    suspend fun readMappedDay(
        mapping: ExerciseHabitMappingEntity,
        day: LocalDate
    ): ExerciseSessionReadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !gateway.isAvailable()) {
            return ExerciseSessionReadResult.Unavailable
        }
        try {
            if (!gateway.hasExerciseReadPermission()) {
                return ExerciseSessionReadResult.PermissionRequired
            }

            // Reject corrupted/legacy mapping kinds before requesting any provider records.
            // A bad persisted value must never cause an unnecessary health-data read.
            val selectedKind = mapping.exerciseKind.toExerciseKindOrNull()
                ?: return ExerciseSessionReadResult.Failure(GENERIC_EXERCISE_READ_ERROR)
            val zone = ZoneId.of(mapping.zoneId)
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            // Include sessions starting before midnight which can overlap today.
            // Matching clips any returned session to the exact local day.
            val queryStart = dayStart.minus(Duration.ofDays(1))
            val client = HealthConnectClient.getOrCreate(context)
            val seenTokens = mutableSetOf<String>()
            val results = ArrayList<ExerciseSession>()
            var token: String? = null
            do {
                // Recheck before each subsequent page, not just after the final page.
                // If access is revoked mid-pagination, avoid another provider read.
                if (token != null && !gateway.hasExerciseReadPermission()) {
                    return ExerciseSessionReadResult.PermissionRequired
                }
                val page = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, dayEnd),
                        pageToken = token
                    )
                )
                for (record in page.records) {
                    // Ignore unrelated or unknown exercise types before building a non-null session.
                    val kind = exerciseKindForHealthConnect(record.exerciseType) ?: continue
                    if (kind != selectedKind) continue
                    // No empty IDs, no invalid sessions and no data from outside the day.
                    if (record.metadata.id.isBlank() ||
                        !record.endTime.isAfter(record.startTime) ||
                        !record.endTime.isAfter(dayStart) ||
                        !record.startTime.isBefore(dayEnd)
                    ) continue
                    results += ExerciseSession(
                        providerRecordId = record.metadata.id,
                        kind = kind,
                        startsAt = record.startTime,
                        endsAt = record.endTime
                    )
                }
                token = page.pageToken
                if (!token.isNullOrEmpty() && !seenTokens.add(token)) {
                    return ExerciseSessionReadResult.Failure(GENERIC_EXERCISE_READ_ERROR)
                }
            } while (!token.isNullOrEmpty())

            // Permission can be revoked while pages are being read. Never hand a stale
            // successful result to the completion flow after the final page.
            if (!gateway.hasExerciseReadPermission()) {
                return ExerciseSessionReadResult.PermissionRequired
            }
            return ExerciseSessionReadResult.Success(results.distinctBy { it.providerRecordId })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (revoked: SecurityException) {
            return ExerciseSessionReadResult.PermissionRequired
        } catch (error: Exception) {
            // Provider exception messages can contain record identifiers or other sensitive details.
            // Never surface those details in the UI or persist them in a sync receipt.
            return ExerciseSessionReadResult.Failure(GENERIC_EXERCISE_READ_ERROR)
        }
    }
}

fun String.toExerciseKindOrNull(): ExerciseKind? =
    ExerciseKind.values().firstOrNull { it.name == this }

/** Includes treadmill running and stationary cycling under their broader habit types. */
fun exerciseKindForHealthConnect(exerciseType: Int): ExerciseKind? = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ExerciseKind.WALKING
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ExerciseKind.RUNNING
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ExerciseKind.CYCLING
    else -> null
}
