package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectExerciseReaderTest {
    @Test fun readerKeepsConflictingProviderIdsForOrderIndependentDayMatching() {
        val day = LocalDate.of(2026, 11, 21)
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-11-21T10:00:00Z")
        val short = ExerciseSession(
            "shared-id", ExerciseKind.RUNNING, start, start.plus(Duration.ofMinutes(10))
        )
        val long = ExerciseSession(
            "shared-id", ExerciseKind.RUNNING, start, start.plus(Duration.ofMinutes(35))
        )
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()

        // Simulate two conflicting records across Health Connect pages.
        // Early distinctBy("shared-id") would make the result depend on page order.
        for (pageOrder in listOf(listOf(short, long), listOf(long, short))) {
            val returned = validReaderSessionsForDay(
                pageOrder, ExerciseKind.RUNNING, dayStart, dayEnd
            )
            assertEquals(2, returned.size)
            val match = matchExerciseDay(
                ExerciseHabitMapping(42, ExerciseKind.RUNNING, 30),
                returned, day, zone, manualCompletionExists = false
            )
            assertTrue(match.eligible)
            assertEquals(Duration.ofMinutes(35), match.totalDuration)
            assertEquals(setOf("shared-id"), match.supportingRecordIds)
        }
    }

    @Test fun readerFiltersBadAndOutOfDayCandidatesBeforeDayMatching() {
        val day = LocalDate.of(2026, 11, 21)
        val zone = ZoneId.of("UTC")
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        val valid = ExerciseSession(
            "shared-id", ExerciseKind.RUNNING,
            start.plusSeconds(3600), start.plusSeconds(5400)
        )
        val candidates = listOf(
            ExerciseSession("shared-id", ExerciseKind.RUNNING, start.minusSeconds(7200), start),
            ExerciseSession("invalid", ExerciseKind.RUNNING, start.plusSeconds(500), start.plusSeconds(100)),
            ExerciseSession("walking", ExerciseKind.WALKING, start.plusSeconds(100), start.plusSeconds(2000)),
            ExerciseSession(" ", ExerciseKind.RUNNING, start.plusSeconds(100), start.plusSeconds(2000)),
            ExerciseSession("starts-at-end", ExerciseKind.RUNNING, end, end.plusSeconds(60)),
            valid
        )
        val returned = validReaderSessionsForDay(candidates, ExerciseKind.RUNNING, start, end)
        assertEquals(listOf(valid), returned)
    }

    @Test fun providerErrorsUsePrivacySafeGenericMessage() {
        val message = GENERIC_EXERCISE_READ_ERROR
        assertEquals("Unable to read exercise sessions. Try again or check Health Connect access.", message)
        assertFalse(message.contains("recordId", ignoreCase = true))
    }

    @Test fun recognizesWalking() {
        assertEquals(
            ExerciseKind.WALKING,
            exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        )
    }

    @Test fun runningAndTreadmillAreBothRunning() {
        assertEquals(
            ExerciseKind.RUNNING,
            exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
        )
        assertEquals(
            ExerciseKind.RUNNING,
            exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        )
    }

    @Test fun stationaryAndOutdoorCyclingAreBothCycling() {
        assertEquals(
            ExerciseKind.CYCLING,
            exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_BIKING)
        )
        assertEquals(
            ExerciseKind.CYCLING,
            exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY)
        )
    }

    @Test fun mappingKindParsingRejectsUnknownAndCaseMismatchedValues() {
        assertEquals(ExerciseKind.WALKING, "WALKING".toExerciseKindOrNull())
        assertEquals(ExerciseKind.RUNNING, "RUNNING".toExerciseKindOrNull())
        assertEquals(ExerciseKind.CYCLING, "CYCLING".toExerciseKindOrNull())
        assertNull("SWIMMING".toExerciseKindOrNull())
        assertNull("running".toExerciseKindOrNull())
        assertNull("".toExerciseKindOrNull())
        assertNull(" RUNNING".toExerciseKindOrNull())
        assertNull("RUNNING ".toExerciseKindOrNull())
        assertNull("RUNNING\\n".toExerciseKindOrNull())
        assertNull("RUNNING\n".toExerciseKindOrNull())
        assertNull("\tRUNNING".toExerciseKindOrNull())
        assertNull("RUNNING_TREADMILL".toExerciseKindOrNull())
        assertNull("BIKING".toExerciseKindOrNull())
    }

    @Test fun mappingKindParsingRejectsUnicodeLookalikesAndControlCharacters() {
        // Persisted values must match the canonical enum exactly, not visually resemble it.
        val invalidValues = listOf(
            "WALKING\r",
            "RUNNING\u0000",
            "CYCLING\u00A0",
            "\u200BRUNNING",
            "WALK\u0130NG",
            "CYCLING\u200D",
            "RUNNING/CYCLING"
        )
        invalidValues.forEach { invalid ->
            assertNull("Unexpectedly accepted mapping kind: ${invalid.toList()}", invalid.toExerciseKindOrNull())
        }
    }

    @Test fun unrelatedExercisesAreIgnored() {
        assertNull(exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL))
        assertNull(exerciseKindForHealthConnect(-42))
    }
}
