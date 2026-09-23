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
import com.heartguard.shared.ai.AuthenticityResult
import com.heartguard.shared.ai.AlertVerdict
import com.heartguard.shared.constants.AlertConstants
import com.heartguard.shared.constants.SensorConstants
import com.heartguard.shared.models.AlertType
import com.heartguard.watch.R
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
                message = "تم كشف سقوط (سقوط حر + اصطدام + سكون).",
                priority = AlertConstants.PRIORITY_CRITICAL
            )
        )
    }

    suspend fun sendHeartRateAlert(
        type: String,
        hr: Int,
        authenticity: AuthenticityResult? = null
    ) {
        Log.w(TAG, "Heart rate anomaly: $type ($hr bpm) verdict=${authenticity?.verdict}")
        val likelyFalse = authenticity?.verdict == AlertVerdict.LIKELY_FALSE_ALARM
        val (alertType, baseMessage, priority) = when (type) {
            "CRITICAL_HIGH" -> Triple(
                AlertType.HEART_RATE_CRITICAL_HIGH,
                "نبض مرتفع جداً: $hr",
                AlertConstants.PRIORITY_CRITICAL
            )
            "CRITICAL_LOW" -> Triple(
                AlertType.HEART_RATE_CRITICAL_LOW,
                context.getString(R.string.hr_critical_low_alert, hr),
                AlertConstants.PRIORITY_CRITICAL
            )
            "HIGH" -> Triple(
                AlertType.HEART_RATE_HIGH,
                "نبض مرتفع: $hr",
                AlertConstants.PRIORITY_HIGH
            )
            "LOW" -> Triple(
                AlertType.HEART_RATE_LOW,
                context.getString(R.string.hr_low_alert, hr, SensorConstants.HR_LOW_THRESHOLD),
                if (likelyFalse) AlertConstants.PRIORITY_MEDIUM else AlertConstants.PRIORITY_CRITICAL
            )
            else -> Triple(
                AlertType.HEART_RATE_HIGH,
                "شذوذ في النبض: $hr",
                AlertConstants.PRIORITY_MEDIUM
            )
        }
        val message = if (authenticity != null) {
            "$baseMessage — ${authenticity.reasonAr}"
        } else {
            baseMessage
        }
        val severity = when {
            likelyFalse -> "MEDIUM"
            priority >= AlertConstants.PRIORITY_HIGH -> "CRITICAL"
            else -> "HIGH"
        }
        sendAlert(
            AlertPayload(
                type = alertType,
                severity = severity,
                message = message,
                priority = priority,
                heartRate = hr,
                authenticity = if (likelyFalse) {
                    AlertConstants.AUTHENTICITY_LIKELY_FALSE
                } else {
                    AlertConstants.AUTHENTICITY_EMERGENCY
                },
                explanation = authenticity?.reasonAr
            ),
            vibrate = !likelyFalse
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

    private suspend fun sendAlert(alert: AlertPayload, vibrate: Boolean = true) {
        if (vibrate) vibrateEmergency()
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

    suspend fun sendHealthDataUpdate(hr: Int?, temp: Float?, motion: Float? = null) {
        try {
            Log.d(TAG, "Sending health data update: HR=$hr, Temp=$temp, Motion=$motion")

            val dataMap = PutDataMapRequest.create(AlertConstants.HEALTH_DATA_PATH).apply {
                hr?.let { dataMap.putInt(AlertConstants.EXTRA_HEART_RATE, it) }
                temp?.let { dataMap.putFloat(AlertConstants.EXTRA_TEMPERATURE, it) }
                motion?.let { dataMap.putFloat(AlertConstants.EXTRA_MOTION, it) }
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val request = dataMap.asPutDataRequest()
            dataClient.putDataItem(request).await()
            Log.d(TAG, "Health data sent via DataClient")

            val json = JSONObject().apply {
                hr?.let { put(AlertConstants.EXTRA_HEART_RATE, it) }
                temp?.let { put(AlertConstants.EXTRA_TEMPERATURE, it.toDouble()) }
                motion?.let { put(AlertConstants.EXTRA_MOTION, it.toDouble()) }
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
