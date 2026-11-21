package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** No records are fetched by this ViewModel; it only checks permission state. */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway
) : ViewModel() {
    private val _status = MutableStateFlow<HealthConnectStatus?>(null)
    val status: StateFlow<HealthConnectStatus?> = _status

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        viewModelScope.launch {
            _status.value = gateway.status()
        }
    }

    fun showError(message: String) {
        _status.value = HealthConnectStatus.Error(message)
    }
}
