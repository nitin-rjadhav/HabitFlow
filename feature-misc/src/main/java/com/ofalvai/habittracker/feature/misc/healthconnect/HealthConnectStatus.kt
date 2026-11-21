package com.ofalvai.habittracker.feature.misc.healthconnect

/**
 * UI-facing states for the opt-in Health Connect integration.
 *
 * Keep availability separate from permission state: a provider can be installed
 * while the user has not granted access, and access can be revoked at any time.
 */
sealed interface HealthConnectStatus {
    data object Unavailable : HealthConnectStatus
    data object SetupRequired : HealthConnectStatus
    data object PermissionRequired : HealthConnectStatus
    data object Ready : HealthConnectStatus
    data class Error(val message: String) : HealthConnectStatus
}

/** The UI never interprets an ungranted permission as an empty activity history. */
fun healthConnectStatus(
    providerAvailable: Boolean,
    providerSetupRequired: Boolean,
    requiredPermissionsGranted: Boolean
): HealthConnectStatus = when {
    !providerAvailable -> HealthConnectStatus.Unavailable
    providerSetupRequired -> HealthConnectStatus.SetupRequired
    !requiredPermissionsGranted -> HealthConnectStatus.PermissionRequired
    else -> HealthConnectStatus.Ready
}
