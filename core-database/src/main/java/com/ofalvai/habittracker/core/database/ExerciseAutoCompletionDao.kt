package com.ofalvai.habittracker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ofalvai.habittracker.core.database.entity.Action
import com.ofalvai.habittracker.core.database.entity.ExerciseAutoCompletionLedger
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.ExerciseSourceRecordEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface ExerciseApplyResult {
    data class Applied(val actionId: Int) : ExerciseApplyResult
    data object MappingChanged : ExerciseApplyResult
    data object Ineligible : ExerciseApplyResult
    data object AlreadyProcessed : ExerciseApplyResult
    data object ExistingCompletion : ExerciseApplyResult
    data object MissingOrArchivedHabit : ExerciseApplyResult
}

/**
 * No external Health Connect API inside the transaction. A future coordinator
 * must check permission, read sessions, and match the day before calling this
 * method. This DAO only performs atomic local changes.
 */
@Dao
interface ExerciseAutoCompletionDao {
    @Query("SELECT * FROM ExerciseHabitMapping WHERE habit_id = :habitId LIMIT 1")
    suspend fun getMapping(habitId: Int): ExerciseHabitMappingEntity?

    @Query("SELECT COUNT(*) FROM Habit WHERE id = :habitId AND archived = 0")
    suspend fun countActiveHabits(habitId: Int): Int

    @Query("SELECT COUNT(*) FROM Action WHERE habit_id = :habitId AND timestamp >= :dayStart AND timestamp < :nextDay")
    suspend fun countExistingActions(habitId: Int, dayStart: Instant, nextDay: Instant): Int

    @Query("SELECT * FROM ExerciseAutoCompletionLedger WHERE habit_id = :habitId AND local_date = :day LIMIT 1")
    suspend fun getLedger(habitId: Int, day: String): ExerciseAutoCompletionLedger?

    @Insert
    suspend fun insertAutomaticAction(action: Action): Long

    @Insert
    suspend fun insertLedger(entry: ExerciseAutoCompletionLedger)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUniqueSources(records: List<ExerciseSourceRecordEntity>)

    /**
     * Idempotent source-to-Action write. Never touches existing manual Actions.
     * The ledger survives user deletion of the auto-created Action, so a later
     * retry cannot re-create an explicitly undone completion.
     */
    @Transaction
    suspend fun applyEligibleDay(
        mapping: ExerciseHabitMappingEntity,
        day: LocalDate,
        duration: Duration,
        supportingRecordIds: Set<String>
    ): ExerciseApplyResult {
        val current = getMapping(mapping.habitId)
        if (current == null || current != mapping) {
            return ExerciseApplyResult.MappingChanged
        }
        if (supportingRecordIds.isEmpty() ||
            supportingRecordIds.any { it.isBlank() } ||
            duration < Duration.ofMinutes(mapping.minimumDurationMinutes.toLong())
        ) {
            return ExerciseApplyResult.Ineligible
        }
        if (countActiveHabits(mapping.habitId) != 1) {
            return ExerciseApplyResult.MissingOrArchivedHabit
        }
        val dayString = day.toString()
        if (getLedger(mapping.habitId, dayString) != null) {
            return ExerciseApplyResult.AlreadyProcessed
        }
        val zone = ZoneId.of(mapping.zoneId)
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        if (countExistingActions(mapping.habitId, start, end) > 0) {
            return ExerciseApplyResult.ExistingCompletion
        }
        val actionTimestamp = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val actionId = insertAutomaticAction(
            Action(habit_id = mapping.habitId, timestamp = actionTimestamp)
        ).toInt()
        insertLedger(
            ExerciseAutoCompletionLedger(
                habitId = mapping.habitId,
                localDate = dayString,
                actionId = actionId,
                durationMillis = duration.toMillis(),
                sourceRecordCount = supportingRecordIds.size
            )
        )
        insertUniqueSources(supportingRecordIds.sorted().map { id ->
            ExerciseSourceRecordEntity(mapping.habitId, dayString, id)
        })
        return ExerciseApplyResult.Applied(actionId)
    }
}
