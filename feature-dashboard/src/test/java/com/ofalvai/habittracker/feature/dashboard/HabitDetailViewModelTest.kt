package com.ofalvai.habittracker.feature.dashboard

import app.cash.turbine.test
import com.ofalvai.habittracker.core.common.OnboardingManager
import com.ofalvai.habittracker.core.common.Telemetry
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.HabitById
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.testing.MainCoroutineRule
import com.ofalvai.habittracker.feature.dashboard.repo.ActionRepository
import com.ofalvai.habittracker.feature.dashboard.ui.habitdetail.HabitDetailEvent
import com.ofalvai.habittracker.feature.dashboard.ui.habitdetail.HabitDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class HabitDetailViewModelTest {
    @get:Rule var mainCoroutineRule = MainCoroutineRule()

    private val dao = mock<HabitDao>()
    private val repository = mock<ActionRepository>()
    private val telemetry = mock<Telemetry>()
    private val onboarding = mock<OnboardingManager>()
    private val habit = Habit(42, "Morning run", Habit.Color.Green, "Manual notes")

    private fun viewModel() = HabitDetailViewModel(dao, repository, telemetry, onboarding)

    @Test fun confirmedDeletionRemovesHabitAndNavigatesOnlyAfterSuccess() = runTest {
        val vm = viewModel()
        vm.habitDetailEvent.test {
            vm.deleteHabit(habit)
            assertEquals(HabitDetailEvent.BackNavigation, awaitItem())
            expectNoEvents()
        }
        verify(dao).deleteHabit(HabitById(42))
        verifyNoInteractions(telemetry)
    }

    @Test fun failedDeletionShowsErrorAndDoesNotNavigate() = runTest {
        val failure = IllegalStateException("Synthetic database failure")
        given(dao.deleteHabit(HabitById(42))).willThrow(failure)
        val vm = viewModel()
        vm.habitDetailEvent.test {
            vm.deleteHabit(habit)
            assertEquals(HabitDetailEvent.DeleteError, awaitItem())
            expectNoEvents()
        }
        verify(dao).deleteHabit(HabitById(42))
        verify(telemetry).logNonFatal(failure)
    }
}
