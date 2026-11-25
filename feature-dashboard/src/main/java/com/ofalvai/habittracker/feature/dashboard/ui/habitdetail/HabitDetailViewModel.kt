/*
 * Copyright 2022 Olivér Falvai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ofalvai.habittracker.feature.dashboard.ui.habitdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ofalvai.habittracker.core.common.OnboardingManager
import com.ofalvai.habittracker.core.common.Telemetry
import com.ofalvai.habittracker.core.database.HabitDao
import com.ofalvai.habittracker.core.database.entity.HabitById
import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.model.HabitWithActions
import com.ofalvai.habittracker.core.ui.state.Result
import com.ofalvai.habittracker.feature.dashboard.mapper.actionsToHistory
import com.ofalvai.habittracker.feature.dashboard.mapper.mapActionCountByMonth
import com.ofalvai.habittracker.feature.dashboard.mapper.mapActionCountByMonthListToItemList
import com.ofalvai.habittracker.feature.dashboard.mapper.mapActionCountByWeek
import com.ofalvai.habittracker.feature.dashboard.mapper.mapActionCountByWeekListToItemList
import com.ofalvai.habittracker.feature.dashboard.mapper.mapHabitSingleStats
import com.ofalvai.habittracker.feature.dashboard.mapper.toEntity
import com.ofalvai.habittracker.feature.dashboard.mapper.toUIColor
import com.ofalvai.habittracker.feature.dashboard.repo.ActionRepository
import com.ofalvai.habittracker.feature.dashboard.ui.model.ActionCountByMonth
import com.ofalvai.habittracker.feature.dashboard.ui.model.ActionCountByWeek
import com.ofalvai.habittracker.feature.dashboard.ui.model.ActionCountChart
import com.ofalvai.habittracker.feature.dashboard.ui.model.SingleStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

private val initialSingleStats = SingleStats(null, 0, 0, 0f)

enum class HabitDetailEvent {
    BackNavigation,
    DeleteError,
    ArchiveError
}

enum class HabitEditSaveState { Idle, Saving, Failed, Saved }

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    private val dao: HabitDao,
    private val actionRepository: ActionRepository,
    private val telemetry: Telemetry,
    onboardingManager: OnboardingManager
) : ViewModel() {

    val habitWithActions = MutableStateFlow<Result<HabitWithActions>>(Result.Loading)
    val singleStats = MutableStateFlow(initialSingleStats)
    val chartData = MutableStateFlow<Result<ActionCountChart>>(Result.Loading)
    private var selectedChartType = ActionCountChart.Type.Weekly
    private var statsJob: Job? = null

    private val mutableEditSaveState = MutableStateFlow(HabitEditSaveState.Idle)
    val editSaveState = mutableEditSaveState.asStateFlow()

    private val eventChannel = Channel<HabitDetailEvent>(Channel.BUFFERED)
    val habitDetailEvent = eventChannel.receiveAsFlow()

    private val actionCountByWeek = MutableStateFlow<List<ActionCountByWeek>>(emptyList())
    private val actionCountByMonth = MutableStateFlow<List<ActionCountByMonth>>(emptyList())

    // Store loaded habit's order to convert the model back to entity later
    private var habitOrder: Int? = null

    init {
        onboardingManager.habitDetailsOpened()
    }

    fun fetchHabitDetails(habitId: Int): Job {
        return viewModelScope.launch {
            try {
                val habit = dao.getHabitWithActions(habitId).let {
                    habitOrder = it.habit.order

                    // TODO: unify this with the regular mapping (where empty day action are filled)
                    HabitWithActions(
                        Habit(it.habit.id, it.habit.name, it.habit.color.toUIColor(), it.habit.notes),
                        it.actions.map { action ->
                            Action(action.id, toggled = true, timestamp = action.timestamp)
                        }.toImmutableList(),
                        it.actions.size,
                        actionsToHistory(it.actions)
                    )
                }
                habitWithActions.value = Result.Success(habit)
            } catch (e: Throwable) {
                telemetry.logNonFatal(e)
                habitWithActions.value = Result.Failure(e)
            }
        }
    }

    fun fetchHabitStats(habitId: Int): Job {
        statsJob?.cancel()
        chartData.value = Result.Loading
        return viewModelScope.launch {
            try {
                // Query sequentially so a child failure cannot cancel the parent
                // before the error state is published.
                val completionRate = dao.getCompletionRate(habitId)
                val weekly = dao.getActionCountByWeek(habitId)
                val monthly = dao.getActionCountByMonth(habitId)
                val summary = mapHabitSingleStats(completionRate, weekly, LocalDate.now())
                val weeklyCounts = mapActionCountByWeek(weekly)
                val monthlyCounts = mapActionCountByMonth(monthly)
                ensureActive()
                singleStats.value = summary
                actionCountByWeek.value = weeklyCounts
                actionCountByMonth.value = monthlyCounts
                publishChart(selectedChartType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                chartData.value = Result.Failure(e)
                telemetry.logNonFatal(e)
            }
        }.also { statsJob = it }
    }

    fun toggleAction(habitId: Int, action: Action, date: LocalDate) {
        viewModelScope.launch {
            actionRepository.toggleAction(habitId, action, date)
            fetchHabitDetails(habitId)
            fetchHabitStats(habitId)
        }
    }

    fun beginEdit() {
        if (mutableEditSaveState.value != HabitEditSaveState.Saving) {
            mutableEditSaveState.value = HabitEditSaveState.Idle
        }
    }

    fun updateHabit(habit: Habit) {
        val previous = mutableEditSaveState.value
        if (previous != HabitEditSaveState.Idle && previous != HabitEditSaveState.Failed) return
        if (!mutableEditSaveState.compareAndSet(previous, HabitEditSaveState.Saving)) return
        viewModelScope.launch {
            try {
                dao.updateHabit(habit.toEntity(order = habitOrder ?: 0, archived = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEditSaveState.value = HabitEditSaveState.Failed
                telemetry.logNonFatal(e)
                return@launch
            }
            // Refresh after confirmed persistence; do not classify refresh failure as a failed write.
            fetchHabitDetails(habit.id).join()
            mutableEditSaveState.value = HabitEditSaveState.Saved
        }
    }

    fun archiveHabit(habit: Habit) {
        viewModelScope.launch {
            try {
                dao.updateHabit(habit.toEntity(order = habitOrder ?: 0, archived = true))
                eventChannel.send(HabitDetailEvent.BackNavigation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                telemetry.logNonFatal(e)
                eventChannel.send(HabitDetailEvent.ArchiveError)
            }
        }
    }

    /** Delete the habit and all dependent actions/provenance; navigate only on success. */
    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            try {
                dao.deleteHabit(HabitById(habit.id))
                eventChannel.send(HabitDetailEvent.BackNavigation)
            } catch (e: Exception) {
                telemetry.logNonFatal(e)
                eventChannel.send(HabitDetailEvent.DeleteError)
            }
        }
    }

    fun switchChartType(newType: ActionCountChart.Type) {
        selectedChartType = newType
        // Cached values must not mask failed or incomplete database reads.
        if (chartData.value is Result.Success) publishChart(newType)
    }

    private fun publishChart(newType: ActionCountChart.Type) {
        try {
            val items = when (newType) {
                ActionCountChart.Type.Weekly -> mapActionCountByWeekListToItemList(
                    actionCountByWeek.value,
                    LocalDate.now(),
                    Locale.getDefault()
                )
                ActionCountChart.Type.Monthly -> mapActionCountByMonthListToItemList(
                    actionCountByMonth.value,
                    LocalDate.now()
                )
            }
            chartData.value = Result.Success(ActionCountChart(items, newType))
        } catch (e: Throwable) {
            chartData.value = Result.Failure(e)
            telemetry.logNonFatal(e)
        }
    }
}