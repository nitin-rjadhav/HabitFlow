/*
 * Copyright 2022 Olivér Falvai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ofalvai.habittracker.core.common

import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface Telemetry {
    enum class BreadcrumbType { Navigation, State, UserAction }
    fun initialize()
    fun logNonFatal(e: Throwable)
    fun leaveBreadcrumb(message: String, metadata: Map<String, Any>, type: BreadcrumbType)
}

/**
 * Local-only telemetry implementation. No crash data is transmitted to the
 * original upstream Bugsnag account. Revisit diagnostics with an owned endpoint
 * and explicit consent before a public release.
 */
class TelemetryImpl(
    private val appContext: android.content.Context,
    private val appPreferences: AppPreferences
) : Telemetry {
    override fun initialize() = Unit
    override fun logNonFatal(e: Throwable) {
        logcat(LogPriority.ERROR) { e.asLog() }
    }
    override fun leaveBreadcrumb(
        message: String,
        metadata: Map<String, Any>,
        type: Telemetry.BreadcrumbType
    ) = Unit
}
