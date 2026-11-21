package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseHabitMapping
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseSessionReadResult
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.HealthConnectExerciseReader
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.matchExerciseDay
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.toExerciseKindOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Reads exercise only when the user taps Preview. Never writes habit completions. */
sealed interface ExercisePreviewState {
    data object Loading : ExercisePreviewState
    data object PermissionRequired : ExercisePreviewState
    data object Unavailable : ExercisePreviewState
    data class Failure(val message: String) : ExercisePreviewState
    data class Result(
        val habitId: Int,
        val minutes: Long,
        val recordCount: Int,
        val eligible: Boolean,
        val manualAlreadyComplete: Boolean
    ) : ExercisePreviewState
}


@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val habitDao: HabitDao,
    private val mappingDao: ExerciseMappingDao,
    private val exerciseReader: HealthConnectExerciseReader
) : ViewModel() {
    private val _status = MutableStateFlow<HealthConnectStatus?>(null)
    val status: StateFlow<HealthConnectStatus?> = _status
    private var refreshJob: Job? = null

    val availableHabits = habitDao.getActiveHabitsWithActions()
        .map { results -> results.map { it.habit } }

    val savedMappings = mappingDao.observeMappings()

    private val _mappingError = MutableStateFlow<String?>(null)
    val mappingError: StateFlow<String?> = _mappingError

    private val _preview = MutableStateFlow<ExercisePreviewState?>(null)
    val preview: StateFlow<ExercisePreviewState?> = _preview
    private var previewJob: Job? = null

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        // Permission changes can race with an in-flight status check.
        refreshJob?.cancel()
        _status.value = null
        refreshJob = viewModelScope.launch {
            val latest = gateway.status()
            _status.value = latest
            if (latest != HealthConnectStatus.Ready) {
                previewJob?.cancel()
                _preview.value = null
            }
        }
    }

    fun showError(message: String) {
        refreshJob?.cancel()
        _status.value = HealthConnectStatus.Error(message)
    }

    /**
     * Explicit read-only preview of today's exercise for one saved mapping.
     * Does not insert Actions, source provenance, or alter manual completions.
     */
    fun previewToday(habitId: Int) {
        previewJob?.cancel()
        if (_status.value != HealthConnectStatus.Ready) {
            _preview.value = ExercisePreviewState.PermissionRequired
            return
        }
        _preview.value = ExercisePreviewState.Loading
        previewJob = viewModelScope.launch {
            try {
                val saved = mappingDao.getMapping(habitId)
                    ?: run {
                        _preview.value = ExercisePreviewState.Failure("Mapping no longer exists")
                        return@launch
                    }
                val kind = saved.exerciseKind.toExerciseKindOrNull()
                    ?: run {
                        _preview.value = ExercisePreviewState.Failure("Unsupported exercise type")
                        return@launch
                    }
                val zone = ZoneId.of(saved.zoneId)
                val today = LocalDate.now(zone)
                when (val result = exerciseReader.readMappedDay(saved, today)) {
                    ExerciseSessionReadResult.Unavailable ->
                        _preview.value = ExercisePreviewState.Unavailable
                    ExerciseSessionReadResult.PermissionRequired -> {
                        _preview.value = ExercisePreviewState.PermissionRequired
                        _status.value = HealthConnectStatus.PermissionRequired
                    }
                    is ExerciseSessionReadResult.Failure ->
                        _preview.value = ExercisePreviewState.Failure(result.message)
                    is ExerciseSessionReadResult.Success -> {
                        val alreadyCompleted = habitDao.getActionsForHabit(habitId)
                            .any { it.timestamp.atZone(zone).toLocalDate() == today }
                        val matching = matchExerciseDay(
                            ExerciseHabitMapping(habitId, kind, saved.minimumDurationMinutes),
                            result.sessions, today, zone,
                            manualCompletionExists = false
                        )
                        _preview.value = ExercisePreviewState.Result(
                            habitId = habitId,
                            minutes = matching.totalDuration.toMinutes(),
                            recordCount = matching.supportingRecordIds.size,
                            eligible = matching.eligible,
                            manualAlreadyComplete = alreadyCompleted
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _preview.value = ExercisePreviewState.Failure(
                    error.message ?: "Unable to preview exercise"
                )
            }
        }
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
                previewJob?.cancel()
                _preview.value = null
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
                previewJob?.cancel()
                _preview.value = null
                _mappingError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mappingError.value = error.message ?: "Could not remove activity mapping"
            }
        }
    }
}
