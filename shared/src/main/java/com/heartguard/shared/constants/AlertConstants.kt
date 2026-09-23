package com.heartguard.shared.constants

object AlertConstants {
    const val ALERT_PATH = "/emergency_alert"
    const val HEALTH_DATA_PATH = "/health_data"
    const val SETTINGS_PATH = "/settings_sync"

    const val MSG_HEALTH_UPDATE = "/msg/health_update"
    const val MSG_ALERT = "/msg/alert"
    const val MSG_REQUEST_DATA = "/msg/request_data"

    const val EXTRA_ALERT_ID = "id"
    const val EXTRA_ALERT_TYPE = "alert_type"
    const val EXTRA_SEVERITY = "severity"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_HEART_RATE = "heart_rate"
    const val EXTRA_TEMPERATURE = "temperature"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_MOTION = "motion"
    const val EXTRA_AUTHENTICITY = "authenticity"
    const val EXTRA_EXPLANATION = "explanation"

    const val AUTHENTICITY_EMERGENCY = "EMERGENCY"
    const val AUTHENTICITY_LIKELY_FALSE = "LIKELY_FALSE_ALARM"

    const val PRIORITY_LOW = 1
    const val PRIORITY_MEDIUM = 2
    const val PRIORITY_HIGH = 3
    const val PRIORITY_CRITICAL = 4
}
