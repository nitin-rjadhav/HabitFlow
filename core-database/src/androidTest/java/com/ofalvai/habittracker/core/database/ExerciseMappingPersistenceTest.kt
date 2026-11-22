package com.ofalvai.habittracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.ExerciseSourceRecordEntity
import com.ofalvai.habittracker.core.database.entity.Habit
import com.ofalvai.habittracker.core.database.entity.HabitById
import com.ofalvai.habittracker.core.testing.BaseInstrumentedTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseMappingPersistenceTest : BaseInstrumentedTest() {
    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var mappingDao: ExerciseMappingDao

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        habitDao = database.habitDao()
        mappingDao = database.exerciseMappingDao()
    }

    @After fun tearDown() { database.close() }

    private suspend fun seedHabit(id: Int = 42) {
        habitDao.insertHabits(listOf(Habit(id, "Running", Habit.Color.Green, 0, false, "")))
    }

    private fun mapping() = ExerciseHabitMappingEntity(42, "RUNNING", 30, "Asia/Kolkata")
    private fun source(id: String) = ExerciseSourceRecordEntity(42, "2026-11-21", id)

    @Test fun mappingIsStoredAndCanBeLoaded() = runTest {
        seedHabit()
        mappingDao.upsertMapping(mapping())
        assertEquals(mapping(), mappingDao.getMapping(42))
    }

    @Test fun repeatedSourceIdIsIgnored() = runTest {
        seedHabit()
        mappingDao.upsertMapping(mapping())
        mappingDao.insertSourceRecords(listOf(source("record-1")))
        mappingDao.insertSourceRecords(listOf(source("record-1")))
        assertEquals(1, mappingDao.getSourceRecordCount(42))
    }

    @Test fun removingMappingCascadesProvenance() = runTest {
        seedHabit()
        mappingDao.upsertMapping(mapping())
        mappingDao.insertSourceRecords(listOf(source("record-1")))
        mappingDao.removeMapping(42)
        assertNull(mappingDao.getMapping(42))
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }

    @Test fun deletingHabitCascadesMappingAndProvenance() = runTest {
        seedHabit()
        mappingDao.upsertMapping(mapping())
        mappingDao.insertSourceRecords(listOf(source("record-1")))
        habitDao.deleteHabit(HabitById(42))
        assertNull(mappingDao.getMapping(42))
        assertEquals(0, mappingDao.getSourceRecordCount(42))
    }
}
