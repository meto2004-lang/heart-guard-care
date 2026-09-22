package com.heartguard.watch.service

import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.heartguard.shared.constants.AlertConstants
import com.heartguard.shared.models.AlertType
import org.json.JSONObject
import java.util.UUID

/** One event, encoded for both transports without regenerating its identity. */
internal data class AlertPayload(
    val type: AlertType,
    val severity: String,
    val message: String,
    val priority: Int,
    val heartRate: Int? = null,
    val temperature: Float? = null,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDataRequest(): PutDataRequest {
        val request = PutDataMapRequest.create(AlertConstants.ALERT_PATH).apply {
            dataMap.putString(AlertConstants.EXTRA_ALERT_ID, id)
            dataMap.putString(AlertConstants.EXTRA_ALERT_TYPE, type.name)
            dataMap.putString(AlertConstants.EXTRA_SEVERITY, severity)
            dataMap.putString(AlertConstants.EXTRA_MESSAGE, message)
            // Keep the existing DataClient wire type for older mobile versions.
            dataMap.putString(AlertConstants.EXTRA_TIMESTAMP, timestamp.toString())
            dataMap.putInt(AlertConstants.EXTRA_PRIORITY, priority)
            heartRate?.let { dataMap.putInt(AlertConstants.EXTRA_HEART_RATE, it) }
            temperature?.let { dataMap.putFloat(AlertConstants.EXTRA_TEMPERATURE, it) }
        }
        return request.asPutDataRequest().setUrgent()
    }

    fun toMessageJson(): String = JSONObject().apply {
        put("type", "alert")
        put(AlertConstants.EXTRA_ALERT_ID, id)
        put(AlertConstants.EXTRA_ALERT_TYPE, type.name)
        put(AlertConstants.EXTRA_SEVERITY, severity)
        put(AlertConstants.EXTRA_MESSAGE, message)
        put(AlertConstants.EXTRA_TIMESTAMP, timestamp)
        put(AlertConstants.EXTRA_PRIORITY, priority)
        heartRate?.let { put(AlertConstants.EXTRA_HEART_RATE, it) }
        temperature?.let { put(AlertConstants.EXTRA_TEMPERATURE, it.toDouble()) }
    }.toString()
}
