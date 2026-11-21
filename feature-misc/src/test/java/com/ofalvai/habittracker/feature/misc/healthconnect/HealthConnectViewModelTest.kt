package com.ofalvai.habittracker.feature.misc.healthconnect

import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.ExerciseAutoCompletionDao
import com.ofalvai.habittracker.core.database.ExerciseApplyResult
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseKind
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseSession
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.HealthConnectExerciseReader
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseSessionReadResult
import com.ofalvai.habittracker.core.testing.MainCoroutineRule
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        given(autoCompletionDao.observeRecentHistory()).willReturn(flowOf(emptyList()))
        given(mappingDao.observeMappings()).willReturn(flowOf(emptyList()))
        given(habitDao.getActiveHabitsWithActions()).willReturn(flowOf(emptyList()))
        return HealthConnectViewModel(gateway, habitDao, mappingDao, reader, autoCompletionDao)
    }

    /** The ViewModel subscribes to completion history on creation, which is a safe read.
     * Only automatic-completion writes must be absent when permission is missing.
     */
    private suspend fun verifyNoCompletionWrites() {
        org.mockito.kotlin.verify(autoCompletionDao, org.mockito.kotlin.never()).applyEligibleDay(
            org.mockito.kotlin.any(), org.mockito.kotlin.any(),
            org.mockito.kotlin.any(), org.mockito.kotlin.any()
        )
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

    @Test fun permissionErrorClearsPreviewAndNeverWritesCompletion() = runTest {
        val viewModel = viewModel()
        viewModel.previewToday(42)
        assertEquals(ExercisePreviewState.PermissionRequired, viewModel.preview.value)
        viewModel.showError("Permission dialog failed")
        assertNull(viewModel.preview.value)
        assertNull(viewModel.applyState.value)
        assertEquals(HealthConnectStatus.Error("Permission dialog failed"), viewModel.status.value)
        viewModel.applyPreview(42)
        advanceUntilIdle()
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
    }

    @Test fun refreshInvalidatesPreviousPreviewBeforeCheckingPermissions() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        val viewModel = viewModel(gateway)
        viewModel.previewToday(42)
        assertEquals(ExercisePreviewState.PermissionRequired, viewModel.preview.value)
        viewModel.refresh()
        assertNull(viewModel.preview.value)
        assertNull(viewModel.applyState.value)
        advanceUntilIdle()
        assertEquals(HealthConnectStatus.Ready, viewModel.status.value)
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
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
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
        viewModel.applyPreview(42)
        advanceUntilIdle()
        verifyNoCompletionWrites()
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

    @Test fun oneTimeSyncWithoutPermissionDoesNotReadOrWrite() = runTest {
        val viewModel = viewModel()
        viewModel.completeEligibleHabitsToday()
        advanceUntilIdle()
        assertEquals(ExerciseSyncState.PermissionRequired, viewModel.syncState.value)
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
    }

    @Test fun oneTimeSyncWithNoSavedMappingsIsNoOp() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        val viewModel = viewModel(gateway)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.completeEligibleHabitsToday()
        advanceUntilIdle()
        assertEquals(ExerciseSyncState.Finished(0, 0, 0), viewModel.syncState.value)
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
    }

    @Test fun oneTimeSyncAppliesQualifyingMappedExerciseExactlyOnce() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        given(gateway.hasExerciseReadPermission()).willReturn(true)
        val viewModel = viewModel(gateway)
        val today = LocalDate.now(ZoneId.of("UTC"))
        val mapping = ExerciseHabitMappingEntity(42, "RUNNING", 30, "UTC")
        val start = today.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant()
        val session = ExerciseSession(
            "session-1", ExerciseKind.RUNNING, start, start.plus(Duration.ofMinutes(35))
        )
        given(mappingDao.observeMappings()).willReturn(flowOf(listOf(mapping)))
        given(reader.readMappedDay(mapping, today)).willReturn(
            ExerciseSessionReadResult.Success(listOf(session))
        )
        given(autoCompletionDao.applyEligibleDay(
            mapping, today, Duration.ofMinutes(35), setOf("session-1")
        )).willReturn(ExerciseApplyResult.Applied(99))

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.completeEligibleHabitsToday()
        advanceUntilIdle()

        assertEquals(ExerciseSyncState.Finished(1, 0, 0), viewModel.syncState.value)
        verify(autoCompletionDao).applyEligibleDay(
            mapping, today, Duration.ofMinutes(35), setOf("session-1")
        )
    }

    @Test fun oneTimeSyncRevokedPermissionNeverReadsOrWrites() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        given(gateway.hasExerciseReadPermission()).willReturn(false)
        val viewModel = viewModel(gateway)
        val mapping = ExerciseHabitMappingEntity(42, "RUNNING", 30, "UTC")
        given(mappingDao.observeMappings()).willReturn(flowOf(listOf(mapping)))

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.completeEligibleHabitsToday()
        advanceUntilIdle()

        assertEquals(HealthConnectStatus.PermissionRequired, viewModel.status.value)
        assertEquals(ExerciseSyncState.PermissionRequired, viewModel.syncState.value)
        verifyNoInteractions(reader)
        verifyNoCompletionWrites()
    }

    @Test fun disconnectRemovesMappingsWithoutTouchingHabitActionsOrAutoHistory() = runTest {
        val viewModel = viewModel()
        viewModel.disconnect()
        advanceUntilIdle()
        verify(mappingDao).removeAllMappings()
        verifyNoInteractions(reader)
        org.mockito.kotlin.verify(autoCompletionDao, org.mockito.kotlin.never()).applyEligibleDay(
            org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )
    }

    @Test fun removeMappingDeletesOnlyLocalSettings() = runTest {
        val viewModel = viewModel()
        viewModel.removeMapping(42)
        advanceUntilIdle()
        verify(mappingDao).removeMapping(42)
    }
}
