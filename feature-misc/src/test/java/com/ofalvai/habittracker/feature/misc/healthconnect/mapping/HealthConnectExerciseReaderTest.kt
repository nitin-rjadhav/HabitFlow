package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class HealthConnectExerciseReaderTest {
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
    }

    @Test fun unrelatedExercisesAreIgnored() {
        assertNull(exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL))
        assertNull(exerciseKindForHealthConnect(-42))
    }
}
