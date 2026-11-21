package com.ofalvai.habittracker.feature.misc.healthconnect

import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.ExerciseAutoCompletionDao
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.HealthConnectExerciseReader
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseSessionReadResult
import com.ofalvai.habittracker.core.testing.MainCoroutineRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class HealthConnectViewModelTest {
    @get:Rule val mainCoroutineRule = MainCoroutineRule()

    private val mappingDao = mock<ExerciseMappingDao>()
    private val habitDao = mock<HabitDao>()
    private val reader = mock<HealthConnectExerciseReader>()
    private val autoCompletionDao = mock<ExerciseAutoCompletionDao>()

    private fun viewModel(gateway: HealthConnectGateway = mock()): HealthConnectViewModel {
        given(mappingDao.observeMappings()).willReturn(flowOf(emptyList()))
        given(habitDao.getActiveHabitsWithActions()).willReturn(flowOf(emptyList()))
        return HealthConnectViewModel(gateway, habitDao, mappingDao, reader, autoCompletionDao)
    }

    @Test fun refreshLoadsPermissionStatus() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.PermissionRequired)
        val viewModel = viewModel(gateway)
        assertNull(viewModel.status.value)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(HealthConnectStatus.PermissionRequired, viewModel.status.value)
    }

    @Test fun errorsAreVisibleWithoutRequestingHealthData() = runTest {
        val viewModel = viewModel()
        viewModel.showError("Permission request failed")
        assertEquals(
            HealthConnectStatus.Error("Permission request failed"),
            viewModel.status.value
        )
    }

    @Test fun refreshClearsOldErrorsAndLoadsCurrentState() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        val viewModel = viewModel(gateway)
        viewModel.showError("Previous error")

        viewModel.refresh()
        assertNull(viewModel.status.value)
        advanceUntilIdle()

        assertEquals(HealthConnectStatus.Ready, viewModel.status.value)
    }

    @Test fun saveMappingCreatesOnlyLocalSettings() = runTest {
        val viewModel = viewModel()
        viewModel.saveMapping(42, "RUNNING", 30)
        advanceUntilIdle()
        verify(mappingDao).upsertMapping(
            org.mockito.kotlin.check<ExerciseHabitMappingEntity> {
                assertEquals(42, it.habitId)
                assertEquals("RUNNING", it.exerciseKind)
                assertEquals(30, it.minimumDurationMinutes)
            }
        )
    }

    @Test fun previewWithoutPermissionNeverReadsHealthData() = runTest {
        val viewModel = viewModel()
        viewModel.previewToday(42)
        advanceUntilIdle()
        assertEquals(ExercisePreviewState.PermissionRequired, viewModel.preview.value)
        verifyNoInteractions(reader, autoCompletionDao)
        viewModel.applyPreview(42)
        advanceUntilIdle()
        verifyNoInteractions(autoCompletionDao)
    }

    @Test fun previewWithNoMappingNeverReadsHealthData() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        val viewModel = viewModel(gateway)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.previewToday(42)
        advanceUntilIdle()
        assertEquals(
            ExercisePreviewState.Failure("Mapping no longer exists"),
            viewModel.preview.value
        )
        verifyNoInteractions(reader)
    }

    @Test fun removeMappingDeletesOnlyLocalSettings() = runTest {
        val viewModel = viewModel()
        viewModel.removeMapping(42)
        advanceUntilIdle()
        verify(mappingDao).removeMapping(42)
    }
}
