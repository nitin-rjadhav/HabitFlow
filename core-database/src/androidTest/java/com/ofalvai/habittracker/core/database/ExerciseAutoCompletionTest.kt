package com.ofalvai.habittracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.database.entity.Action
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.Habit
import com.ofalvai.habittracker.core.database.entity.HabitById
import com.ofalvai.habittracker.core.testing.BaseInstrumentedTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseAutoCompletionTest : BaseInstrumentedTest() {
    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var mappingDao: ExerciseMappingDao
    private lateinit var autoDao: ExerciseAutoCompletionDao

    private val day = LocalDate.of(2026, 11, 21)
    private val mapping = ExerciseHabitMappingEntity(42, "RUNNING", 30, "Asia/Kolkata")

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        habitDao = database.habitDao()
        mappingDao = database.exerciseMappingDao()
        autoDao = database.exerciseAutoCompletionDao()
    }
    @After fun tearDown() { database.close() }

    private suspend fun setupMapping() {
        habitDao.insertHabits(listOf(Habit(42, "Morning run", Habit.Color.Green, 0, false, "")))
        mappingDao.upsertMapping(mapping)
    }
    private suspend fun apply() = autoDao.applyEligibleDay(
        mapping, day, Duration.ofMinutes(35), setOf("watch-1", "phone-2")
    )

    @Test fun eligibleDayCreatesOneActionAndLocalProvenance() = runTest {
        setupMapping()
        val applied = apply()
        assertTrue(applied is ExerciseApplyResult.Applied)
        val actions = habitDao.getActionsForHabit(42)
        assertEquals(1, actions.size)
        assertEquals(day, actions.single().timestamp.atZone(ZoneId.of(mapping.zoneId)).toLocalDate())
        assertEquals(2, mappingDao.getSourceRecordCount(42))
        val ledger = autoDao.getLedger(42, day.toString())
        assertNotNull(ledger)
        assertEquals(actions.single().id, ledger!!.actionId)
    }

    @Test fun retriesNeverDuplicateActionOrSourceRecords() = runTest {
        setupMapping()
        apply()
        assertEquals(ExerciseApplyResult.AlreadyProcessed, apply())
        assertEquals(1, habitDao.getActionsForHabit(42).size)
        assertEquals(2, mappingDao.getSourceRecordCount(42))
    }

    @Test fun manualCompletionBlocksAutoWrite() = runTest {
        setupMapping()
        habitDao.insertActions(listOf(Action(
            habit_id = 42, timestamp = Instant.parse("2026-11-21T06:15:00Z")
        )))
        assertEquals(ExerciseApplyResult.ExistingCompletion, apply())
        assertNull(autoDao.getLedger(42, day.toString()))
        assertEquals(1, habitDao.getActionsForHabit(42).size)
    }

    @Test fun historyRetainsAppliedThenUndoneStatusAfterDisconnect() = runTest {
        setupMapping()
        apply()
        val active = autoDao.observeRecentHistory().first()
        assertEquals(1, active.size)
        assertNotNull(active.single().actionId)

        val actionId = habitDao.getActionsForHabit(42).single().id
        habitDao.deleteAction(actionId)
        mappingDao.removeAllMappings()

        val undone = autoDao.observeRecentHistory().first()
        assertEquals(1, undone.size)
        assertNull(undone.single().actionId)
        assertEquals(35 * 60_000L, undone.single().durationMillis)
        assertEquals(0, mappingDao.getSourceRecordCount(42))
        assertEquals(0, habitDao.getActionsForHabit(42).size)
        assertEquals(ExerciseApplyResult.MappingChanged, apply())
    }

    @Test fun disconnectPreservesPreviouslyAppliedAction() = runTest {
        setupMapping()
        apply()
        mappingDao.removeAllMappings()
        assertEquals(1, habitDao.getActionsForHabit(42).size)
        assertNotNull(autoDao.observeRecentHistory().first().single().actionId)
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }

    @Test fun userUndoLeavesSuppressionLedgerAndPreventsReapply() = runTest {
        setupMapping()
        apply()
        val action = habitDao.getActionsForHabit(42).single()
        habitDao.deleteAction(action.id)
        val ledger = autoDao.getLedger(42, day.toString())
        assertNotNull(ledger)
        assertNull(ledger!!.actionId)
        assertEquals(ExerciseApplyResult.AlreadyProcessed, apply())
        assertEquals(0, habitDao.getActionsForHabit(42).size)
    }

    @Test fun belowThresholdAndMissingSourceNeverCreateActions() = runTest {
        setupMapping()
        assertEquals(
            ExerciseApplyResult.Ineligible,
            autoDao.applyEligibleDay(mapping, day, Duration.ofMinutes(20), setOf("run"))
        )
        assertEquals(
            ExerciseApplyResult.Ineligible,
            autoDao.applyEligibleDay(mapping, day, Duration.ofMinutes(40), emptySet())
        )
        assertTrue(habitDao.getActionsForHabit(42).isEmpty())
    }

    @Test fun changedMappingCannotWriteStaleCompletion() = runTest {
        setupMapping()
        mappingDao.upsertMapping(mapping.copy(minimumDurationMinutes = 60))
        assertEquals(ExerciseApplyResult.MappingChanged, apply())
        assertTrue(habitDao.getActionsForHabit(42).isEmpty())
    }

    @Test fun manualActionOnPreviousUtcDayBlocksSameLocalDayCompletion() = runTest {
        setupMapping()
        // 20:00 UTC on 20 Nov is 01:30 IST on 21 Nov, the mapped local day.
        habitDao.insertActions(listOf(Action(
            habit_id = 42, timestamp = Instant.parse("2026-11-20T20:00:00Z")
        )))

        assertEquals(ExerciseApplyResult.ExistingCompletion, apply())
        assertEquals(1, habitDao.getActionsForHabit(42).size)
        assertNull(autoDao.getLedger(42, day.toString()))
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }

    @Test fun manualActionAtNextLocalMidnightDoesNotBlockPreviousDay() = runTest {
        setupMapping()
        // 18:30 UTC on 21 Nov is exactly 00:00 IST on 22 Nov.
        // This manual Action must not suppress an eligible 21 Nov completion.
        val nextDayManual = Instant.parse("2026-11-21T18:30:00Z")
        habitDao.insertActions(listOf(Action(habit_id = 42, timestamp = nextDayManual)))

        assertTrue(apply() is ExerciseApplyResult.Applied)
        val actions = habitDao.getActionsForHabit(42)
        assertEquals(2, actions.size)
        assertTrue(actions.any { it.timestamp == nextDayManual })
        val ledger = autoDao.getLedger(42, day.toString())
        assertNotNull(ledger)
        assertEquals(
            day,
            actions.single { it.id == ledger!!.actionId }
                .timestamp.atZone(ZoneId.of(mapping.zoneId)).toLocalDate()
        )
    }

    @Test fun undoSuppressionSurvivesDisconnectAndReconnectingSameMapping() = runTest {
        setupMapping()
        assertTrue(apply() is ExerciseApplyResult.Applied)
        val actionId = habitDao.getActionsForHabit(42).single().id
        habitDao.deleteAction(actionId)
        val suppressed = autoDao.getLedger(42, day.toString())
        assertNotNull(suppressed)
        assertNull(suppressed!!.actionId)

        mappingDao.removeAllMappings()
        assertEquals(0, mappingDao.getSourceRecordCount(42))
        // Opting in again must not silently re-create a completion the user undid.
        mappingDao.upsertMapping(mapping)
        assertEquals(ExerciseApplyResult.AlreadyProcessed, apply())
        assertTrue(habitDao.getActionsForHabit(42).isEmpty())
        assertNull(autoDao.getLedger(42, day.toString())!!.actionId)
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }

    @Test fun deletingHabitRemovesLocalLedger() = runTest {
        setupMapping()
        apply()
        habitDao.deleteHabit(HabitById(42))
        assertNull(autoDao.getLedger(42, day.toString()))
    }

    @Test fun backupRestoreClearsImportedProvenance() = runTest {
        setupMapping()
        apply()
        habitDao.restoreBackup(emptyList(), emptyList())
        assertNull(autoDao.getLedger(42, day.toString()))
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }
}
