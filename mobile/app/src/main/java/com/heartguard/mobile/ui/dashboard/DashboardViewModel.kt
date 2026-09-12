package com.heartguard.mobile.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.EmergencyContactEntity
import com.heartguard.mobile.data.repository.AlertRepository
import com.heartguard.mobile.service.WearableDataSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isWatchConnected: Boolean = false,
    val heartRate: Int = 0,
    val temperature: Float = 0f,
    val recentAlerts: List<AlertEntity> = emptyList(),
    val contacts: List<EmergencyContactEntity> = emptyList(),
    val lastSyncTime: String = "غير متاح"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val alertRepository: AlertRepository,
    private val healthDataHolder: HealthDataHolder,
    private val wearableDataSyncService: WearableDataSyncService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
        wearableDataSyncService.startPeriodicSync()
    }

    private fun observeData() {
        viewModelScope.launch {
            alertRepository.getAllAlerts().collect { alerts ->
                _uiState.update { it.copy(recentAlerts = alerts) }
            }
        }

        viewModelScope.launch {
            alertRepository.getAllContacts().collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }

        viewModelScope.launch {
            healthDataHolder.healthData.collect { data ->
                _uiState.update {
                    it.copy(
                        heartRate = data.heartRate,
                        temperature = data.temperature,
                        lastSyncTime = if (data.timestamp > 0) {
                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(data.timestamp))
                        } else "غير متاح"
                    )
                }
            }
        }

        viewModelScope.launch {
            healthDataHolder.isWatchConnected.collect { connected ->
                _uiState.update { it.copy(isWatchConnected = connected) }
            }
        }
    }

    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            val contact = EmergencyContactEntity(
                name = name,
                phoneNumber = phone,
                relationship = relationship
            )
            alertRepository.insertContact(contact)
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            alertRepository.deleteContact(contact)
        }
    }

    fun markAlertAsRead(alertId: String) {
        viewModelScope.launch {
            alertRepository.markAlertAsRead(alertId)
        }
    }

    fun markAlertAsHandled(alertId: String) {
        viewModelScope.launch {
            alertRepository.markAlertAsHandled(alertId)
        }
    }
}
