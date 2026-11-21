package com.ofalvai.habittracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Durable opt-in completion provenance keyed by habit and LOCAL calendar day.
 *
 * If a user removes an automatic Action, the action_id becomes NULL by
 * foreign-key SET_NULL, preserving a do-not-reapply tombstone for this day.
 * Replacing/removing a mapping must not erase historical user decisions.
 */
@Entity(
    tableName = "ExerciseAutoCompletionLedger",
    primaryKeys = ["habit_id", "local_date"],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Action::class,
            parentColumns = ["id"],
            childColumns = ["action_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("action_id")]
)
data class ExerciseAutoCompletionLedger(
    @ColumnInfo(name = "habit_id") val habitId: Int,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "action_id") val actionId: Int?,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "source_record_count") val sourceRecordCount: Int
)
