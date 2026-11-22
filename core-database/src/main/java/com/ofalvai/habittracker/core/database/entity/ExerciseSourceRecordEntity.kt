package com.ofalvai.habittracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Local-only source identity for one mapped habit and local date.
 *
 * This table is NOT a habit completion and contains no health measurements.
 * Future automatic completion must write this transactionally with Action.
 * The composite key makes repeated imports idempotent, including cross-midnight
 * sessions attributed to more than one local day.
 */
@Entity(
    tableName = "ExerciseSourceRecord",
    primaryKeys = ["habit_id", "local_date", "provider_record_id"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseHabitMappingEntity::class,
            parentColumns = ["habit_id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExerciseSourceRecordEntity(
    @ColumnInfo(name = "habit_id") val habitId: Int,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "provider_record_id") val providerRecordId: String
) {
    init {
        require(habitId > 0)
        require(localDate.isNotBlank())
        require(providerRecordId.isNotBlank())
    }
}
