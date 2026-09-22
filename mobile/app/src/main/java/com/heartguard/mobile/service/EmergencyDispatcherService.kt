package com.heartguard.mobile.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.EmergencyContactEntity
import com.heartguard.mobile.data.repository.AlertRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyDispatcherService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertRepository: AlertRepository
) {
    companion object {
        private const val TAG = "EmergencyDispatcher"
    }

    suspend fun dispatchEmergencyAlert(alert: AlertEntity) {
        val contacts = alertRepository.getAllContacts().first()

        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured - cannot send SMS/call for ${alert.type}")
            return
        }

        // عند 70 (HEART_RATE_LOW) و 65 (HEART_RATE_CRITICAL_LOW) يجب الاتصال + SMS + صفارة
        val shouldCall = alert.severity == "CRITICAL" ||
                alert.severity == "HIGH" ||
                alert.type.contains("HEART_RATE_LOW") ||
                alert.type.contains("HEART_RATE_CRITICAL_LOW") ||
                alert.type == "FALL_DETECTED" ||
                alert.type == "SOS_MANUAL"

        Log.w(TAG, "Dispatching alert ${alert.type} severity=${alert.severity} shouldCall=$shouldCall to ${contacts.size} contacts")

        contacts.forEach { contact ->
            sendSMS(contact.phoneNumber, "${alert.message} - الوقت: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp))}")

            if (shouldCall) {
                makeEmergencyCall(contact.phoneNumber)
                // تأخير بسيط بين المكالمات لو فيه أكثر من جهة اتصال لتجنب التداخل
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.i(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber", e)
        }
    }

    private fun makeEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Emergency call initiated to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make emergency call to $phoneNumber", e)
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open dialer", e2)
            }
        }
    }
}
