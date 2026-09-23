package com.heartguard.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LiveHealthData(
    val heartRate: Int = 0,
    val temperature: Float = 0f,
    val motion: Float = 0f,
    val timestamp: Long = 0L
)

@Singleton
class HealthDataHolder @Inject constructor() {

    private val _healthData = MutableStateFlow(LiveHealthData())
    val healthData: StateFlow<LiveHealthData> = _healthData.asStateFlow()

    private val _isWatchConnected = MutableStateFlow(false)
    val isWatchConnected: StateFlow<Boolean> = _isWatchConnected.asStateFlow()

    fun updateHeartRate(hr: Int) {
        _healthData.value = _healthData.value.copy(
            heartRate = hr,
            timestamp = System.currentTimeMillis()
        )
    }

    fun updateTemperature(temp: Float) {
        _healthData.value = _healthData.value.copy(
            temperature = temp,
            timestamp = System.currentTimeMillis()
        )
    }

    fun updateHealthData(hr: Int?, temp: Float?, motion: Float? = null) {
        _healthData.value = _healthData.value.copy(
            heartRate = hr ?: _healthData.value.heartRate,
            temperature = temp ?: _healthData.value.temperature,
            motion = motion ?: _healthData.value.motion,
            timestamp = System.currentTimeMillis()
        )
    }

    fun setWatchConnected(connected: Boolean) {
        _isWatchConnected.value = connected
    }
}
