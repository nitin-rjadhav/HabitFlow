package com.ofalvai.habittracker.feature.misc.healthconnect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectSdkSupportTest {
    @Test fun unavailableOnLegacyAndroid() {
        assertFalse(isHealthConnectSdkSupported(21))
        assertFalse(isHealthConnectSdkSupported(25))
    }

    @Test fun supportedFromAndroidOreo() {
        assertTrue(isHealthConnectSdkSupported(26))
        assertTrue(isHealthConnectSdkSupported(35))
        assertTrue(isHealthConnectSdkSupported(36))
    }
}
