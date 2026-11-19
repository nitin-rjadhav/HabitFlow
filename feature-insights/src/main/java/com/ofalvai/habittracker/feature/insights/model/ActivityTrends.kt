package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate
import java.time.YearMonth

/** Calendar-week totals (Monday to Sunday), clipped to the selected month and today. */
data class WeeklyActivity(val weekStart: LocalDate, val completions: Int)

/** Streaks count consecutive days with at least one recorded completion. */
data class ActivityTrends(
    val weekly: List<WeeklyActivity>,
    val longestActiveDayStreak: Int,
    val endingActiveDayStreak: Int
)

fun HeatmapMonth.toActivityTrends(today: LocalDate = LocalDate.now()): ActivityTrends {
    val last = minOf(yearMonth.atEndOfMonth(), today)
    val first = yearMonth.atDay(1)
    if (last.isBefore(first)) return ActivityTrends(emptyList(), 0, 0)

    val weekly = linkedMapOf<LocalDate, Int>()
    var longest = 0
    var consecutive = 0
    var day = first
    while (!day.isAfter(last)) {
        val count = dayMap[day]?.value?.coerceAtLeast(0) ?: 0
        val weekStart = day.minusDays((day.dayOfWeek.value - 1).toLong())
        weekly[weekStart] = (weekly[weekStart] ?: 0) + count
        consecutive = if (count > 0) consecutive + 1 else 0
        longest = maxOf(longest, consecutive)
        day = day.plusDays(1)
    }
    return ActivityTrends(weekly.map { (start, total) -> WeeklyActivity(start, total) }, longest, consecutive)
}
