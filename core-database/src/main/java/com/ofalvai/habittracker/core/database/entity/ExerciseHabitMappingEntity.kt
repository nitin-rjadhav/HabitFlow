package com.ofalvai.habittracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Each habit has at most one explicit exercise mapping. The app never creates
 * one automatically; selecting and removing it is a deliberate user action.
 */
@Entity(
    tableName = "ExerciseHabitMapping",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExerciseHabitMappingEntity(
    @PrimaryKey @ColumnInfo(name = "habit_id") val habitId: Int,
    @ColumnInfo(name = "exercise_kind") val exerciseKind: String,
    @ColumnInfo(name = "minimum_duration_minutes") val minimumDurationMinutes: Int,
    @ColumnInfo(name = "zone_id") val zoneId: String
) {
    init {
        require(habitId > 0)
        require(exerciseKind in setOf("WALKING", "RUNNING", "CYCLING"))
        require(minimumDurationMinutes in 1..1440)
        require(zoneId.isNotBlank())
    }
}
