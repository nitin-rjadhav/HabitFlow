package com.ofalvai.habittracker.feature.insights.model

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlySummaryTest {
    private fun month(vararg entries: Pair<LocalDate, Int>): HeatmapMonth =
        HeatmapMonth(
            yearMonth = YearMonth.of(2026, 11),
            dayMap = entries.associate { (date, count) ->
                date to HeatmapMonth.BucketInfo(0, count)
            }.toImmutableMap(),
            totalHabitCount = 4,
            bucketCount = 0,
            bucketMaxValues = persistentListOf()
        )

    @Test fun emptyMonthHasZeroMetrics() {
        assertEquals(MonthlySummary(0, 0, 0), month().toMonthlySummary(LocalDate.of(2026, 11, 19)))
    }

    @Test fun sumsCompletionsAndCountsOnlyDaysWithActivity() {
        val data = month(
            LocalDate.of(2026, 11, 1) to 2,
            LocalDate.of(2026, 11, 2) to 0,
            LocalDate.of(2026, 11, 3) to 4,
            LocalDate.of(2026, 11, 4) to 1
        )
        assertEquals(MonthlySummary(7, 3, 4), data.toMonthlySummary(LocalDate.of(2026, 11, 19)))
    }

    @Test fun ignoresFutureAndOutsideMonthEntries() {
        val data = month(
            LocalDate.of(2026, 11, 18) to 2,
            LocalDate.of(2026, 11, 20) to 4,
            LocalDate.of(2026, 10, 31) to 3
        )
        assertEquals(MonthlySummary(2, 1, 2), data.toMonthlySummary(LocalDate.of(2026, 11, 19)))
    }
}
