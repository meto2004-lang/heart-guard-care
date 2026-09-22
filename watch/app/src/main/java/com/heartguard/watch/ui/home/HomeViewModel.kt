package com.heartguard.watch.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun triggerTestFall() {
        viewModelScope.launch {
            emergencyAlertService.sendFallAlert()
        }
    }

    fun triggerTestHeartRate70() {
        viewModelScope.launch {
            emergencyAlertService.sendHeartRateAlert("LOW", 70)
        }
    }

    fun triggerTestHeartRate65() {
        viewModelScope.launch {
            emergencyAlertService.sendHeartRateAlert("CRITICAL_LOW", 65)
        }
    }

    private fun getHeartRateStatusText(hr: Int): String {
        return when {
            hr >= 150 -> "حرج مرتفع"
            hr >= 120 -> "مرتفع"
            hr <= 65 -> "حرج منخفض"
            hr <= 70 -> "منخفض"
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
