package com.ofalvai.habittracker.feature.dashboard.ui.dashboard.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.feature.dashboard.R

/**
 * A badge is shown only when today's *actual* completed Action ID is still
 * linked to a local exercise-completion ledger. This is not a timestamp guess.
 */
fun isExerciseCompletedToday(actions: List<Action>, activeExerciseActionIds: Set<Int>): Boolean {
    val today = actions.lastOrNull() ?: return false
    return today.toggled && today.id > 0 && today.id in activeExerciseActionIds
}

/** Text is inherently announced by accessibility services. */
@Composable
fun ExerciseCompletionBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.dashboard_exercise_completion_badge),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall
    )
}
