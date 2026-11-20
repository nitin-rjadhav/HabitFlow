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

package com.ofalvai.habittracker.feature.insights.ui.component

import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.ui.component.CalendarDayLegend
import com.ofalvai.habittracker.core.ui.component.CalendarPager
import com.ofalvai.habittracker.core.ui.component.ErrorView
import com.ofalvai.habittracker.core.ui.component.HorizontalMonthCalendar
import com.ofalvai.habittracker.core.ui.recomposition.StableHolder
import com.ofalvai.habittracker.core.ui.state.Result
import com.ofalvai.habittracker.core.ui.theme.AppDimensions
import com.ofalvai.habittracker.core.ui.theme.LocalAppColors
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.insights.R
import com.ofalvai.habittracker.feature.insights.model.HeatmapMonth
import com.ofalvai.habittracker.feature.insights.model.MonthlyComparison
import com.ofalvai.habittracker.feature.insights.model.toMonthlySummary
import com.ofalvai.habittracker.feature.insights.model.toActivityTrends
import com.ofalvai.habittracker.feature.insights.ui.InsightsIcons
import com.ofalvai.habittracker.feature.insights.ui.InsightsViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun Heatmap(viewModel: InsightsViewModel) {

    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    val heatmapState by viewModel.heatmapState.collectAsState()
    val completedHabitsAtDate by viewModel.heatmapCompletedHabitsAtDate.collectAsState()
    val comparison by viewModel.monthlyComparison.collectAsState()

    val onMonthChange: (YearMonth) -> Unit = {
        yearMonth = it
        viewModel.fetchHeatmap(yearMonth)
    }
    val onLoadHabitsAt: (LocalDate) -> Unit = {
        viewModel.fetchCompletedHabitsAt(it)
    }

    Heatmap(StableHolder(yearMonth), heatmapState, completedHabitsAtDate, onMonthChange, onLoadHabitsAt, comparison)
}

