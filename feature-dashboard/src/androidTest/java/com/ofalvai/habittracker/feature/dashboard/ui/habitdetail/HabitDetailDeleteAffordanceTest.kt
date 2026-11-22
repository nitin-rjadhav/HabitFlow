package com.ofalvai.habittracker.feature.dashboard.ui.habitdetail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.model.HabitWithActions
import com.ofalvai.habittracker.core.ui.state.Result
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.dashboard.ui.model.SingleStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated Compose checks for the Edit-screen destructive affordance.
 * This class deliberately has no Hilt rule; run individually while issue #6
 * (Compose instrumentation runner hang) is being investigated.
 */
@RunWith(AndroidJUnit4::class)
class HabitDetailDeleteAffordanceTest {
    @get:Rule val compose = createComposeRule()

    private val habit = Habit(42, "Morning run", Habit.Color.Green, "Notes")

    private fun render(onDelete: (Habit) -> Unit) {
        compose.setContent {
            PreviewTheme {
                HabitDetailHeader(
                    habitDetailState = Result.Success(
                        HabitWithActions(habit, persistentListOf(), 0,
                            com.ofalvai.habittracker.core.model.ActionHistory.Clean)
                    ),
                    singleStats = SingleStats(null, 0, 0, 0f),
                    scrollState = rememberScrollState(),
                    onBack = {},
                    onSave = {},
                    onArchive = {},
                    onDelete = onDelete
                )
            }
        }
        compose.onNodeWithContentDescription("Edit").performClick()
    }

    @Test fun editShowsAccessibleDeleteActionWithMinimumTouchHeight() {
        render {}
        compose.onNodeWithText("Delete habit permanently")
            .assertExists()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test fun deleteActionPassesOriginalHabitWithoutDeletingIt() {
        var selected: Habit? = null
        render { selected = it }
        compose.onNodeWithText("Delete habit permanently").performClick()
        compose.runOnIdle { assertEquals(habit, selected) }
    }
}
