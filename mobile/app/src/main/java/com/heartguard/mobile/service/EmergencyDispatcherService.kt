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
            Log.w(TAG, "No emergency contacts configured")
            return
        }

        contacts.forEach { contact ->
            sendSMS(contact.phoneNumber, alert.message)

            if (alert.severity == "CRITICAL") {
                makeEmergencyCall(contact.phoneNumber)
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
