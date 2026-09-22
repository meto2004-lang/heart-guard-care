package com.heartguard.shared.utils

import com.heartguard.shared.constants.SensorConstants
import com.heartguard.shared.models.AlertType

object HeartRateAlertPolicy {
    fun isAtOrBelowLowThreshold(hr: Int): Boolean =
        hr > 0 && hr <= SensorConstants.HR_LOW_THRESHOLD

    /**
     * True for dedicated low-HR types, and for older watches that labeled a
     * low reading as [AlertType.HEART_RATE_HIGH] while still sending the bpm.
     */
    fun isLowHeartRateAlert(type: String, heartRate: Int?): Boolean {
        if (type.equals(AlertType.HEART_RATE_LOW.name, ignoreCase = true) ||
            type.equals(AlertType.HEART_RATE_CRITICAL_LOW.name, ignoreCase = true)
        ) {
            return true
        }
        val hr = heartRate ?: return false
        return isAtOrBelowLowThreshold(hr) &&
            (type.equals(AlertType.HEART_RATE_HIGH.name, ignoreCase = true) ||
                type.equals(AlertType.HEART_RATE_CRITICAL_HIGH.name, ignoreCase = true))
    }
}
