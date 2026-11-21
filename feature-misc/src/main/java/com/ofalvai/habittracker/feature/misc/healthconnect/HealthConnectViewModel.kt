package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** No records are fetched by this ViewModel; it only checks permission state. */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway
) : ViewModel() {
    private val _status = MutableStateFlow<HealthConnectStatus?>(null)
    val status: StateFlow<HealthConnectStatus?> = _status
    private var refreshJob: Job? = null

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        // Permission changes can race with an in-flight status check.
        // Only the latest refresh is allowed to update the UI.
        refreshJob?.cancel()
        _status.value = null
        refreshJob = viewModelScope.launch {
            _status.value = gateway.status()
        }
    }

    fun showError(message: String) {
        refreshJob?.cancel()
        _status.value = HealthConnectStatus.Error(message)
    }
}
