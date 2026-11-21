package com.ofalvai.habittracker.feature.misc.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Read-only Health Connect gateway. Never creates a client when the provider is unavailable.
 * Permission requests are initiated explicitly by the UI, not here.
 */
class HealthConnectGateway(private val context: Context) {
    val exerciseReadPermission: String =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasExerciseReadPermission(): Boolean {
        if (!isAvailable()) return false
        return HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()
            .contains(exerciseReadPermission)
    }

    suspend fun status(): HealthConnectStatus {
        return try {
            when (sdkStatus()) {
                HealthConnectClient.SDK_UNAVAILABLE ->
                    HealthConnectStatus.Unavailable
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    HealthConnectStatus.SetupRequired
                HealthConnectClient.SDK_AVAILABLE ->
                    if (hasExerciseReadPermission()) HealthConnectStatus.Ready
                    else HealthConnectStatus.PermissionRequired
                else -> HealthConnectStatus.Unavailable
            }
        } catch (error: Exception) {
            HealthConnectStatus.Error(error.message ?: "Health Connect check failed")
        }
    }
}
