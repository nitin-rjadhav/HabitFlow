package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Permission and explicit mapping settings only: no health records are read here. */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val habitDao: HabitDao,
    private val mappingDao: ExerciseMappingDao
) : ViewModel() {
    private val _status = MutableStateFlow<HealthConnectStatus?>(null)
    val status: StateFlow<HealthConnectStatus?> = _status
    private var refreshJob: Job? = null

    val availableHabits = habitDao.getActiveHabitsWithActions()
        .map { results -> results.map { it.habit } }

    val savedMappings = mappingDao.observeMappings()

    private val _mappingError = MutableStateFlow<String?>(null)
    val mappingError: StateFlow<String?> = _mappingError

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        // Permission changes can race with an in-flight status check.
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

    fun saveMapping(habitId: Int, exerciseKind: String, minimumMinutes: Int) {
        viewModelScope.launch {
            try {
                val mapping = ExerciseHabitMappingEntity(
                    habitId = habitId,
                    exerciseKind = exerciseKind,
                    minimumDurationMinutes = minimumMinutes,
                    zoneId = ZoneId.systemDefault().id
                )
                mappingDao.upsertMapping(mapping)
                _mappingError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mappingError.value = error.message ?: "Could not save activity mapping"
            }
        }
    }

    fun removeMapping(habitId: Int) {
        viewModelScope.launch {
            try {
                mappingDao.removeMapping(habitId)
                _mappingError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mappingError.value = error.message ?: "Could not remove activity mapping"
            }
        }
    }
}
