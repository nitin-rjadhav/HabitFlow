package com.ofalvai.habittracker.feature.misc.healthconnect

import com.ofalvai.habittracker.core.testing.MainCoroutineRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock

class HealthConnectViewModelTest {
    @get:Rule val mainCoroutineRule = MainCoroutineRule()

    @Test fun refreshLoadsPermissionStatus() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.PermissionRequired)
        val viewModel = HealthConnectViewModel(gateway)
        assertNull(viewModel.status.value)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(HealthConnectStatus.PermissionRequired, viewModel.status.value)
    }

    @Test fun errorsAreVisibleWithoutRequestingHealthData() = runTest {
        val viewModel = HealthConnectViewModel(mock())
        viewModel.showError("Permission request failed")
        assertEquals(
            HealthConnectStatus.Error("Permission request failed"),
            viewModel.status.value
        )
    }

    @Test fun refreshClearsOldErrorsAndLoadsCurrentState() = runTest {
        val gateway = mock<HealthConnectGateway>()
        given(gateway.status()).willReturn(HealthConnectStatus.Ready)
        val viewModel = HealthConnectViewModel(gateway)
        viewModel.showError("Previous error")

        viewModel.refresh()
        assertNull(viewModel.status.value)
        advanceUntilIdle()

        assertEquals(HealthConnectStatus.Ready, viewModel.status.value)
    }
}
