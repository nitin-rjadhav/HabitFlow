package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectExerciseReaderTest {
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

    @Test fun unrelatedExercisesAreIgnored() {
        assertNull(exerciseKindForHealthConnect(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL))
        assertNull(exerciseKindForHealthConnect(-42))
    }
}
