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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real on-disk v7 -> v8 upgrade regression. Unlike the v6 -> v8 test,
 * this fixture already has a saved mapping and local source identity.
 * The ledger is introduced by v8 without wiping existing data.
 *
 * The test uses synthetic fixture identifiers; no Health Connect API is called.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseMigration7To8Test : BaseInstrumentedTest() {

    @Test
    fun upgradePreservesMappingsSourceIdsAndManualActions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "habitflow-v7-to-v8-${System.nanoTime()}"
        val file = context.getDatabasePath(filename)
        file.parentFile?.mkdirs()
        val timestamp = Instant.parse("2026-11-21T06:30:00Z")

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Exact column and FK layout of the v6 Habit/Action schema.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Habit` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                    "`order` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Action` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`habit_id` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`habit_id`) REFERENCES `Habit`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_Action_habit_id` ON `Action` (`habit_id`)")
            // Tables added by v6 -> v7 migration, before the ledger existed.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ExerciseHabitMapping` (" +
                    "`habit_id` INTEGER NOT NULL, `exercise_kind` TEXT NOT NULL, " +
                    "`minimum_duration_minutes` INTEGER NOT NULL, `zone_id` TEXT NOT NULL, " +
                    "PRIMARY KEY(`habit_id`), " +
                    "FOREIGN KEY(`habit_id`) REFERENCES `Habit`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ExerciseSourceRecord` (" +
                    "`habit_id` INTEGER NOT NULL, `local_date` TEXT NOT NULL, " +
                    "`provider_record_id` TEXT NOT NULL, " +
                    "PRIMARY KEY(`habit_id`, `local_date`, `provider_record_id`), " +
                    "FOREIGN KEY(`habit_id`) REFERENCES `ExerciseHabitMapping`(`habit_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            // The old identity is replaced by Room after successful migration.
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '3e6d9eda6f4aeeef34fb460a453c412e')"
            )
            db.execSQL(
                "INSERT INTO `Habit` (id,name,color,`order`,archived,notes) " +
                    "VALUES(42,'Running','Green',0,0,'Keep during upgrade')"
            )
            db.execSQL(
                "INSERT INTO `Action` (id,habit_id,timestamp) VALUES(7,42,${timestamp.toEpochMilli()})"
            )
            db.execSQL(
                "INSERT INTO `ExerciseHabitMapping` " +
                    "(habit_id,exercise_kind,minimum_duration_minutes,zone_id) " +
                    "VALUES(42,'RUNNING',30,'Asia/Kolkata')"
            )
            db.execSQL(
                "INSERT INTO `ExerciseSourceRecord` (habit_id,local_date,provider_record_id) " +
                    "VALUES(42,'2026-11-21','synthetic-device-record')"
            )
            db.version = 7
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, filename)
            .addMigrations(*MIGRATIONS)
            .build()
        try {
            val habits = database.habitDao().getHabits()
            assertEquals(1, habits.size)
            assertEquals("Keep during upgrade", habits.single().notes)
            val actions = database.habitDao().getActionsForHabit(42)
            assertEquals(1, actions.size)
            assertEquals(7, actions.single().id)
            assertEquals(timestamp, actions.single().timestamp)

            val saved = database.exerciseMappingDao().getMapping(42)
            assertEquals(ExerciseHabitMappingEntity(42, "RUNNING", 30, "Asia/Kolkata"), saved)
            val sources = database.exerciseMappingDao().getSourceRecords(42, "2026-11-21")
            assertEquals(1, sources.size)
            assertEquals("synthetic-device-record", sources.single().providerRecordId)
            assertNull(database.exerciseAutoCompletionDao().getLedger(42, "2026-11-21"))
        } finally {
            database.close()
            context.deleteDatabase(filename)
        }
    }
}
