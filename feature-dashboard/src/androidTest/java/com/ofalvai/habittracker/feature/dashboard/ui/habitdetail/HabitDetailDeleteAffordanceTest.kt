package com.ofalvai.habittracker.feature.dashboard.ui.habitdetail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.model.HabitWithActions
import com.ofalvai.habittracker.core.ui.state.Result
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.dashboard.ui.model.SingleStats
import com.ofalvai.habittracker.feature.dashboard.ui.model.DashboardConfig
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.DashboardContent
import kotlinx.collections.immutable.ImmutableList
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

    private fun render(onSave: (Habit) -> Unit = {}, onDelete: (Habit) -> Unit) {
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
                    onSave = onSave,
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
    @Test fun blankEditNameShowsErrorAndBlocksSaveUntilCorrected() {
        var saved: Habit? = null
        render(onSave = { saved = it }, onDelete = {})
        val nameField = hasSetTextAction() and androidx.compose.ui.test.hasText("Morning run")
        compose.onNode(nameField).performTextReplacement("   ")
        compose.onNodeWithContentDescription("Save").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Enter a habit name").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, saved) }
        compose.onNode(hasSetTextAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a habit name"))
            .performTextReplacement("Evening run")
        compose.onNodeWithText("Enter a habit name").assertDoesNotExist()
        compose.onNodeWithContentDescription("Archive").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle { assertEquals(habit.copy(name = "Evening run"), saved) }
    }

    @Test fun dashboardLoadingEmptyAndFailureAreDistinct() {
        val state = mutableStateOf<Result<ImmutableList<HabitWithActions>>>(Result.Loading)
        var addCount = 0
        compose.setContent {
            PreviewTheme {
                DashboardContent(
                    habits = state.value, config = DashboardConfig.FiveDay,
                    onboardingState = null, exerciseActionIds = emptySet(),
                    onAddHabitClick = { addCount++ }, onConfigClick = {},
                    onActionToggle = { _, _, _ -> }, onHabitDetail = {},
                    onSettingsClick = {}, onArchiveClick = {}, onExportClick = {},
                    onMove = {}
                )
            }
        }
        compose.onNodeWithText("Loading habits…").assertIsDisplayed()
        compose.onNodeWithText("Create your first habit").assertDoesNotExist()
        compose.runOnIdle { state.value = Result.Success(persistentListOf()) }
        compose.onNodeWithText("Loading habits…").assertDoesNotExist()
        compose.onNodeWithText("You don't have any habits yet.").assertIsDisplayed()
        compose.onNodeWithText("Create your first habit").performClick()
        compose.runOnIdle {
            assertEquals(1, addCount)
            state.value = Result.Failure(IllegalStateException("synthetic"))
        }
        compose.onNodeWithText("Failed to load habit list").assertIsDisplayed()
        compose.onNodeWithText("My habits").assertIsDisplayed()
        compose.onNodeWithText("Create your first habit").assertDoesNotExist()
    }

    @Test fun detailLoadingAndFailureRetainBackNavigation() {
        val state = mutableStateOf<Result<HabitWithActions>>(Result.Loading)
        var backCount = 0
        compose.setContent {
            PreviewTheme {
                HabitDetailScreen(
                    habitDetailState = state.value,
                    singleStats = SingleStats(null, 0, 0, 0f),
                    chartData = Result.Loading,
                    onChartTypeChange = {},
                    onBack = { backCount++ }, onEdit = {}, onArchive = {},
                    onDelete = {}, onDayToggle = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText("Loading habit details…").assertIsDisplayed()
        compose.onNodeWithText("Loading habit statistics…").assertDoesNotExist()
        compose.runOnIdle { state.value = Result.Failure(IllegalStateException("synthetic")) }
        compose.onNodeWithText("Loading habit details…").assertDoesNotExist()
        compose.onNodeWithText("Failed to load habit details").assertIsDisplayed()
        compose.onNodeWithText("Failed to load habit statistics").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun cancelConfirmationPreservesHabitAndDoesNotDelete() {
        var deleteCount = 0
        compose.setContent {
            var pending by remember { mutableStateOf<Habit?>(habit) }
            PreviewTheme {
                DeleteHabitConfirmationDialog(
                    habit = pending,
                    onDismiss = { pending = null },
                    onConfirm = { deleteCount++ }
                )
            }
        }
        compose.onNodeWithText("Delete “Morning run” and all its completion history? This cannot be undone. Archive keeps your history instead.")
            .assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete forever").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deleteCount) }
    }

    @Test fun confirmDeletionInvokesCallbackForSelectedHabitOnly() {
        var deleted: Habit? = null
        compose.setContent {
            var pending by remember { mutableStateOf<Habit?>(habit) }
            PreviewTheme {
                DeleteHabitConfirmationDialog(
                    habit = pending,
                    onDismiss = { pending = null },
                    onConfirm = {
                        deleted = it
                        pending = null
                    }
                )
            }
        }
        compose.onNodeWithText("Delete forever").performClick()
        compose.onNodeWithText("Delete forever").assertDoesNotExist()
        compose.runOnIdle { assertEquals(habit, deleted) }
    }

}
