package com.ofalvai.habittracker.feature.misc.healthconnect

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata only. Never stores Health Connect records, exercise durations or source IDs. */
data class ExerciseSyncReceipt(
    val finishedAtEpochMillis: Long,
    val applied: Int,
    val unchanged: Int,
    val failed: Int
) {
    init {
        require(finishedAtEpochMillis > 0L)
        require(applied >= 0 && unchanged >= 0 && failed >= 0)
    }
}

/** Versioned, local-only completion metadata retained across app restarts. */
@Singleton
class ExerciseSyncReceiptStore @Inject constructor(
    private val preferences: SharedPreferences
) {
    fun read(): ExerciseSyncReceipt? = decodeExerciseSyncReceipt(
        preferences.getString(KEY, null)
    )

    fun write(receipt: ExerciseSyncReceipt) {
        preferences.edit().putString(KEY, encodeExerciseSyncReceipt(receipt)).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "health_connect_last_finished_foreground_sync_v1"
    }
}

internal fun encodeExerciseSyncReceipt(receipt: ExerciseSyncReceipt): String =
    "v1|${receipt.finishedAtEpochMillis}|${receipt.applied}|${receipt.unchanged}|${receipt.failed}"

internal fun decodeExerciseSyncReceipt(raw: String?): ExerciseSyncReceipt? {
    if (raw.isNullOrBlank()) return null
    val fields = raw.split('|')
    if (fields.size != 5 || fields[0] != "v1") return null
    val finished = fields[1].toLongOrNull() ?: return null
    val applied = fields[2].toIntOrNull() ?: return null
    val unchanged = fields[3].toIntOrNull() ?: return null
    val failed = fields[4].toIntOrNull() ?: return null
    if (finished <= 0L || applied < 0 || unchanged < 0 || failed < 0) return null
    return ExerciseSyncReceipt(finished, applied, unchanged, failed)
}
