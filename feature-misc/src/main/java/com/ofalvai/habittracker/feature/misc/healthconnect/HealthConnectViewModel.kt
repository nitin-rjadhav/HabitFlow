package com.ofalvai.habittracker.feature.misc.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ofalvai.habittracker.core.database.ExerciseMappingDao
import com.ofalvai.habittracker.core.database.ExerciseAutoCompletionDao
import com.ofalvai.habittracker.core.database.ExerciseApplyResult
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.ExerciseAutoCompletionLedger
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseHabitMapping
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.GENERIC_EXERCISE_READ_ERROR
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.ExerciseSessionReadResult
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.HealthConnectExerciseReader
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.matchExerciseDay
import com.ofalvai.habittracker.feature.misc.healthconnect.mapping.toExerciseKindOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Read-only preview state; applying a completion requires a second explicit action. */
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


/** A one-time, explicitly initiated foreground sync of today's mapped habits. */
sealed interface ExerciseSyncState {
    data object Running : ExerciseSyncState
    data object PermissionRequired : ExerciseSyncState
    data class Finished(
        val applied: Int,
        val unchanged: Int,
        val failed: Int
    ) : ExerciseSyncState
    data class Error(val message: String) : ExerciseSyncState
}

sealed interface ExerciseApplyState {
    data object Saving : ExerciseApplyState
    data object Saved : ExerciseApplyState
    data object Unchanged : ExerciseApplyState
    data object PermissionRequired : ExerciseApplyState
    data class Error(val message: String) : ExerciseApplyState
}

/**
 * A preview is valid only for its original local calendar day and for a brief
 * period. Applying stale results after midnight or a long delay is unsafe.
 */
