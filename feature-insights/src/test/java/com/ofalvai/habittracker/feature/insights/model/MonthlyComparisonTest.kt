package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyComparisonTest {
    private fun month(year: Int, month: Int, vararg days: Pair<Int, Int>): HeatmapMonth {
        val period = YearMonth.of(year, month)
        return HeatmapMonth(
            yearMonth = period,
            dayMap = days.associate { (day, count) ->
                period.atDay(day) to HeatmapMonth.BucketInfo(0, count)
            }.toImmutableMap(),
            totalHabitCount = 4,
            bucketCount = 0,
            bucketMaxValues = persistentListOf()
        )
    }

    @Test fun comparesOnlyElapsedDaysInCurrentMonth() {
        val current = month(2026, 11, 1 to 1, 19 to 3, 20 to 8)
        val previous = month(2026, 10, 1 to 2, 19 to 1, 20 to 7)
        assertEquals(
            MonthlyComparison(YearMonth.of(2026, 11), 19, 4, 3),
            compareMonthlyCompletions(current, previous, LocalDate.of(2026, 11, 19))
        )
    }

    @Test fun completedMonthsUseMatchingDaysOfShorterMonth() {
        val current = month(2026, 3, 1 to 2, 28 to 3, 31 to 9)
        val previous = month(2026, 2, 1 to 1, 28 to 2)
        assertEquals(
            MonthlyComparison(YearMonth.of(2026, 3), 28, 5, 3),
            compareMonthlyCompletions(current, previous, LocalDate.of(2026, 11, 19))
        )
    }

    @Test fun emptyMonthsCompareAsZero() {
        val result = compareMonthlyCompletions(
            month(2026, 10), month(2026, 9), LocalDate.of(2026, 11, 19)
        )
        assertEquals(0, result?.difference)
    }

    @Test fun futureMonthsHaveNoComparison() {
        assertNull(compareMonthlyCompletions(
            month(2026, 12), month(2026, 11), LocalDate.of(2026, 11, 19)
        ))
    }

    @Test fun mismatchedPreviousMonthHasNoComparison() {
        assertNull(compareMonthlyCompletions(
            month(2026, 11), month(2026, 9), LocalDate.of(2026, 11, 19)
        ))
    }
}
