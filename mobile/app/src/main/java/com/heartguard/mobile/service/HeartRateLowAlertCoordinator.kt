package com.heartguard.mobile.service

import android.content.Context
import android.util.Log
import com.heartguard.mobile.R
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.repository.AlertRepository
import com.heartguard.shared.constants.SensorConstants
import com.heartguard.shared.models.AlertSeverity
import com.heartguard.shared.models.AlertType
import com.heartguard.shared.utils.HeartRateLowEpisodeGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-side low-HR alarm: fires once when live bpm drops to
 * [SensorConstants.HR_LOW_THRESHOLD] or below, and shares the episode
 * with watch-originated alerts so SMS/call/siren run only once.
 */
@Singleton
class HeartRateLowAlertCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthDataHolder: HealthDataHolder,
    private val alertRepository: AlertRepository,
    private val emergencyDispatcher: EmergencyDispatcherService
) {
    companion object {
        private const val TAG = "HeartRateLowAlert"
    }

    private val gate = HeartRateLowEpisodeGate()
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            healthDataHolder.healthData.collect { data ->
                if (gate.onHeartRate(data.heartRate)) {
                    raiseLocalAlert(data.heartRate)
                }
            }
        }
        Log.i(TAG, "Low-HR phone alarm armed at ${SensorConstants.HR_LOW_THRESHOLD} bpm")
    }

    /** @return false if a low-HR episode is already being handled */
    fun tryBeginEpisodeFromWatch(): Boolean = gate.tryBeginEpisode()

    private suspend fun raiseLocalAlert(hr: Int) {
        val message = context.getString(
            R.string.hr_low_alert_message,
            hr,
            SensorConstants.HR_LOW_THRESHOLD
        )
        val alert = AlertEntity(
            id = UUID.randomUUID().toString(),
            type = AlertType.HEART_RATE_LOW.name,
            severity = AlertSeverity.CRITICAL.name,
            message = message,
            heartRate = hr
        )
        try {
            if (!alertRepository.insertAlertIfNew(alert)) {
                Log.d(TAG, "Local low-HR alert already stored: ${alert.id}")
                return
            }
            try {
                EmergencyAlarmService.trigger(context, alert.type, alert.message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start loud alarm for low HR", e)
            }
            emergencyDispatcher.dispatchEmergencyAlert(alert)
            Log.w(TAG, "Low-HR phone alarm raised at $hr bpm")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to raise local low-HR alert", e)
        }
    }
}
