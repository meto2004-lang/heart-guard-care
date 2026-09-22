package com.heartguard.watch.service

import android.app.Application
import com.google.android.gms.wearable.DataMap
import com.heartguard.shared.constants.AlertConstants
import com.heartguard.shared.models.AlertType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AlertPayloadTest {
    @Test
    fun bothTransportsCarryTheSameEventForEveryAlertType() {
        for (type in AlertType.entries) {
            val payload = AlertPayload(
                type = type,
                severity = "CRITICAL",
                message = "تنبيه اختبار",
                priority = AlertConstants.PRIORITY_CRITICAL,
                heartRate = 155,
                temperature = 36.5f,
                timestamp = 1_700_000_000_000L
            )
            val request = payload.toDataRequest()
            val data = DataMap.fromByteArray(requireNotNull(request.data))
            val message = JSONObject(payload.toMessageJson())

            assertEquals(AlertConstants.ALERT_PATH, request.uri.path)
            assertEquals(payload.id, data.getString(AlertConstants.EXTRA_ALERT_ID))
            assertEquals(payload.id, message.getString(AlertConstants.EXTRA_ALERT_ID))
            assertEquals(payload.timestamp.toString(), data.getString(AlertConstants.EXTRA_TIMESTAMP))
            assertEquals(payload.timestamp, message.getLong(AlertConstants.EXTRA_TIMESTAMP))
            assertEquals(type.name, data.getString(AlertConstants.EXTRA_ALERT_TYPE))
            assertEquals(type.name, message.getString(AlertConstants.EXTRA_ALERT_TYPE))
            assertEquals(data.getString(AlertConstants.EXTRA_SEVERITY), message.getString(AlertConstants.EXTRA_SEVERITY))
            assertEquals(data.getString(AlertConstants.EXTRA_MESSAGE), message.getString(AlertConstants.EXTRA_MESSAGE))
            assertEquals(data.getInt(AlertConstants.EXTRA_HEART_RATE), message.getInt(AlertConstants.EXTRA_HEART_RATE))
            assertEquals(data.getFloat(AlertConstants.EXTRA_TEMPERATURE).toDouble(), message.getDouble(AlertConstants.EXTRA_TEMPERATURE), 0.001)
        }
    }

    @Test
    fun separateIdenticalEventsHaveDifferentIdsEvenAtTheSameTimestamp() {
        val first = sos()
        val second = sos()
        assertNotEquals(first.id, second.id)
        assertEquals(first.toMessageJson(), first.toMessageJson())
    }

    @Test
    fun sosDoesNotInventSensorReadings() {
        val payload = sos()
        val data = DataMap.fromByteArray(requireNotNull(payload.toDataRequest().data))
        val message = JSONObject(payload.toMessageJson())
        assertFalse(data.containsKey(AlertConstants.EXTRA_HEART_RATE))
        assertFalse(data.containsKey(AlertConstants.EXTRA_TEMPERATURE))
        assertFalse(message.has(AlertConstants.EXTRA_HEART_RATE))
        assertFalse(message.has(AlertConstants.EXTRA_TEMPERATURE))
    }

    private fun sos() = AlertPayload(
        type = AlertType.SOS_MANUAL,
        severity = "CRITICAL",
        message = "SOS",
        priority = AlertConstants.PRIORITY_CRITICAL,
        timestamp = 1_700_000_000_000L
    )
}
