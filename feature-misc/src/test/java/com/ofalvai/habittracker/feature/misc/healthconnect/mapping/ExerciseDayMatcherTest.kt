package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDayMatcherTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 21)
    private val running = ExerciseHabitMapping(42, ExerciseKind.RUNNING, 30)

    private fun session(id: String, start: String, end: String, type: ExerciseKind = ExerciseKind.RUNNING) =
        ExerciseSession(id, type, Instant.parse(start), Instant.parse(end))

    @Test fun exactlyAtThresholdIsEligible() {
        val result = matchExerciseDay(
            running,
            listOf(session("one", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")),
            day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
        assertEquals(setOf("one"), result.supportingRecordIds)
    }

    @Test fun multipleShortSessionsAddUp() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("a", "2026-11-21T08:00:00Z", "2026-11-21T08:10:00Z"),
                session("b", "2026-11-21T09:00:00Z", "2026-11-21T09:20:00Z")
            ), day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(setOf("a", "b"), result.supportingRecordIds)
    }

    @Test fun duplicateRecordIdIsOnlyCountedOnce() {
        val first = session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:20:00Z")
        val result = matchExerciseDay(running, listOf(first, first), day, utc, false)
        assertFalse(result.eligible)
        assertEquals(Duration.ofMinutes(20), result.totalDuration)
    }

    @Test fun wrongActivityAndInvalidSessionsAreIgnored() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("bike", "2026-11-21T10:00:00Z", "2026-11-21T11:00:00Z", ExerciseKind.CYCLING),
                session("bad", "2026-11-21T10:20:00Z", "2026-11-21T10:10:00Z"),
                session("", "2026-11-21T09:00:00Z", "2026-11-21T10:00:00Z")
            ), day, utc, false
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ZERO, result.totalDuration)
    }

    @Test fun respectsLocalDayAcrossMidnightInIndia() {
        val india = ZoneId.of("Asia/Kolkata")
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 20),
            listOf(session("night", "2026-11-20T18:15:00Z", "2026-11-20T19:00:00Z")),
            day, india, false
        )
        // At 23:45 IST the previous day, ends at 00:30 IST of requested day.
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
    }

    @Test fun dstSpringForwardHasCorrectInstantBoundaries() {
        val ny = ZoneId.of("America/New_York")
        val transitionDay = LocalDate.of(2026, 3, 8)
        val result = matchExerciseDay(
            ExerciseHabitMapping(9, ExerciseKind.WALKING, 70),
            listOf(session("dst", "2026-03-08T06:30:00Z", "2026-03-08T07:45:00Z", ExerciseKind.WALKING)),
            transitionDay, ny, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(75), result.totalDuration)
    }

    @Test fun manualCompletionAlwaysWins() {
        val result = matchExerciseDay(
            running,
            listOf(session("record", "2026-11-21T10:00:00Z", "2026-11-21T11:00:00Z")),
            day, utc, true
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ZERO, result.totalDuration)
        assertTrue(result.supportingRecordIds.isEmpty())
    }

    @Test fun overlappingRecordsFromDifferentSourcesDoNotDoubleCount() {
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 40),
            listOf(
                session("watch", "2026-11-21T10:00:00Z", "2026-11-21T10:20:00Z"),
                session("phone", "2026-11-21T10:10:00Z", "2026-11-21T10:30:00Z")
            ), day, utc, false
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
        assertEquals(setOf("watch", "phone"), result.supportingRecordIds)
    }

    @Test fun adjacentNonOverlappingSessionsStillAddUp() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("watch", "2026-11-21T10:00:00Z", "2026-11-21T10:15:00Z"),
                session("phone", "2026-11-21T10:15:00Z", "2026-11-21T10:30:00Z")
            ), day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
    }


    @Test fun invalidDuplicateDoesNotHideLaterValidSession() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("same", "2026-11-21T10:30:00Z", "2026-11-21T10:00:00Z"),
                session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")
            ), day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
        assertEquals(setOf("same"), result.supportingRecordIds)
    }

    @Test fun unrelatedDuplicateDoesNotHideLaterMatchingActivity() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z", ExerciseKind.WALKING),
                session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")
            ), day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
    }

    @Test fun outOfDayDuplicateDoesNotHideTodaysMatchingSession() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("same", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z"),
                session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")
            ), day, utc, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
        assertEquals(setOf("same"), result.supportingRecordIds)
    }

    @Test fun dstFallBackCountsRepeatedHourAsRealElapsedTime() {
        val ny = ZoneId.of("America/New_York")
        val fallBackDay = LocalDate.of(2026, 11, 1)
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 90),
            listOf(session("fall-back", "2026-11-01T05:30:00Z", "2026-11-01T07:00:00Z")),
            fallBackDay, ny, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(90), result.totalDuration)
    }


    @Test fun conflictingDuplicateIdsUseLongestValidIntervalRegardlessOfOrder() {
        val short = session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:10:00Z")
        val long = session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")
        for (records in listOf(listOf(short, long), listOf(long, short))) {
            val result = matchExerciseDay(running, records, day, utc, false)
            assertTrue(result.eligible)
            assertEquals(Duration.ofMinutes(30), result.totalDuration)
            assertEquals(setOf("same"), result.supportingRecordIds)
        }
    }

    @Test fun conflictingDuplicateIdsCompareClippedDurationNotWholeSession() {
        val mostlyYesterday = session("same", "2026-11-20T20:00:00Z", "2026-11-21T00:05:00Z")
        val today = session("same", "2026-11-21T10:00:00Z", "2026-11-21T10:30:00Z")
        val result = matchExerciseDay(running, listOf(mostlyYesterday, today), day, utc, false)
        assertTrue(result.eligible)
        assertEquals(Duration.ofMinutes(30), result.totalDuration)
    }


    @Test fun springForwardDayClipsAtNextLocalMidnight() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        // This calendar day is only 23 hours long, not 24.
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 1440),
            listOf(session("whole-day", "2026-03-08T05:00:00Z", "2026-03-09T04:00:00Z")),
            date, zone, false
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ofHours(23), result.totalDuration)
    }

    @Test fun fallBackDayCanContainMoreThanTwentyFourHours() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 11, 1)
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 1440),
            listOf(session("whole-day", "2026-11-01T04:00:00Z", "2026-11-02T05:00:00Z")),
            date, zone, false
        )
        assertTrue(result.eligible)
        assertEquals(Duration.ofHours(25), result.totalDuration)
    }

    @Test fun sessionsTouchingMidnightWithoutOverlapDoNotContribute() {
        val result = matchExerciseDay(
            running,
            listOf(
                session("ends-at-start", "2026-11-20T23:00:00Z", "2026-11-21T00:00:00Z"),
                session("starts-at-end", "2026-11-22T00:00:00Z", "2026-11-22T01:00:00Z")
            ), day, utc, false
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ZERO, result.totalDuration)
        assertTrue(result.supportingRecordIds.isEmpty())
    }


    @Test fun crossingLocalMidnightCountsOnlySelectedDayAndUnionsOverlappingSources() {
        val india = ZoneId.of("Asia/Kolkata")
        val selected = LocalDate.of(2026, 11, 21)
        val records = listOf(
            // 23:50-00:20 IST: only 20 minutes belong to the selected date.
            session("watch", "2026-11-20T18:20:00Z", "2026-11-20T18:50:00Z"),
            // 00:10-00:40 IST: overlaps the first source by ten minutes.
            session("phone", "2026-11-20T18:40:00Z", "2026-11-20T19:10:00Z")
        )
        for (ordered in listOf(records, records.reversed())) {
            val result = matchExerciseDay(
                ExerciseHabitMapping(42, ExerciseKind.RUNNING, 40),
                ordered, selected, india, false
            )
            assertTrue(result.eligible)
            assertEquals(Duration.ofMinutes(40), result.totalDuration)
            assertEquals(setOf("watch", "phone"), result.supportingRecordIds)
        }
    }

    @Test fun crossingLocalMidnightDoesNotCountPreviousDayTowardThreshold() {
        val india = ZoneId.of("Asia/Kolkata")
        val result = matchExerciseDay(
            ExerciseHabitMapping(42, ExerciseKind.RUNNING, 21),
            listOf(session("watch", "2026-11-20T18:20:00Z", "2026-11-20T18:50:00Z")),
            LocalDate.of(2026, 11, 21), india, false
        )
        assertFalse(result.eligible)
        assertEquals(Duration.ofMinutes(20), result.totalDuration)
        assertEquals(setOf("watch"), result.supportingRecordIds)
    }

    @Test fun validatesUserSelectedMapping() {
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseHabitMapping(0, ExerciseKind.RUNNING, 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseHabitMapping(4, ExerciseKind.RUNNING, 0)
        }
    }
}
