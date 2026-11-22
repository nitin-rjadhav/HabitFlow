package com.ofalvai.habittracker.feature.misc.healthconnect

import java.time.Instant
import javax.inject.Inject

/**
 * A small injectable time source keeps preview freshness checks deterministic
 * in unit tests, without relying on a real midnight or a 15-minute delay.
 */
class HealthConnectTimeSource @Inject constructor() {
    fun now(): Instant = Instant.now()
}
