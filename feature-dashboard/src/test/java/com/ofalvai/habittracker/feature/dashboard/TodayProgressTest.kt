package com.ofalvai.habittracker.feature.dashboard

import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.core.model.ActionHistory
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.model.HabitWithActions
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.todayProgress
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayProgressTest {
    private fun habit(id: Int, yesterday: Boolean = false, today: Boolean = false) =
        HabitWithActions(
            habit = Habit(id, "Habit $id", Habit.Color.Red, ""),
            actions = persistentListOf(
                Action(0, yesterday, null),
                Action(0, today, null)
            ),
            totalActionCount = 0,
            actionHistory = ActionHistory.Clean
        )

    @Test fun emptyListIsSafe() {
        val result = todayProgress(emptyList())
        assertEquals(0, result.completed)
        assertEquals(0, result.total)
        assertEquals(0f, result.fraction, 0f)
    }

    @Test fun countsOnlyTodaysActions() {
        val result = todayProgress(listOf(
            habit(1, yesterday = true),
            habit(2, today = true),
            habit(3, today = true),
            habit(4)
        ))
        assertEquals(2, result.completed)
        assertEquals(4, result.total)
        assertEquals(0.5f, result.fraction, 0f)
        assertEquals(50, result.percent)
    }

    @Test fun allCompleted() {
        val result = todayProgress(listOf(habit(1, today = true), habit(2, today = true)))
        assertEquals(100, result.percent)
        assertEquals(1f, result.fraction, 0f)
    }

    @Test fun noCompleted() {
        val result = todayProgress(listOf(habit(1), habit(2)))
        assertEquals(0, result.percent)
    }

    @Test fun missingActionHistoryDoesNotCountAsCompleted() {
        val emptyActions = habit(1).copy(actions = persistentListOf())
        val result = todayProgress(listOf(emptyActions))
        assertEquals(0, result.completed)
        assertEquals(1, result.total)
    }
}
