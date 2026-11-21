package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.ExerciseAutoCompletionDao
import com.ofalvai.habittracker.core.database.ExerciseApplyResult
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
import java.time.Duration
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


sealed interface ExerciseApplyState {
    data object Saving : ExerciseApplyState
    data object Saved : ExerciseApplyState
    data object Unchanged : ExerciseApplyState
    data object PermissionRequired : ExerciseApplyState
    data class Error(val message: String) : ExerciseApplyState
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val habitDao: HabitDao,
    private val mappingDao: ExerciseMappingDao,
    private val exerciseReader: HealthConnectExerciseReader,
    private val autoCompletionDao: ExerciseAutoCompletionDao
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

    private data class Candidate(
        val mapping: ExerciseHabitMappingEntity,
        val day: LocalDate,
        val duration: Duration,
        val ids: Set<String>
    )
    private var candidate: Candidate? = null
    private var applyJob: Job? = null
    private val _applyState = MutableStateFlow<ExerciseApplyState?>(null)
    val applyState: StateFlow<ExerciseApplyState?> = _applyState

    private fun clearPreview() {
        previewJob?.cancel()
        applyJob?.cancel()
        candidate = null
        _preview.value = null
        _applyState.value = null
    }

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        // Permission changes can race with an in-flight status check.
        refreshJob?.cancel()
        _status.value = null
        refreshJob = viewModelScope.launch {
            val latest = gateway.status()
            _status.value = latest
            if (latest != HealthConnectStatus.Ready) clearPreview()
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
        clearPreview()
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
                        if (matching.eligible && !alreadyCompleted) {
                            candidate = Candidate(saved, today, matching.totalDuration, matching.supportingRecordIds)
                        }
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

    /** Only the user's second explicit action can write a completion. */
    fun applyPreview(habitId: Int) {
        val pending = candidate
        if (_status.value != HealthConnectStatus.Ready ||
            pending == null || pending.mapping.habitId != habitId ||
            _applyState.value == ExerciseApplyState.Saving
        ) return
        _applyState.value = ExerciseApplyState.Saving
        applyJob = viewModelScope.launch {
            try {
                if (!gateway.hasExerciseReadPermission()) {
                    candidate = null
                    _applyState.value = ExerciseApplyState.PermissionRequired
                    _status.value = HealthConnectStatus.PermissionRequired
                    return@launch
                }
                _applyState.value = when (val result = autoCompletionDao.applyEligibleDay(
                    pending.mapping, pending.day, pending.duration, pending.ids
                )) {
                    is ExerciseApplyResult.Applied -> ExerciseApplyState.Saved
                    ExerciseApplyResult.AlreadyProcessed,
                    ExerciseApplyResult.ExistingCompletion -> ExerciseApplyState.Unchanged
                    else -> ExerciseApplyState.Error("Completion was not applied: $result")
                }
                candidate = null
                _preview.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                candidate = null
                _applyState.value = ExerciseApplyState.PermissionRequired
                _status.value = HealthConnectStatus.PermissionRequired
            } catch (error: Exception) {
                candidate = null
                _applyState.value = ExerciseApplyState.Error(
                    error.message ?: "Could not apply exercise completion"
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
                clearPreview()
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
                clearPreview()
                _mappingError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mappingError.value = error.message ?: "Could not remove activity mapping"
            }
        }
    }
}
