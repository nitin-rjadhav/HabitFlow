package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTrendsTest {
    private fun month(vararg entries: Pair<Int, Int>): HeatmapMonth =
        HeatmapMonth(
            yearMonth = YearMonth.of(2026, 11),
            dayMap = entries.associate { (day, count) ->
                LocalDate.of(2026, 11, day) to HeatmapMonth.BucketInfo(0, count)
            }.toImmutableMap(),
            totalHabitCount = 4,
            bucketCount = 0,
            bucketMaxValues = persistentListOf()
        )

    @Test fun weeklyTotalsSplitOnMondayAndStreaksResetOnInactiveDays() {
        val trends = month(1 to 2, 2 to 1, 3 to 4, 5 to 2, 8 to 3, 9 to 1)
            .toActivityTrends(LocalDate.of(2026, 11, 9))
        assertEquals(listOf(
            WeeklyActivity(LocalDate.of(2026, 10, 26), 2),
            WeeklyActivity(LocalDate.of(2026, 11, 2), 13),
            WeeklyActivity(LocalDate.of(2026, 11, 9), 1)
        ), trends.weekly)
        assertEquals(2, trends.longestActiveDayStreak)
        assertEquals(1, trends.endingActiveDayStreak)
    }

    @Test fun futureCompletionsAreExcluded() {
        val trends = month(18 to 2, 19 to 1, 20 to 9)
            .toActivityTrends(LocalDate.of(2026, 11, 19))
        assertEquals(3, trends.weekly.sumOf { it.completions })
        assertEquals(2, trends.longestActiveDayStreak)
        assertEquals(2, trends.endingActiveDayStreak)
    }

    @Test fun futureMonthHasNoActivity() {
        assertEquals(ActivityTrends(emptyList(), 0, 0),
            month(1 to 5).toActivityTrends(LocalDate.of(2026, 10, 31)))
    }

    @Test fun emptyMonthHasZeroStreaksAndZeroWeeklyTotals() {
        val trends = month().toActivityTrends(LocalDate.of(2026, 11, 2))
        assertEquals(0, trends.longestActiveDayStreak)
        assertEquals(0, trends.endingActiveDayStreak)
        assertEquals(0, trends.weekly.sumOf { it.completions })
    }
}
