package com.ofalvai.habittracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.testing.BaseInstrumentedTest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-disk Room upgrade path, not a fresh in-memory v8 database.
 * Starts with the persisted v6 Habit/Action schema and validates 6→7→8.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseMigration6To8Test : BaseInstrumentedTest() {

    @Test
    fun upgradePreservesExistingManualHabitsAndActions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "habitflow-phase8-migration-${System.nanoTime()}"
        val file = context.getDatabasePath(filename)
        file.parentFile?.mkdirs()
        val storedTimestamp = Instant.parse("2026-11-20T06:30:00Z")
        val original = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // Shape matches the checked-in AppDatabase/6.json schema.
            original.execSQL(
                "CREATE TABLE IF NOT EXISTS `Habit` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                    "`order` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
            )
            original.execSQL(
                "CREATE TABLE IF NOT EXISTS `Action` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`habit_id` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`habit_id`) REFERENCES `Habit`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            original.execSQL("CREATE INDEX IF NOT EXISTS `index_Action_habit_id` ON `Action` (`habit_id`)")
            original.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            original.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '3e6d9eda6f4aeeef34fb460a453c412e')"
            )
            original.execSQL(
                "INSERT INTO `Habit` (id,name,color,`order`,archived,notes) " +
                    "VALUES(42,'Running','Green',0,0,'Manual tracking preserved')"
            )
            original.execSQL(
                "INSERT INTO `Action` (id,habit_id,timestamp) VALUES(7,42,${storedTimestamp.toEpochMilli()})"
            )
            original.version = 6
        } finally {
            original.close()
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, filename)
            .addMigrations(*MIGRATIONS)
            .build()
        try {
            val habits = database.habitDao().getHabits()
            assertEquals(1, habits.size)
            assertEquals("Manual tracking preserved", habits.single().notes)
            val actions = database.habitDao().getActionsForHabit(42)
            assertEquals(1, actions.size)
            assertEquals(7, actions.single().id)
            assertEquals(storedTimestamp, actions.single().timestamp)

            val mapping = ExerciseHabitMappingEntity(42, "RUNNING", 30, "Asia/Kolkata")
            database.exerciseMappingDao().upsertMapping(mapping)
            assertEquals(mapping, database.exerciseMappingDao().getMapping(42))
            assertEquals(null, database.exerciseAutoCompletionDao().getLedger(42, "2026-11-20"))
            assertNotNull(database.exerciseAutoCompletionDao().observeRecentHistory())
        } finally {
            database.close()
            context.deleteDatabase(filename)
        }
    }
}
