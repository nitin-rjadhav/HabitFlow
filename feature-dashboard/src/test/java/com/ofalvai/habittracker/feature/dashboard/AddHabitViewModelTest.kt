package com.ofalvai.habittracker.feature.dashboard

import app.cash.turbine.test
import com.ofalvai.habittracker.core.common.OnboardingManager
import com.ofalvai.habittracker.core.common.Telemetry
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.testing.MainCoroutineRule
import com.ofalvai.habittracker.feature.dashboard.mapper.toEntity
import com.ofalvai.habittracker.feature.dashboard.ui.addhabit.AddHabitSaveState
import com.ofalvai.habittracker.feature.dashboard.ui.addhabit.AddHabitViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class AddHabitViewModelTest {
    @get:Rule val mainCoroutineRule = MainCoroutineRule()
    private val dao = mock<HabitDao>()
    private val onboarding = mock<OnboardingManager>()
    private val telemetry = mock<Telemetry>()
    private val habit = Habit(0, "Morning run", Habit.Color.Green, "Notes")

    @Test fun failedInsertStaysOnFormAndAllowsRetry() = runTest {
        val entity = listOf(habit.toEntity(order = 0, archived = false))
        whenever(dao.getTotalHabitCount()).thenReturn(flowOf(0))
        val failure = IllegalStateException("Synthetic insert failure")
        whenever(dao.insertHabits(entity)).thenThrow(failure).thenReturn(Unit)
        val vm = AddHabitViewModel(dao, onboarding, telemetry)
        vm.backNavigationEvent.test {
            vm.addHabit(habit)
            assertEquals(AddHabitSaveState.Saving, vm.saveState.value)
            runCurrent()
            assertEquals(AddHabitSaveState.Failed, vm.saveState.value)
            expectNoEvents()
            verifyNoInteractions(onboarding)
            vm.addHabit(habit)
            awaitItem()
            assertEquals(AddHabitSaveState.Saved, vm.saveState.value)
            expectNoEvents()
        }
        verify(dao, times(2)).insertHabits(entity)
        verify(onboarding).firstHabitCreated()
    }

    @Test fun repeatedSubmissionAndOnboardingFailureDoNotDuplicateCommittedHabit() = runTest {
        whenever(dao.getTotalHabitCount()).thenReturn(flowOf(0))
        doThrow(IllegalStateException("Synthetic preference failure")).whenever(onboarding).firstHabitCreated()
        val vm = AddHabitViewModel(dao, onboarding, telemetry)
        vm.backNavigationEvent.test {
            vm.addHabit(habit)
            vm.addHabit(habit)
            awaitItem()
            assertEquals(AddHabitSaveState.Saved, vm.saveState.value)
            vm.addHabit(habit)
            runCurrent()
            expectNoEvents()
        }
        verify(dao, times(1)).insertHabits(listOf(habit.toEntity(order = 0, archived = false)))
    }
}
