package com.heartguard.shared.utils

import com.heartguard.shared.constants.SensorConstants
import com.heartguard.shared.models.AlertSeverity
import com.heartguard.shared.models.AlertType

object HealthUtils {

    fun classifyHeartRate(hr: Int): Pair<AlertType, AlertSeverity> {
        return when {
            hr >= SensorConstants.HR_CRITICAL_HIGH -> AlertType.HEART_RATE_CRITICAL_HIGH to AlertSeverity.CRITICAL
            hr >= SensorConstants.HR_HIGH_THRESHOLD -> AlertType.HEART_RATE_HIGH to AlertSeverity.HIGH
            hr <= SensorConstants.HR_CRITICAL_LOW -> AlertType.HEART_RATE_CRITICAL_LOW to AlertSeverity.CRITICAL
            hr <= SensorConstants.HR_LOW_THRESHOLD -> AlertType.HEART_RATE_LOW to AlertSeverity.HIGH
            else -> AlertType.HEART_RATE_HIGH to AlertSeverity.LOW
        }
    }

    fun classifyTemperature(temp: Float): Pair<AlertType, AlertSeverity> {
        return when {
            temp >= SensorConstants.TEMP_HIGH_FEVER -> AlertType.TEMPERATURE_HIGH_FEVER to AlertSeverity.CRITICAL
            temp >= SensorConstants.TEMP_FEVER_THRESHOLD -> AlertType.TEMPERATURE_FEVER to AlertSeverity.HIGH
            temp >= SensorConstants.TEMP_HEAT_STRESS -> AlertType.TEMPERATURE_HEAT_STRESS to AlertSeverity.MEDIUM
            else -> AlertType.TEMPERATURE_FEVER to AlertSeverity.LOW
        }
    }

    fun getHeartRateStatusText(hr: Int): String {
        return when {
            hr >= SensorConstants.HR_CRITICAL_HIGH -> "عالي جداً"
            hr >= SensorConstants.HR_HIGH_THRESHOLD -> "مرتفع"
            hr <= SensorConstants.HR_CRITICAL_LOW -> "منخفض جداً"
            hr <= SensorConstants.HR_LOW_THRESHOLD -> "منخفض"
            else -> "طبيعي"
        }
    }

    fun getTemperatureStatusText(temp: Float): String {
        return when {
            temp >= SensorConstants.TEMP_HIGH_FEVER -> "حمى عالية"
            temp >= SensorConstants.TEMP_FEVER_THRESHOLD -> "حمى"
            temp >= SensorConstants.TEMP_HEAT_STRESS -> "إجهاد حراري"
            else -> "طبيعي"
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
