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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
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
        sendAlert(
            AlertPayload(
                type = AlertType.FALL_DETECTED,
                severity = "CRITICAL",
                message = "تم كشف سقوط!",
                priority = AlertConstants.PRIORITY_CRITICAL
            )
        )
    }

    suspend fun sendHeartRateAlert(type: String, hr: Int) {
        Log.w(TAG, "Heart rate anomaly: $type ($hr bpm)")
        val (message, priority) = when (type) {
            "CRITICAL_HIGH" -> "نبض مرتفع جداً: $hr" to AlertConstants.PRIORITY_CRITICAL
            "CRITICAL_LOW" -> "نبض منخفض جداً: $hr" to AlertConstants.PRIORITY_CRITICAL
            "HIGH" -> "نبض مرتفع: $hr" to AlertConstants.PRIORITY_HIGH
            "LOW" -> "نبض منخفض: $hr" to AlertConstants.PRIORITY_HIGH
            else -> "شذوذ في النبض: $hr" to AlertConstants.PRIORITY_MEDIUM
        }
        sendAlert(
            AlertPayload(
                type = AlertType.HEART_RATE_HIGH,
                severity = if (priority >= 3) "CRITICAL" else "HIGH",
                message = message,
                priority = priority,
                heartRate = hr
            )
        )
    }

    suspend fun sendTemperatureAlert(type: String, temp: Float) {
        Log.w(TAG, "Temperature anomaly: $type ($temp°C)")
        val (message, priority) = when (type) {
            "HIGH_FEVER" -> "حمى عالية: $temp°م" to AlertConstants.PRIORITY_CRITICAL
            "FEVER" -> "حمى: $temp°م" to AlertConstants.PRIORITY_HIGH
            "HEAT_STRESS" -> "إجهاد حراري: $temp°م" to AlertConstants.PRIORITY_MEDIUM
            else -> "شذوذ في الحرارة: $temp°م" to AlertConstants.PRIORITY_LOW
        }
        sendAlert(
            AlertPayload(
                type = AlertType.TEMPERATURE_FEVER,
                severity = if (priority >= 3) "CRITICAL" else "HIGH",
                message = message,
                priority = priority,
                temperature = temp
            )
        )
    }

    suspend fun sendSOSAlert() {
        Log.w(TAG, "SOS manually triggered")
        sendAlert(
            AlertPayload(
                type = AlertType.SOS_MANUAL,
                severity = "CRITICAL",
                message = "تم ضغط زر الطوارئ!",
                priority = AlertConstants.PRIORITY_CRITICAL
            )
        )
    }

    private suspend fun sendAlert(alert: AlertPayload) {
        vibrateEmergency()
        try {
            dataClient.putDataItem(alert.toDataRequest()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Still attempt MessageClient if storing the DataItem fails.
            Log.e(TAG, "Failed to send alert ${alert.id} via DataClient", e)
        }
        sendViaMessageClient(alert.toMessageJson())
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
                messageClient.sendMessage(node.id, path, jsonString.toByteArray(Charsets.UTF_8)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send via MessageClient", e)
        }
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
