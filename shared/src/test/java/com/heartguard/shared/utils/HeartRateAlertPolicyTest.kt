package com.heartguard.shared.utils

import com.heartguard.shared.models.AlertType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateAlertPolicyTest {
    @Test
    fun dedicatedLowTypesAlwaysMatch() {
        assertTrue(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_LOW.name, null))
        assertTrue(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_CRITICAL_LOW.name, 40))
    }

    @Test
    fun oldWatchHighTypeWithLowReadingMatches() {
        assertTrue(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_HIGH.name, 70))
        assertTrue(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_HIGH.name, 65))
    }

    @Test
    fun highReadingDoesNotMatchEvenOnHighType() {
        assertFalse(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_HIGH.name, 120))
        assertFalse(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.SOS_MANUAL.name, 65))
        assertFalse(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_HIGH.name, 0))
        assertFalse(HeartRateAlertPolicy.isLowHeartRateAlert(AlertType.HEART_RATE_HIGH.name, null))
    }

    @Test
    fun thresholdIsInclusiveAt70() {
        assertTrue(HeartRateAlertPolicy.isAtOrBelowLowThreshold(70))
        assertFalse(HeartRateAlertPolicy.isAtOrBelowLowThreshold(71))
        assertFalse(HeartRateAlertPolicy.isAtOrBelowLowThreshold(0))
    }
}
