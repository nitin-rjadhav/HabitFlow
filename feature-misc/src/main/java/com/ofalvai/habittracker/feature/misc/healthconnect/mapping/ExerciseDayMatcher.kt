package com.ofalvai.habittracker.feature.misc.healthconnect.mapping

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Types supported for the first opt-in exercise-to-habit mapping. */
enum class ExerciseKind { WALKING, RUNNING, CYCLING }

/** A user-selected mapping. No mapping means no automatic habit completions. */
data class ExerciseHabitMapping(
    val habitId: Int,
    val exerciseKind: ExerciseKind,
    val minimumDurationMinutes: Int
) {
    init {
        require(habitId > 0) { "A saved habit must be selected" }
        require(minimumDurationMinutes in 1..1440) { "Duration must be 1 to 1440 minutes" }
    }
}

/** Provider identity is needed to avoid counting a record twice across syncs. */
data class ExerciseSession(
    val providerRecordId: String,
    val kind: ExerciseKind,
    val startsAt: Instant,
    val endsAt: Instant
)

/**
 * This is an eligibility decision, NOT a database write.
 * A caller must persist provenance together with any eventual automatic completion.
 */
data class ExerciseDayMatch(
    val eligible: Boolean,
    val totalDuration: Duration,
    val supportingRecordIds: Set<String>
)

/**
 * Count only the portion of each exercise session falling within the selected
 * local calendar day. UTC instants cover DST transitions correctly.
 *
 * Deduplicate by provider record ID before summing. Existing manual completion
 * never generates a replacement automatic completion.
 */
fun matchExerciseDay(
    mapping: ExerciseHabitMapping,
    sessions: List<ExerciseSession>,
    day: LocalDate,
    zone: ZoneId,
    manualCompletionExists: Boolean
): ExerciseDayMatch {
    if (manualCompletionExists) {
        return ExerciseDayMatch(false, Duration.ZERO, emptySet())
    }
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    val contributingIds = linkedSetOf<String>()
    val clippedIntervals = mutableListOf<Pair<Instant, Instant>>()

    // A single exercise can be present in more than one app with different
    // provider IDs. Count overlapping wall-clock time only once.
    val validSessions = sessions.asSequence()
        // Validate before deduplication: a malformed or unrelated record must
        // not hide a later valid record with the same provider identity.
        .filter { it.providerRecordId.isNotBlank() &&
            it.kind == mapping.exerciseKind &&
            it.endsAt.isAfter(it.startsAt) &&
            // Exclude out-of-day records before deduplication. Otherwise a
            // stale record with the same provider ID can hide today's session.
            it.endsAt.isAfter(start) && it.startsAt.isBefore(end)
        }
        // A provider ID may occur in conflicting records. Prefer the longest
        // valid in-day interval rather than whichever duplicate arrived first.
        // This makes matching independent of provider pagination order.
        .groupBy { it.providerRecordId }
        .values.map { duplicates ->
            duplicates.maxWithOrNull(compareBy<ExerciseSession> {
                Duration.between(maxOf(it.startsAt, start), minOf(it.endsAt, end))
            }.thenBy { it.startsAt }.thenBy { it.endsAt })!!
        }
    for (session in validSessions) {

        val intersectionStart = maxOf(session.startsAt, start)
        val intersectionEnd = minOf(session.endsAt, end)
        if (!intersectionEnd.isAfter(intersectionStart)) continue

        contributingIds += session.providerRecordId
        clippedIntervals += intersectionStart to intersectionEnd
    }

    val sorted = clippedIntervals.sortedBy { it.first }
    var total = Duration.ZERO
    var mergedStart: Instant? = null
    var mergedEnd: Instant? = null
    for ((intervalStart, intervalEnd) in sorted) {
        val currentEnd = mergedEnd
        if (mergedStart == null || currentEnd == null) {
            mergedStart = intervalStart
            mergedEnd = intervalEnd
        } else if (!intervalStart.isAfter(currentEnd)) {
            mergedEnd = maxOf(currentEnd, intervalEnd)
        } else {
            total = total.plus(Duration.between(mergedStart, currentEnd))
            mergedStart = intervalStart
            mergedEnd = intervalEnd
        }
    }
    if (mergedStart != null && mergedEnd != null) {
        total = total.plus(Duration.between(mergedStart, mergedEnd))
    }

    val eligible = total >= Duration.ofMinutes(mapping.minimumDurationMinutes.toLong())
    return ExerciseDayMatch(eligible, total, contributingIds)
}
