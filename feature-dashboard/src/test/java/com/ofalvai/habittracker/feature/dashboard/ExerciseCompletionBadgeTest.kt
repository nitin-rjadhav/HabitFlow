package com.ofalvai.habittracker.feature.dashboard

import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.view.isExerciseCompletedToday
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCompletionBadgeTest {
    @Test fun displaysForTodaysActiveExerciseAction() {
        val actions = listOf(Action(0, false, null), Action(42, true, Instant.now()))
        assertTrue(isExerciseCompletedToday(actions, setOf(42)))
    }

    @Test fun manualCompletionDoesNotReceiveHealthConnectBadge() {
        val actions = listOf(Action(0, false, null), Action(15, true, Instant.now()))
        assertFalse(isExerciseCompletedToday(actions, setOf(42)))
    }

    @Test fun yesterdayOrUndoneAutomaticCompletionDoesNotReceiveBadge() {
        val actions = listOf(Action(42, true, Instant.now()), Action(0, false, null))
        assertFalse(isExerciseCompletedToday(actions, setOf(42)))
        assertFalse(isExerciseCompletedToday(listOf(Action(42, false, null)), setOf(42)))
        assertFalse(isExerciseCompletedToday(listOf(Action(42, true, Instant.now())), emptySet()))
    }

    @Test fun noBadgeForEmptyActionsOrPlaceholderIds() {
        assertFalse(isExerciseCompletedToday(emptyList(), setOf(42)))
        assertFalse(isExerciseCompletedToday(listOf(Action(0, true, null)), setOf(0)))
    }
}
