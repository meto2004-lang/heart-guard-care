package com.heartguard.shared.constants

object SensorConstants {
    const val HR_HIGH_THRESHOLD = 120
    const val HR_LOW_THRESHOLD = 70
    const val HR_CRITICAL_HIGH = 150
    const val HR_CRITICAL_LOW = 65

    const val TEMP_FEVER_THRESHOLD = 37.5f
    const val TEMP_HIGH_FEVER = 38.5f
    const val TEMP_HEAT_STRESS = 36.5f

    const val ACCELEROMETER_FALL_THRESHOLD = 35f

    const val SENSOR_UPDATE_INTERVAL_MS = 1000L
    const val DATA_SYNC_INTERVAL_MS = 5000L
}
