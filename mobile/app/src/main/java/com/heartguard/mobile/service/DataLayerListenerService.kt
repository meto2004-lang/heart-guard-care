package com.heartguard.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.repository.AlertRepository
import com.heartguard.mobile.ui.dashboard.DashboardActivity
import com.heartguard.shared.constants.AlertConstants
import com.heartguard.shared.models.AlertSeverity
import com.heartguard.shared.models.AlertType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class DataLayerListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "DataLayerListenerMobile"
        private const val CHANNEL_ID = "emergency_alerts"
        private const val NOTIFICATION_CHANNEL_NAME = "تنبيهات الطوارئ"

        /**
         * التنبيهات التي تشغّل صفارة الإنذار العالية على الجوال.
         * SOS دائماً، إضافةً إلى أي تنبيه حرج (سقوط، نبض/حرارة حرجة).
         */
        private val LOUD_ALARM_ALERT_TYPES = setOf(
            AlertType.SOS_MANUAL.name,
            AlertType.FALL_DETECTED.name
        )

        private fun requiresLoudAlarm(alertType: String, severity: String): Boolean =
            alertType in LOUD_ALARM_ALERT_TYPES ||
                severity.equals(AlertSeverity.CRITICAL.name, ignoreCase = true)
    }

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var emergencyDispatcher: EmergencyDispatcherService
    @Inject lateinit var healthDataHolder: HealthDataHolder

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
        dataEvents.forEach { event ->
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                Log.d(TAG, "Data event path: $path")

                when (path) {
                    AlertConstants.ALERT_PATH -> handleEmergencyAlert(event.dataItem)
                    AlertConstants.HEALTH_DATA_PATH -> handleHealthData(event.dataItem)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: path=${messageEvent.path}")
        when (messageEvent.path) {
            AlertConstants.MSG_HEALTH_UPDATE -> handleHealthMessage(messageEvent.data)
            AlertConstants.MSG_ALERT -> handleAlertMessage(messageEvent.data)
        }
    }

    private fun handleHealthMessage(data: ByteArray) {
        try {
            val json = JSONObject(String(data))
            val heartRate = if (json.has(AlertConstants.EXTRA_HEART_RATE)) json.getInt(AlertConstants.EXTRA_HEART_RATE) else null
            val temperature = if (json.has(AlertConstants.EXTRA_TEMPERATURE)) json.getDouble(AlertConstants.EXTRA_TEMPERATURE).toFloat() else null

            Log.d(TAG, "Health message received: HR=$heartRate, Temp=$temperature")
            healthDataHolder.setWatchConnected(true)
            healthDataHolder.updateHealthData(heartRate, temperature)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse health message", e)
        }
    }

    private fun handleAlertMessage(data: ByteArray) {
        try {
            val json = JSONObject(String(data))
            val alertType = json.optString(AlertConstants.EXTRA_ALERT_TYPE) ?: return
            val severity = json.optString(AlertConstants.EXTRA_SEVERITY, "HIGH")
            val message = json.optString(AlertConstants.EXTRA_MESSAGE, "")
            val timestamp = json.optLong(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis())
            val heartRate = if (json.has(AlertConstants.EXTRA_HEART_RATE)) json.getInt(AlertConstants.EXTRA_HEART_RATE) else null
            val temperature = if (json.has(AlertConstants.EXTRA_TEMPERATURE)) json.getDouble(AlertConstants.EXTRA_TEMPERATURE).toFloat() else null
            val alertId = System.currentTimeMillis().toString()

            Log.w(TAG, "Alert message received: $alertType - $message")

            serviceScope.launch {
                val alert = AlertEntity(
                    id = alertId,
                    type = alertType,
                    severity = severity,
                    message = message,
                    heartRate = heartRate,
                    temperature = temperature,
                    timestamp = timestamp
                )
                alertRepository.insertAlert(alert)
                notifyCaregiver(alertType, message, severity)
                emergencyDispatcher.dispatchEmergencyAlert(alert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse alert message", e)
        }
    }

    private fun handleEmergencyAlert(dataItem: com.google.android.gms.wearable.DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

        val alertType = dataMap.getString(AlertConstants.EXTRA_ALERT_TYPE) ?: return
        val severity = dataMap.getString(AlertConstants.EXTRA_SEVERITY) ?: "HIGH"
        val message = dataMap.getString(AlertConstants.EXTRA_MESSAGE) ?: ""
        val timestamp = dataMap.getString(AlertConstants.EXTRA_TIMESTAMP)?.toLongOrNull() ?: System.currentTimeMillis()
        val heartRate = if (dataMap.containsKey(AlertConstants.EXTRA_HEART_RATE)) dataMap.getInt(AlertConstants.EXTRA_HEART_RATE) else null
        val temperature = if (dataMap.containsKey(AlertConstants.EXTRA_TEMPERATURE)) dataMap.getFloat(AlertConstants.EXTRA_TEMPERATURE) else null
        val alertId = dataMap.getString("id") ?: System.currentTimeMillis().toString()

        Log.w(TAG, "Emergency alert received: $alertType - $message")

        serviceScope.launch {
            val alert = AlertEntity(
                id = alertId,
                type = alertType,
                severity = severity,
                message = message,
                heartRate = heartRate,
                temperature = temperature,
                timestamp = timestamp
            )
            alertRepository.insertAlert(alert)

            notifyCaregiver(alertType, message, severity)

            emergencyDispatcher.dispatchEmergencyAlert(alert)
        }
    }

    private fun handleHealthData(dataItem: com.google.android.gms.wearable.DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

        val heartRate = if (dataMap.containsKey(AlertConstants.EXTRA_HEART_RATE)) {
            dataMap.getInt(AlertConstants.EXTRA_HEART_RATE)
        } else null

        val temperature = if (dataMap.containsKey(AlertConstants.EXTRA_TEMPERATURE)) {
            dataMap.getFloat(AlertConstants.EXTRA_TEMPERATURE)
        } else null

        Log.d(TAG, "Health data received: HR=$heartRate, Temp=$temperature")

        healthDataHolder.setWatchConnected(true)
        healthDataHolder.updateHealthData(heartRate, temperature)
    }

    /**
     * التنبيهات الحرجة (خصوصاً SOS) تشغّل صفارة إنذار عالية ومستمرة على الجوال
     * مع شاشة إنذار كاملة، وباقي التنبيهات تظهر كإشعار عادي.
     */
    private fun notifyCaregiver(alertType: String, message: String, severity: String) {
        if (requiresLoudAlarm(alertType, severity)) {
            Log.w(TAG, "Loud SOS alarm requested for $alertType ($severity)")
            EmergencyAlarmService.trigger(this, alertType, message)
        } else {
            showNotification(alertType, message, severity)
        }
    }

    private fun showNotification(alertType: String, message: String, severity: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات طوارئ من ساعة الحارس"
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (severity) {
            "CRITICAL" -> "تنبيه طوارئ حرج!"
            "HIGH" -> "تنبيه طوارئ"
            else -> "تنبيه"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
