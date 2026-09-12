package com.heartguard.shared.models

data class SensorData(
    val heartRate: Int = 0,
    val heartRateStatus: Int = 0,
    val skinTemperature: Float = 0f,
    val ambientTemperature: Float = 0f,
    val temperatureStatus: Int = 0,
    val accelerometerX: Float = 0f,
    val accelerometerY: Float = 0f,
    val accelerometerZ: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
