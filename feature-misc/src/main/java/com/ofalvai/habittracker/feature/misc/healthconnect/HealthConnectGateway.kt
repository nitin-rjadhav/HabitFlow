package com.ofalvai.habittracker.feature.misc.healthconnect

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * This gateway must not access Health Connect APIs on devices below API 26.
 * The manifest explicitly overrides the SDK library's minSdk to retain
 * HabitFlow's existing API 21 support, so runtime guards are mandatory.
 */
class HealthConnectGateway @Inject constructor(@ApplicationContext private val context: Context) {
    val exerciseReadPermission: String
        get() {
            check(isHealthConnectSdkSupported(Build.VERSION.SDK_INT)) {
                "Health Connect SDK requires Android API 26 or higher"
            }
            return HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        }

    fun sdkStatus(): Int {
        if (!isHealthConnectSdkSupported(Build.VERSION.SDK_INT)) {
            return HealthConnectClient.SDK_UNAVAILABLE
        }
        return HealthConnectClient.getSdkStatus(context)
    }

    fun isAvailable(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasExerciseReadPermission(): Boolean {
        if (!isAvailable()) return false
        return HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()
            .contains(exerciseReadPermission)
    }

    suspend fun status(): HealthConnectStatus {
        if (!isHealthConnectSdkSupported(Build.VERSION.SDK_INT)) {
            return HealthConnectStatus.Unavailable
        }
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

/** Pure check, also usable by UI to hide the setup action on older devices. */
internal fun isHealthConnectSdkSupported(deviceSdk: Int): Boolean =
    deviceSdk >= Build.VERSION_CODES.O
