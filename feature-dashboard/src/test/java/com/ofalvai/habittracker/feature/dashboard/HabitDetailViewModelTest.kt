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
import com.ofalvai.habittracker.core.database.entity.ActionCompletionRate
import com.ofalvai.habittracker.core.ui.state.Result
import com.ofalvai.habittracker.feature.dashboard.ui.model.ActionCountChart
import java.time.Instant
import kotlinx.coroutines.test.runCurrent
import com.ofalvai.habittracker.feature.dashboard.ui.habitdetail.HabitEditSaveState
import com.ofalvai.habittracker.core.database.entity.HabitWithActions
import com.ofalvai.habittracker.feature.dashboard.mapper.toEntity
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

    @Test fun archiveFailureReportsErrorWithoutNavigating() = runTest {
        val entity = habit.toEntity(order = 0, archived = true)
        val failure = IllegalStateException("Synthetic archive failure")
        whenever(dao.updateHabit(entity)).thenThrow(failure)
        val vm = viewModel()
        vm.habitDetailEvent.test {
            vm.archiveHabit(habit)
            assertEquals(HabitDetailEvent.ArchiveError, awaitItem())
            expectNoEvents()
        }
        verify(dao).updateHabit(entity)
        verify(telemetry).logNonFatal(failure)
    }

    @Test fun failedEditCanBeRetriedWithoutDuplicateUpdate() = runTest {
        val entity = habit.toEntity(order = 0, archived = false)
        val failure = IllegalStateException("Synthetic update failure")
        whenever(dao.updateHabit(entity)).thenThrow(failure).thenReturn(Unit)
        whenever(dao.getHabitWithActions(habit.id)).thenReturn(HabitWithActions(entity, emptyList()))
        val vm = viewModel()
        vm.updateHabit(habit)
        runCurrent()
        assertEquals(HabitEditSaveState.Failed, vm.editSaveState.value)
        vm.updateHabit(habit)
        vm.updateHabit(habit)
        runCurrent()
        assertEquals(HabitEditSaveState.Saved, vm.editSaveState.value)
        verify(dao, times(2)).updateHabit(entity)
        vm.beginEdit()
        assertEquals(HabitEditSaveState.Idle, vm.editSaveState.value)
    }

    @Test fun statsFailureIsNotReplacedByEmptyChartAndRetryRecoversSelectedType() = runTest {
        val failure = IllegalStateException("Synthetic statistics query failure")
        whenever(dao.getCompletionRate(habit.id))
            .thenThrow(failure).thenReturn(ActionCompletionRate(Instant.EPOCH, 0))
        whenever(dao.getActionCountByWeek(habit.id)).thenReturn(emptyList())
        whenever(dao.getActionCountByMonth(habit.id)).thenReturn(emptyList())
        val vm = viewModel()
        assertEquals(Result.Loading, vm.chartData.value)
        vm.fetchHabitStats(habit.id).join()
        assertEquals(Result.Failure(failure), vm.chartData.value)
        vm.switchChartType(ActionCountChart.Type.Monthly)
        assertEquals(Result.Failure(failure), vm.chartData.value)
        val retry = vm.fetchHabitStats(habit.id)
        assertEquals(Result.Loading, vm.chartData.value)
        retry.join()
        val chart = vm.chartData.value as Result.Success
        assertEquals(ActionCountChart.Type.Monthly, chart.value.type)
        assertEquals(0, vm.singleStats.value.actionCount)
        verify(telemetry).logNonFatal(failure)
    }
}
