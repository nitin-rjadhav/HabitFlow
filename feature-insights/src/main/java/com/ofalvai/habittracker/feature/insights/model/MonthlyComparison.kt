package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Compares like-for-like calendar days in the selected and preceding months.
 * For the current month, only elapsed days are counted; for completed months,
 * both periods are limited to the shorter month's length.
 */
data class MonthlyComparison(
    val yearMonth: YearMonth,
    val daysCompared: Int,
    val currentCompletions: Int,
    val previousCompletions: Int
) {
    val difference: Int get() = currentCompletions - previousCompletions
}

fun compareMonthlyCompletions(
    current: HeatmapMonth,
    previous: HeatmapMonth,
    today: LocalDate = LocalDate.now()
): MonthlyComparison? {
    val selected = current.yearMonth
    if (previous.yearMonth != selected.minusMonths(1) ||
        selected > YearMonth.from(today)) return null

    val elapsedDays = if (selected == YearMonth.from(today)) today.dayOfMonth
        else selected.lengthOfMonth()
    val days = min(elapsedDays, min(selected.lengthOfMonth(), previous.yearMonth.lengthOfMonth()))

    fun sumThrough(month: HeatmapMonth): Int =
        month.dayMap.entries.sumOf { (date, bucket) ->
            if (YearMonth.from(date) == month.yearMonth && date.dayOfMonth <= days) {
                bucket.value.coerceAtLeast(0)
            } else 0
        }

    return MonthlyComparison(
        yearMonth = selected,
        daysCompared = days,
        currentCompletions = sumThrough(current),
        previousCompletions = sumThrough(previous)
    )
}
