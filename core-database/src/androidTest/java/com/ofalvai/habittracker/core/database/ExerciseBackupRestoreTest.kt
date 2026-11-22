package com.ofalvai.habittracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.database.entity.Action
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.Habit
import com.ofalvai.habittracker.core.testing.BaseInstrumentedTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the same transactional HabitDao.restoreBackup() used by manual ZIP restore.
 * All records and IDs are synthetic; no Health Connect provider or permissions are used.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseBackupRestoreTest : BaseInstrumentedTest() {
    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var mappingDao: ExerciseMappingDao
    private lateinit var autoDao: ExerciseAutoCompletionDao

    private val day = LocalDate.of(2026, 11, 21)
    private val mapping = ExerciseHabitMappingEntity(42, "RUNNING", 30, "Asia/Kolkata")
    private val duration = Duration.ofMinutes(35)

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        habitDao = database.habitDao()
        mappingDao = database.exerciseMappingDao()
        autoDao = database.exerciseAutoCompletionDao()
    }

    @After fun tearDown() {
        database.close()
    }

    private suspend fun seedHealthCompletion() {
        habitDao.insertHabits(listOf(
            Habit(42, "Original running habit", Habit.Color.Green, 0, false, "")
        ))
        mappingDao.upsertMapping(mapping)
        assertTrue(autoDao.applyEligibleDay(
            mapping, day, duration, setOf("synthetic-exercise-1")
        ) is ExerciseApplyResult.Applied)
    }

    @Test fun restoringSameHabitIdReplacesOldHealthStateAndKeepsImportedManualAction() = runTest {
        seedHealthCompletion()
        val autoAction = habitDao.getActionsForHabit(42).single()
        habitDao.deleteAction(autoAction.id)
        assertNull(autoDao.getLedger(42, day.toString())!!.actionId)

        // Manual ZIP does not contain Health Connect mapping or undo metadata.
        val restoredHabit = Habit(42, "Imported running habit", Habit.Color.Blue, 0, false, "ZIP")
        val restoredAction = Action(
            id = 500, habit_id = 42,
            timestamp = Instant.parse("2026-11-21T06:00:00Z")
        )
        habitDao.restoreBackup(listOf(restoredHabit), listOf(restoredAction))

        assertEquals(listOf(restoredHabit), habitDao.getHabits())
        assertEquals(listOf(restoredAction), habitDao.getActionsForHabit(42))
        assertNull(mappingDao.getMapping(42))
        assertNull(autoDao.getLedger(42, day.toString()))
        assertEquals(0, mappingDao.getSourceRecordCount(42))

        // Reconnecting the same habit cannot overwrite the imported manual Action.
        mappingDao.upsertMapping(mapping)
        assertEquals(
            ExerciseApplyResult.ExistingCompletion,
            autoDao.applyEligibleDay(mapping, day, duration, setOf("new-synthetic-record"))
        )
        assertEquals(listOf(restoredAction), habitDao.getActionsForHabit(42))
    }

    @Test fun invalidRestoreRollsBackHabitDeletionAndHealthLedgerTogether() = runTest {
        seedHealthCompletion()
        val priorHabit = habitDao.getHabits().single()
        val priorAction = habitDao.getActionsForHabit(42).single()
        val priorLedger = autoDao.getLedger(42, day.toString())
        assertNotNull(priorLedger)
        assertEquals(1, mappingDao.getSourceRecordCount(42))

        // An invalid action FK must abort the entire Room transaction, not wipe
        // the existing habits and user-visible Health Connect audit history.
        val invalidAction = Action(
            id = 501, habit_id = 999,
            timestamp = Instant.parse("2026-11-21T09:00:00Z")
        )
        var rejected = false
        try {
            habitDao.restoreBackup(
                listOf(Habit(43, "Bad ZIP", Habit.Color.Red, 0, false, "")),
                listOf(invalidAction)
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Invalid foreign-key backup must be rejected", rejected)

        assertEquals(listOf(priorHabit), habitDao.getHabits())
        assertEquals(listOf(priorAction), habitDao.getActionsForHabit(42))
        assertEquals(mapping, mappingDao.getMapping(42))
        assertEquals(priorLedger, autoDao.getLedger(42, day.toString()))
        assertEquals(1, mappingDao.getSourceRecordCount(42))
        assertTrue(habitDao.getActionsForHabit(43).isEmpty())
    }
}
