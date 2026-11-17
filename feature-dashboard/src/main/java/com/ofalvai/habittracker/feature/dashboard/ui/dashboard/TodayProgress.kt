package com.ofalvai.habittracker.feature.dashboard.ui.dashboard

import com.ofalvai.habittracker.core.model.HabitWithActions

/** Computes today's completion from the most recent day in each mapped action list. */
internal data class TodayProgressState(val completed: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()
}

internal fun todayProgress(habits: List<HabitWithActions>): TodayProgressState =
    TodayProgressState(
        completed = habits.count { it.actions.lastOrNull()?.toggled == true },
        total = habits.size
    )
