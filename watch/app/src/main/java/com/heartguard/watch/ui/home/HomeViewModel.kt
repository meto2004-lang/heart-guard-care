package com.heartguard.watch.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.shared.constants.SensorConstants
import com.heartguard.watch.data.local.entities.HealthDataEntity
import com.heartguard.watch.data.repository.HealthRepository
import com.heartguard.watch.service.EmergencyAlertService
import com.heartguard.watch.service.HealthMonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val heartRate: Int = 0,
    val temperature: Float = 0f,
    val isMonitoring: Boolean = false,
    val heartRateStatus: String = "طبيعي",
    val temperatureStatus: String = "طبيعي",
    val fallDetectionEnabled: Boolean = true,
    val lastUpdate: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val healthRepository: HealthRepository,
    private val emergencyAlertService: EmergencyAlertService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeHealthData()
    }

    private fun observeHealthData() {
        viewModelScope.launch {
            healthRepository.getLatestHealthData().collect { data ->
                data?.let {
                    _uiState.update { state ->
                        val newHr = if (it.heartRate > 0) it.heartRate else state.heartRate
                        val newTemp = if (it.skinTemperature > 0f) it.skinTemperature else state.temperature
                        state.copy(
                            heartRate = newHr,
                            temperature = newTemp,
                            heartRateStatus = getHeartRateStatusText(newHr),
                            temperatureStatus = getTemperatureStatusText(newTemp),
                            lastUpdate = it.timestamp
                        )
                    }
                }
            }
        }
    }

    fun startMonitoring() {
        val intent = Intent(getApplication(), HealthMonitoringService::class.java).apply {
            action = HealthMonitoringService.ACTION_START
        }
        getApplication<Application>().startForegroundService(intent)
        _uiState.update { it.copy(isMonitoring = true) }
    }

    fun stopMonitoring() {
        val intent = Intent(getApplication(), HealthMonitoringService::class.java).apply {
            action = HealthMonitoringService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _uiState.update { it.copy(isMonitoring = false) }
    }

    fun triggerSOS() {
        viewModelScope.launch {
            emergencyAlertService.sendSOSAlert()
        }
    }

    private fun getHeartRateStatusText(hr: Int): String {
        return when {
            hr >= SensorConstants.HR_CRITICAL_HIGH -> "حرج مرتفع"
            hr >= SensorConstants.HR_HIGH_THRESHOLD -> "مرتفع"
            hr <= SensorConstants.HR_CRITICAL_LOW -> "حرج منخفض"
            hr <= SensorConstants.HR_LOW_THRESHOLD -> "منخفض"
            hr == 0 -> "غير متاح"
            else -> "طبيعي"
        }
    }

    private fun getTemperatureStatusText(temp: Float): String {
        return when {
            temp >= 39.0f -> "حمى عالية"
            temp >= 38.0f -> "حمى"
            temp >= 37.5f -> "إجهاد حراري"
            temp == 0f -> "غير متاح"
            else -> "طبيعي"
        }
    }
}
