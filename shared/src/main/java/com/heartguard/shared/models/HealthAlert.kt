package com.heartguard.shared.models

data class HealthAlert(
    val id: String = "",
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int? = null,
    val temperature: Float? = null,
    val location: String? = null,
    val isAcknowledged: Boolean = false
)

enum class AlertType {
    FALL_DETECTED,
    HEART_RATE_HIGH,
    HEART_RATE_LOW,
    HEART_RATE_CRITICAL_HIGH,
    HEART_RATE_CRITICAL_LOW,
    TEMPERATURE_FEVER,
    TEMPERATURE_HIGH_FEVER,
    TEMPERATURE_HEAT_STRESS,
    SOS_MANUAL
}

enum class AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
