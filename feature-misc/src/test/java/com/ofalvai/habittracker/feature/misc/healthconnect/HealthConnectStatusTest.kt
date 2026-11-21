package com.ofalvai.habittracker.feature.misc.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectStatusTest {
    @Test fun unavailableProvider() {
        assertEquals(HealthConnectStatus.Unavailable, healthConnectStatus(false, false, false))
    }

    @Test fun setupRequired() {
        assertEquals(HealthConnectStatus.SetupRequired, healthConnectStatus(true, true, false))
    }

    @Test fun permissionRequired() {
        assertEquals(HealthConnectStatus.PermissionRequired, healthConnectStatus(true, false, false))
    }

    @Test fun readyOnlyWithPermission() {
        assertEquals(HealthConnectStatus.Ready, healthConnectStatus(true, false, true))
    }

    @Test fun providerAvailabilityTakesPrecedence() {
        assertEquals(HealthConnectStatus.Unavailable, healthConnectStatus(false, true, true))
    }
}
