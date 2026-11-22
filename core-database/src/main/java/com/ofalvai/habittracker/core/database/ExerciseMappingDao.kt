package com.ofalvai.habittracker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.ExerciseSourceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseMappingDao {
    @Query("SELECT * FROM ExerciseHabitMapping ORDER BY habit_id ASC")
    fun observeMappings(): Flow<List<ExerciseHabitMappingEntity>>

    @Query("SELECT * FROM ExerciseHabitMapping WHERE habit_id = :habitId LIMIT 1")
    suspend fun getMapping(habitId: Int): ExerciseHabitMappingEntity?

    /** Replacing a mapping intentionally clears its old source provenance via FK CASCADE. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(mapping: ExerciseHabitMappingEntity)

    /**
     * Disconnect the on-device mapping setup. Foreign keys clear source IDs,
     * but the historical completion ledger and actual Actions are preserved.
     */
    @Query("DELETE FROM ExerciseHabitMapping")
    suspend fun removeAllMappings()

    @Query("DELETE FROM ExerciseHabitMapping WHERE habit_id = :habitId")
    suspend fun removeMapping(habitId: Int)

    /** Unique key avoids duplicate records after repeated sync/retry. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceRecords(records: List<ExerciseSourceRecordEntity>): List<Long>

    @Query("SELECT * FROM ExerciseSourceRecord WHERE habit_id = :habitId AND local_date = :localDate ORDER BY provider_record_id ASC")
    suspend fun getSourceRecords(habitId: Int, localDate: String): List<ExerciseSourceRecordEntity>

    @Query("SELECT COUNT(*) FROM ExerciseSourceRecord WHERE habit_id = :habitId")
    suspend fun getSourceRecordCount(habitId: Int): Int
}
