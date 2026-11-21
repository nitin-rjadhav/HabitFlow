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
    var total = Duration.ZERO
    val contributingIds = linkedSetOf<String>()

    for (session in sessions.distinctBy { it.providerRecordId }) {
        if (session.providerRecordId.isBlank() ||
            session.kind != mapping.exerciseKind ||
            !session.endsAt.isAfter(session.startsAt)
        ) continue

        val intersectionStart = maxOf(session.startsAt, start)
        val intersectionEnd = minOf(session.endsAt, end)
        if (!intersectionEnd.isAfter(intersectionStart)) continue

        contributingIds += session.providerRecordId
        total = total.plus(Duration.between(intersectionStart, intersectionEnd))
    }
    val eligible = total >= Duration.ofMinutes(mapping.minimumDurationMinutes.toLong())
    return ExerciseDayMatch(eligible, total, contributingIds)
}
