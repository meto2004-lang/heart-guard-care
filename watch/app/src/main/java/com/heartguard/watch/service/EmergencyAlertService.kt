package com.heartguard.watch.service

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.heartguard.shared.constants.AlertConstants
import com.heartguard.shared.models.AlertType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyAlertService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val vibrator: Vibrator by lazy {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    companion object {
        private const val TAG = "EmergencyAlertService"
    }

    suspend fun sendFallAlert() {
        Log.w(TAG, "FALL DETECTED - Sending emergency alert")
        vibrateEmergency()

        val alertData = PutDataMapRequest.create(AlertConstants.ALERT_PATH).apply {
            dataMap.putString(AlertConstants.EXTRA_ALERT_TYPE, AlertType.FALL_DETECTED.name)
            dataMap.putString(AlertConstants.EXTRA_SEVERITY, "CRITICAL")
            dataMap.putString(AlertConstants.EXTRA_MESSAGE, "تم كشف سقوط!")
            dataMap.putString(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis().toString())
            dataMap.putInt(AlertConstants.EXTRA_PRIORITY, AlertConstants.PRIORITY_CRITICAL)
            dataMap.putString("id", UUID.randomUUID().toString())
        }

        val request = alertData.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        sendViaMessageClient(buildAlertJson(AlertType.FALL_DETECTED.name, "CRITICAL", "تم كشف سقوط!"))
        Log.i(TAG, "Fall alert sent to phone")
    }

    suspend fun sendHeartRateAlert(type: String, hr: Int) {
        Log.w(TAG, "Heart rate anomaly: $type ($hr bpm)")
        vibrateEmergency()

        val (message, priority) = when (type) {
            "CRITICAL_HIGH" -> "نبض مرتفع جداً: $hr" to AlertConstants.PRIORITY_CRITICAL
            "CRITICAL_LOW" -> "نبض منخفض جداً: $hr" to AlertConstants.PRIORITY_CRITICAL
            "HIGH" -> "نبض مرتفع: $hr" to AlertConstants.PRIORITY_HIGH
            "LOW" -> "نبض منخفض: $hr" to AlertConstants.PRIORITY_HIGH
            else -> "شذوذ في النبض: $hr" to AlertConstants.PRIORITY_MEDIUM
        }

        val alertData = PutDataMapRequest.create(AlertConstants.ALERT_PATH).apply {
            dataMap.putString(AlertConstants.EXTRA_ALERT_TYPE, AlertType.HEART_RATE_HIGH.name)
            dataMap.putString(AlertConstants.EXTRA_SEVERITY, if (priority >= 3) "CRITICAL" else "HIGH")
            dataMap.putString(AlertConstants.EXTRA_MESSAGE, message)
            dataMap.putString(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis().toString())
            dataMap.putInt(AlertConstants.EXTRA_HEART_RATE, hr)
            dataMap.putInt(AlertConstants.EXTRA_PRIORITY, priority)
            dataMap.putString("id", UUID.randomUUID().toString())
        }

        val request = alertData.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        sendViaMessageClient(buildAlertJson(AlertType.HEART_RATE_HIGH.name, if (priority >= 3) "CRITICAL" else "HIGH", message, hr = hr))
    }

    suspend fun sendTemperatureAlert(type: String, temp: Float) {
        Log.w(TAG, "Temperature anomaly: $type ($temp°C)")
        vibrateEmergency()

        val (message, priority) = when (type) {
            "HIGH_FEVER" -> "حمى عالية: $temp°م" to AlertConstants.PRIORITY_CRITICAL
            "FEVER" -> "حمى: $temp°م" to AlertConstants.PRIORITY_HIGH
            "HEAT_STRESS" -> "إجهاد حراري: $temp°م" to AlertConstants.PRIORITY_MEDIUM
            else -> "شذوذ في الحرارة: $temp°م" to AlertConstants.PRIORITY_LOW
        }

        val alertData = PutDataMapRequest.create(AlertConstants.ALERT_PATH).apply {
            dataMap.putString(AlertConstants.EXTRA_ALERT_TYPE, AlertType.TEMPERATURE_FEVER.name)
            dataMap.putString(AlertConstants.EXTRA_SEVERITY, if (priority >= 3) "CRITICAL" else "HIGH")
            dataMap.putString(AlertConstants.EXTRA_MESSAGE, message)
            dataMap.putString(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis().toString())
            dataMap.putFloat(AlertConstants.EXTRA_TEMPERATURE, temp)
            dataMap.putInt(AlertConstants.EXTRA_PRIORITY, priority)
            dataMap.putString("id", UUID.randomUUID().toString())
        }

        val request = alertData.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        sendViaMessageClient(buildAlertJson(AlertType.TEMPERATURE_FEVER.name, if (priority >= 3) "CRITICAL" else "HIGH", message, temp = temp))
    }

    suspend fun sendSOSAlert() {
        Log.w(TAG, "SOS manually triggered")
        vibrateEmergency()

        val alertData = PutDataMapRequest.create(AlertConstants.ALERT_PATH).apply {
            dataMap.putString(AlertConstants.EXTRA_ALERT_TYPE, AlertType.SOS_MANUAL.name)
            dataMap.putString(AlertConstants.EXTRA_SEVERITY, "CRITICAL")
            dataMap.putString(AlertConstants.EXTRA_MESSAGE, "تم ضغط زر الطوارئ!")
            dataMap.putString(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis().toString())
            dataMap.putInt(AlertConstants.EXTRA_PRIORITY, AlertConstants.PRIORITY_CRITICAL)
            dataMap.putString("id", UUID.randomUUID().toString())
        }

        val request = alertData.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        sendViaMessageClient(buildAlertJson(AlertType.SOS_MANUAL.name, "CRITICAL", "تم ضغط زر الطوارئ!"))
    }

    suspend fun sendHealthDataUpdate(hr: Int?, temp: Float?) {
        try {
            Log.d(TAG, "Sending health data update: HR=$hr, Temp=$temp")

            val dataMap = PutDataMapRequest.create(AlertConstants.HEALTH_DATA_PATH).apply {
                hr?.let { dataMap.putInt(AlertConstants.EXTRA_HEART_RATE, it) }
                temp?.let { dataMap.putFloat(AlertConstants.EXTRA_TEMPERATURE, it) }
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val request = dataMap.asPutDataRequest()
            dataClient.putDataItem(request).await()
            Log.d(TAG, "Health data sent via DataClient")

            val json = JSONObject().apply {
                hr?.let { put(AlertConstants.EXTRA_HEART_RATE, it) }
                temp?.let { put(AlertConstants.EXTRA_TEMPERATURE, it.toDouble()) }
                put("timestamp", System.currentTimeMillis())
            }
            sendViaMessageClient(json.toString(), AlertConstants.MSG_HEALTH_UPDATE)
            Log.d(TAG, "Health data sent via MessageClient")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send health data update", e)
        }
    }

    private suspend fun sendViaMessageClient(jsonString: String, path: String = AlertConstants.MSG_ALERT) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, jsonString.toByteArray()).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send via MessageClient", e)
        }
    }

    private fun buildAlertJson(type: String, severity: String, message: String, hr: Int? = null, temp: Float? = null): String {
        return JSONObject().apply {
            put("type", "alert")
            put(AlertConstants.EXTRA_ALERT_TYPE, type)
            put(AlertConstants.EXTRA_SEVERITY, severity)
            put(AlertConstants.EXTRA_MESSAGE, message)
            put(AlertConstants.EXTRA_TIMESTAMP, System.currentTimeMillis())
            hr?.let { put(AlertConstants.EXTRA_HEART_RATE, it) }
            temp?.let { put(AlertConstants.EXTRA_TEMPERATURE, it.toDouble()) }
        }.toString()
    }

    private fun vibrateEmergency() {
        try {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500),
                intArrayOf(0, 255, 0, 255, 0, 255),
                -1
            )
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
}
