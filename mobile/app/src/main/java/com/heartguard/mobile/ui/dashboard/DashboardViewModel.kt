package com.heartguard.mobile.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.mobile.R
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.EmergencyContactEntity
import com.heartguard.mobile.data.repository.AlertRepository
import com.heartguard.mobile.service.EmergencyAlarmService
import com.heartguard.mobile.service.EmergencyDispatcherService
import com.heartguard.mobile.service.SosAlarmState
import com.heartguard.mobile.service.WearableDataSyncService
import com.heartguard.shared.models.AlertSeverity
import com.heartguard.shared.models.AlertType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardUiState(
    val isWatchConnected: Boolean = false,
    val heartRate: Int = 0,
    val temperature: Float = 0f,
    val recentAlerts: List<AlertEntity> = emptyList(),
    val contacts: List<EmergencyContactEntity> = emptyList(),
    val lastSyncTime: String = "غير متاح",
    val isAlarmActive: Boolean = false,
    val alarmMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val alertRepository: AlertRepository,
    private val healthDataHolder: HealthDataHolder,
    private val wearableDataSyncService: WearableDataSyncService,
    private val emergencyDispatcher: EmergencyDispatcherService
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

        viewModelScope.launch {
            EmergencyAlarmService.alarmState.collect { state ->
                _uiState.update {
                    it.copy(
                        isAlarmActive = state is SosAlarmState.Active,
                        alarmMessage = (state as? SosAlarmState.Active)?.message
                    )
                }
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

    /**
     * زر SOS في الجوال: يسجّل التنبيه، يشغّل صفارة الإنذار العالية،
     * ثم يرسل الرسائل/المكالمات لجهات الاتصال الطارئة.
     */
    fun triggerSos() {
        viewModelScope.launch {
            val message = getApplication<Application>().getString(R.string.sos_message_from_phone)
            val alert = AlertEntity(
                id = UUID.randomUUID().toString(),
                type = AlertType.SOS_MANUAL.name,
                severity = AlertSeverity.CRITICAL.name,
                message = message
            )
            alertRepository.insertAlert(alert)

            // الإنذار الصوتي أولاً حتى لا تسبقه مكالمة الطوارئ
            EmergencyAlarmService.trigger(getApplication<Application>(), alert.type, alert.message)

            emergencyDispatcher.dispatchEmergencyAlert(alert)
        }
    }

    /** تشغيل صفارة الإنذار فقط (للتأكد من ارتفاع الصوت) بدون إرسال رسائل أو مكالمات. */
    fun testAlarmSound() {
        val message = getApplication<Application>().getString(R.string.sos_alarm_test_message)
        EmergencyAlarmService.trigger(getApplication<Application>(), AlertType.SOS_MANUAL.name, message)
    }

    /**
     * اختبار إنذار انخفاض النبض عند 70 - يشغل صفارة + يتصل بجهات الاتصال
     * حسب طلب المستخدم: عند 70 اعطاء انذار بالصوت وقيامه بالاتصال
     */
    fun testHeartRate70() {
        viewModelScope.launch {
            val alert = AlertEntity(
                id = UUID.randomUUID().toString(),
                type = AlertType.HEART_RATE_LOW.name,
                severity = AlertSeverity.HIGH.name,
                message = "نبض منخفض: 70 نبضة/دقيقة - اختبار",
                heartRate = 70,
                timestamp = System.currentTimeMillis()
            )
            alertRepository.insertAlert(alert)
            EmergencyAlarmService.trigger(getApplication<Application>(), alert.type, alert.message)
            emergencyDispatcher.dispatchEmergencyAlert(alert)
        }
    }

    fun testHeartRate65() {
        viewModelScope.launch {
            val alert = AlertEntity(
                id = UUID.randomUUID().toString(),
                type = AlertType.HEART_RATE_CRITICAL_LOW.name,
                severity = AlertSeverity.CRITICAL.name,
                message = "نبض منخفض جداً: 65 نبضة/دقيقة - اختبار حرج",
                heartRate = 65,
                timestamp = System.currentTimeMillis()
            )
            alertRepository.insertAlert(alert)
            EmergencyAlarmService.trigger(getApplication<Application>(), alert.type, alert.message)
            emergencyDispatcher.dispatchEmergencyAlert(alert)
        }
    }

    fun stopAlarm() {
        EmergencyAlarmService.stop(getApplication<Application>())
    }
}
