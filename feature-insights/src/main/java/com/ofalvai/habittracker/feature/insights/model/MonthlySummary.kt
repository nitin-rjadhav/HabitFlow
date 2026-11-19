package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate

/** Counts observed habit completions from the same daily totals displayed in the heatmap. */
data class MonthlySummary(
    val completions: Int,
    val activeDays: Int,
    val bestDayCompletions: Int
)

fun HeatmapMonth.toMonthlySummary(today: LocalDate = LocalDate.now()): MonthlySummary {
    val values = dayMap
        .filterKeys { date -> date.year == yearMonth.year &&
            date.month == yearMonth.month && !date.isAfter(today) }
        .values
        .map { it.value.coerceAtLeast(0) }

    return MonthlySummary(
        completions = values.sum(),
        activeDays = values.count { it > 0 },
        bestDayCompletions = values.maxOrNull() ?: 0
    )
}