internal fun isCurrentExercisePreview(
    day: LocalDate, zone: ZoneId, previewedAt: Instant, now: Instant
): Boolean {
    if (previewedAt.isAfter(now)) return false
    if (Duration.between(previewedAt, now) > Duration.ofMinutes(15)) return false
    return now.atZone(zone).toLocalDate() == day
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val habitDao: HabitDao,
    private val mappingDao: ExerciseMappingDao,
    private val exerciseReader: HealthConnectExerciseReader,
    private val autoCompletionDao: ExerciseAutoCompletionDao,
    private val syncReceiptStore: ExerciseSyncReceiptStore
) : ViewModel() {
    private val _status = MutableStateFlow<HealthConnectStatus?>(null)
    val status: StateFlow<HealthConnectStatus?> = _status
    private var refreshJob: Job? = null

    val availableHabits = habitDao.getActiveHabitsWithActions()
        .map { results -> results.map { it.habit } }

    val savedMappings = mappingDao.observeMappings()

    /** Local audit history remains readable after Health Connect is revoked or disconnected. */
    val completionHistory = autoCompletionDao.observeRecentHistory()

    private val _mappingError = MutableStateFlow<String?>(null)
    val mappingError: StateFlow<String?> = _mappingError

    private val _preview = MutableStateFlow<ExercisePreviewState?>(null)
    val preview: StateFlow<ExercisePreviewState?> = _preview
    private var previewJob: Job? = null

    private data class Candidate(
        val mapping: ExerciseHabitMappingEntity,
        val day: LocalDate,
        val duration: Duration,
        val ids: Set<String>,
        val previewedAt: Instant
    )
    private var candidate: Candidate? = null
    private var applyJob: Job? = null
    private var syncJob: Job? = null
    private val _syncState = MutableStateFlow<ExerciseSyncState?>(null)
    val syncState: StateFlow<ExerciseSyncState?> = _syncState

    // Last *finished* explicit sync, not a periodic/background sync indicator.
    private val _lastSyncReceipt = MutableStateFlow(syncReceiptStore.read())
    val lastSyncReceipt: StateFlow<ExerciseSyncReceipt?> = _lastSyncReceipt
    private val _applyState = MutableStateFlow<ExerciseApplyState?>(null)
    val applyState: StateFlow<ExerciseApplyState?> = _applyState

    private fun clearPreview() {
        previewJob?.cancel()
        applyJob?.cancel()
        syncJob?.cancel()
        _syncState.value = null
        candidate = null
        _preview.value = null
        _applyState.value = null
    }

    val exerciseReadPermission: String
        get() = gateway.exerciseReadPermission

    fun refresh() {
        // Permission changes can race with an in-flight status check.
        refreshJob?.cancel()
        // Invalidate any preview immediately; a pending permission check must
        // never leave an Apply action usable with stale permission state.
        clearPreview()
        _status.value = null
        refreshJob = viewModelScope.launch {
            val latest = gateway.status()
            _status.value = latest
            if (latest != HealthConnectStatus.Ready) clearPreview()
        }
    }

    fun showError(message: String) {
        refreshJob?.cancel()
        clearPreview()
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
                        _preview.value = ExercisePreviewState.Failure(GENERIC_EXERCISE_READ_ERROR)
                    is ExerciseSessionReadResult.Success -> {
                        val alreadyCompleted = habitDao.getActionsForHabit(habitId)
                            .any { it.timestamp.atZone(zone).toLocalDate() == today }
                        val matching = matchExerciseDay(
                            ExerciseHabitMapping(habitId, kind, saved.minimumDurationMinutes),
                            result.sessions, today, zone,
                            manualCompletionExists = false
                        )
                        if (matching.eligible && !alreadyCompleted) {
                            candidate = Candidate(
                                saved, today, matching.totalDuration,
                                matching.supportingRecordIds, Instant.now()
                            )
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
                _preview.value = ExercisePreviewState.Failure("Unable to preview exercise")
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
        val zone = try {
            ZoneId.of(pending.mapping.zoneId)
        } catch (error: Exception) {
            candidate = null
            _preview.value = null
            _applyState.value = ExerciseApplyState.Error("Invalid saved time zone; update the mapping")
            return
        }
        if (!isCurrentExercisePreview(pending.day, zone, pending.previewedAt, Instant.now())) {
            candidate = null
            _preview.value = null
            _applyState.value = ExerciseApplyState.Error(
                "Exercise preview expired or the local day changed. Preview today again."
            )
            return
        }
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
                _applyState.value = ExerciseApplyState.Error("Could not apply exercise completion")
            }
        }
    }

    /**
     * One-time opt-in bulk sync. This is never called from onResume, startup
     * or background work. The user taps the action after mapping and permission.
     *
     * Every successful local write goes through the transactional ledger:
     * archived habits, manual completions, duplicates, and user-undone
     * exercise completions are skipped rather than overwritten.
     */
    fun completeEligibleHabitsToday() {
        clearPreview()
        if (_status.value != HealthConnectStatus.Ready) {
            _syncState.value = ExerciseSyncState.PermissionRequired
            return
        }
        _syncState.value = ExerciseSyncState.Running
        syncJob = viewModelScope.launch {
            try {
                val selectedMappings = mappingDao.observeMappings().first()
                var applied = 0
                var unchanged = 0
                var failed = 0
                for (mapping in selectedMappings) {
                    // Re-check on every mapping in case Health Connect access is
                    // revoked while the foreground operation is in progress.
                    if (!gateway.hasExerciseReadPermission()) {
                        _status.value = HealthConnectStatus.PermissionRequired
                        _syncState.value = ExerciseSyncState.PermissionRequired
                        return@launch
                    }
                    try {
                        val kind = mapping.exerciseKind.toExerciseKindOrNull()
                        if (kind == null) {
                            failed++
                            continue
                        }
                        val zone = ZoneId.of(mapping.zoneId)
                        val today = LocalDate.now(zone)
                        when (val read = exerciseReader.readMappedDay(mapping, today)) {
                            ExerciseSessionReadResult.PermissionRequired -> {
                                _status.value = HealthConnectStatus.PermissionRequired
                                _syncState.value = ExerciseSyncState.PermissionRequired
                                return@launch
                            }
                            ExerciseSessionReadResult.Unavailable,
                            is ExerciseSessionReadResult.Failure -> {
                                failed++
                            }
                            is ExerciseSessionReadResult.Success -> {
                                val match = matchExerciseDay(
                                    ExerciseHabitMapping(
                                        mapping.habitId, kind, mapping.minimumDurationMinutes
                                    ),
                                    read.sessions, today, zone,
                                    manualCompletionExists = false
                                )
                                if (!match.eligible) {
                                    unchanged++
                                    continue
                                }
                                // Do not apply records across local midnight.
                                if (LocalDate.now(zone) != today) {
                                    failed++
                                    continue
                                }
                                if (!gateway.hasExerciseReadPermission()) {
                                    _status.value = HealthConnectStatus.PermissionRequired
                                    _syncState.value = ExerciseSyncState.PermissionRequired
                                    return@launch
                                }
                                when (autoCompletionDao.applyEligibleDay(
                                    mapping, today, match.totalDuration, match.supportingRecordIds
                                )) {
                                    is ExerciseApplyResult.Applied -> applied++
                                    ExerciseApplyResult.AlreadyProcessed,
                                    ExerciseApplyResult.ExistingCompletion,
                                    ExerciseApplyResult.Ineligible,
                                    ExerciseApplyResult.MappingChanged,
                                    ExerciseApplyResult.MissingOrArchivedHabit -> unchanged++
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (revoked: SecurityException) {
                        _status.value = HealthConnectStatus.PermissionRequired
                        _syncState.value = ExerciseSyncState.PermissionRequired
                        return@launch
                    } catch (error: Exception) {
                        failed++
                    }
                }
                _syncState.value = ExerciseSyncState.Finished(applied, unchanged, failed)
                val receipt = ExerciseSyncReceipt(
                    Instant.now().toEpochMilli(), applied, unchanged, failed
                )
                // Persistence failures must not misreport the completed sync as failed:
                // some habit actions may already have been committed.
                try {
                    syncReceiptStore.write(receipt)
                    _lastSyncReceipt.value = receipt
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The in-memory result remains truthful for this session.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (revoked: SecurityException) {
                _status.value = HealthConnectStatus.PermissionRequired
                _syncState.value = ExerciseSyncState.PermissionRequired
            } catch (error: Exception) {
                _syncState.value = ExerciseSyncState.Error("Unable to complete today's mapped habits")
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
                _mappingError.value = "Could not save activity mapping"
            }
        }
    }

    /** Stop Health Connect usage without deleting any manually/automatically completed habits. */
    fun disconnect() {
        clearPreview()
        viewModelScope.launch {
            try {
                mappingDao.removeAllMappings()
                syncReceiptStore.clear()
                _lastSyncReceipt.value = null
                _mappingError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mappingError.value = "Could not remove exercise mappings"
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
                _mappingError.value = "Could not remove activity mapping"
            }
        }
    }
}