@Composable
fun Heatmap(
    yearMonth: StableHolder<YearMonth>,
    heatmapState: Result<HeatmapMonth>,
    completedHabitsAtDate: ImmutableList<Habit>?,
    onMonthChange: (YearMonth) -> Unit,
    onLoadHabitsAt: (LocalDate) -> Unit,
    comparison: MonthlyComparison? = null
) {
    InsightCard(
        iconPainter = InsightsIcons.Heatmap,
        title = stringResource(R.string.insights_heatmap_title),
        description = stringResource(R.string.insights_heatmap_description),
    ) {
        Column {
            CalendarPager(
                yearMonth = yearMonth,
                onPreviousClick = { onMonthChange(yearMonth.item.minusMonths(1)) },
                onNextClick = { onMonthChange(yearMonth.item.plusMonths(1)) }
            )

            CalendarDayLegend()

            when (heatmapState) {
                is Result.Success -> {
                    val heatmapData = heatmapState.value
                    // A month change is asynchronous: never display the previous
                    // month's totals under the newly selected month header.
                    if (heatmapData.yearMonth != yearMonth.item) {
                        Spacer(Modifier.height(280.dp))
                    } else {
                    val enoughData = hasEnoughData(heatmapData)

                    MonthlySummaryRow(heatmapData)
                    ActivityTrendsRow(heatmapData)
                    if (comparison?.yearMonth == heatmapData.yearMonth) {
                        MonthlyComparisonLabel(comparison)
                    }

                    if (!enoughData) {
                        EmptyView()
                    }
                    HeatmapCalendar(yearMonth, heatmapData, completedHabitsAtDate, onLoadHabitsAt, onMonthChange)

                    if (enoughData) {
                        HeatmapLegend(
                            heatmapData,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                        )
                    }
                    }
                }

                Result.Loading -> {
                    Spacer(Modifier.height(280.dp))
                }
                is Result.Failure -> {
                    ErrorView(label = stringResource(R.string.insights_heatmap_error))
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryRow(month: HeatmapMonth) {
    val summary = month.toMonthlySummary()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimensions.spacingMd),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryMetric(
            label = stringResource(R.string.insights_month_completions),
            value = summary.completions
        )
        SummaryMetric(
            label = stringResource(R.string.insights_month_active_days),
            value = summary.activeDays
        )
        SummaryMetric(
            label = stringResource(R.string.insights_month_best_day),
            value = summary.bestDayCompletions
        )
    }
}

@Composable
private fun ActivityTrendsRow(month: HeatmapMonth) {
    val trends = month.toActivityTrends()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppDimensions.spacingSm)) {
        Text(
            text = stringResource(
                R.string.insights_activity_streaks,
                trends.longestActiveDayStreak,
                trends.endingActiveDayStreak
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                R.string.insights_weekly_activity,
                trends.weekly.joinToString(" · ") { it.completions.toString() }
                    .ifEmpty { "0" }
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MonthlyComparisonLabel(comparison: MonthlyComparison) {
    val difference = comparison.difference
    val signed = if (difference > 0) "+$difference" else difference.toString()
    Text(
        text = stringResource(
            R.string.insights_month_comparison,
            signed,
            comparison.daysCompared
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryMetric(label: String, value: Int) {
    Column(
        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeatmapCalendar(
    yearMonth: StableHolder<YearMonth>,
    heatmapData: HeatmapMonth,
    completedHabitsAtDate: ImmutableList<Habit>?,
    onLoadHabitsAt: (LocalDate) -> Unit,
    onMonthSwipe: (YearMonth) -> Unit
) {
    var selectedDate by remember(yearMonth.item) { mutableStateOf<LocalDate?>(null) }
    val onDayClick: (LocalDate) -> Unit = {
        selectedDate = it
        onLoadHabitsAt(it)
    }

    selectedDate?.let { date ->
        if (completedHabitsAtDate != null) {
            DayPopup(date, completedHabitsAtDate, onDismiss = { selectedDate = null })
        }
    }

    HorizontalMonthCalendar(yearMonth, onMonthSwipe) {
        val dayData = heatmapData.dayMap[it.date] ?: HeatmapMonth.BucketInfo(0, 0)
        DayCell(it, dayData, heatmapData.bucketCount, selectedDate == it.date, onDayClick)
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    bucketInfo: HeatmapMonth.BucketInfo,
    bucketCount: Int,
    isSelected: Boolean,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val completionDescription = stringResource(R.string.insights_day_completion_count, bucketInfo.value)
    val color = MaterialTheme.colorScheme.tertiary
        .adjustToBucketIndex(bucketInfo.bucketIndex, bucketCount)

    val dayOutline = when {
        isSelected -> Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
        today == day.date -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        else -> Modifier
    }
    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                text = AnnotatedString(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(day.date))
                stateDescription = completionDescription
                selected = isSelected
            }
            .padding(4.dp)
            .then(dayOutline)
            .clip(CircleShape)
            .clickable(role = Role.Button) { onDayClick(day.date) }
            .aspectRatio(1f)
            .background(color, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val isFaded = day.position != DayPosition.MonthDate || day.date.isAfter(today)
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(if (isFaded) 0.5f else 1f),
        )
    }
}

@Composable
private fun DayPopup(
    date: LocalDate,
    habits: ImmutableList<Habit>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = MaterialTheme.shapes.small
        Box(
            Modifier
                .shadow(4.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(AppDimensions.spacingMd)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(AppDimensions.spacingSm))
                Text(
                    text = LocalContext.current.resources.getQuantityString(
                        R.plurals.insights_heatmap_popup_action_count,
                        habits.size,
                        habits.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Divider(Modifier.padding(bottom = AppDimensions.spacingSm, top = AppDimensions.spacingXs))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (habits.isEmpty()) {
                        Text(
                            text = stringResource(R.string.insights_heatmap_no_completed_habits),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        habits.forEach { habit ->
                            Text(
                                text = habit.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = AppDimensions.spacingXs)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppDimensions.spacingSm))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.insights_heatmap_close_details))
                }
            }

        }
    }
}

@Composable
private fun HeatmapLegend(
    heatmapData: HeatmapMonth,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        Text(
            text = stringResource(R.string.insights_heatmap_legend_label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp).alignByBaseline()
        )

        Row(
            Modifier.border(1.dp, LocalAppColors.current.gray2).alignByBaseline()
        ) {
            heatmapData.bucketMaxValues.forEach {
                val bucketIndex = it.first
                val maxValue = it.second
                val backgroundColor = MaterialTheme.colorScheme.tertiary.adjustToBucketIndex(
                    bucketIndex, heatmapData.bucketCount
                )
                Box(
                    Modifier.background(backgroundColor).size(24.dp)
                ) {
                    Text(
                        text = maxValue.toString(),
                        color = contentColorFor(backgroundColor),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxSize().padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyView() {
    Text(
        text = stringResource(R.string.insights_heatmap_empty_label),
        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun hasEnoughData(heatmapData: HeatmapMonth): Boolean {
    return heatmapData.totalHabitCount > 0
}

@ColorInt
private fun Color.adjustToBucketIndex(index: Int, bucketCount: Int): Color {
    if (index > 0 && index >= bucketCount) {
        throw IllegalArgumentException("Bucket index ($index) outside of bucket range (count=$bucketCount)")
    }

    return if (bucketCount == 0) {
        return Color.Transparent
    } else {
        this.copy(alpha = index / bucketCount.toFloat())
    }
}

@Preview
@ShowkaseComposable(name = "Heatmap", group = "Insights")
@Composable
fun PreviewHeatmap() {
    PreviewTheme {
        var yearMonth by remember { mutableStateOf(YearMonth.of(2021, 4)) }
        val heatmapState = Result.Success(
            HeatmapMonth(
                yearMonth = yearMonth,
                dayMap = persistentMapOf(
                    LocalDate.of(2021, 4, 20) to HeatmapMonth.BucketInfo(2, 2),
                    LocalDate.of(2021, 4, 21) to HeatmapMonth.BucketInfo(1, 1),
                    LocalDate.of(2021, 4, 22) to HeatmapMonth.BucketInfo(0, 0),
                    LocalDate.of(2021, 4, 23) to HeatmapMonth.BucketInfo(3, 3),
                    LocalDate.of(2021, 4, 24) to HeatmapMonth.BucketInfo(4, 4),
                ),
                totalHabitCount = 4,
                bucketCount = 5,
                bucketMaxValues = persistentListOf(
                    0 to 0,
                    1 to 1,
                    2 to 2,
                    3 to 3,
                    4 to 4
                )
            )
        )

        val onMonthChange: (YearMonth) -> Unit = {
            yearMonth = it
        }

        Heatmap(StableHolder(yearMonth), heatmapState, null, onMonthChange, {})
    }
}