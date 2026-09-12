package com.heartguard.mobile.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.heartguard.mobile.R
import com.heartguard.mobile.service.EmergencyAlarmService
import com.heartguard.mobile.ui.theme.HeartGuardMobileTheme
import com.heartguard.shared.models.AlertType

/**
 * شاشة الإنذار الكاملة: تُفتح تلقائياً (Full-Screen Intent) عند وصول نداء الطوارئ SOS
 * حتى لو كانت شاشة الجوال مقفلة أو مطفأة، وتعرض زر إيقاف الإنذار بشكل واضح.
 */
class EmergencyAlarmActivity : ComponentActivity() {

    private var alertType by mutableStateOf(AlertType.SOS_MANUAL.name)
    private var alertMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureForEmergencyDisplay()
        readIntent(intent)
        ensureAlarmServiceRunning()

        setContent {
            HeartGuardMobileTheme {
                EmergencyAlarmScreen(
                    message = alertMessage,
                    onDismissAlarm = { dismissAlarm() },
                    onFinishScreen = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        ensureAlarmServiceRunning()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // رجوع = إيقاف الإنذار حتى لا يبقى الصوت يعمل بلا طريقة واضحة لإسكاته
        dismissAlarm()
        finish()
    }

    private fun readIntent(intent: Intent?) {
        alertType = intent?.getStringExtra(EmergencyAlarmService.EXTRA_ALERT_TYPE)
            ?: AlertType.SOS_MANUAL.name
        alertMessage = intent?.getStringExtra(EmergencyAlarmService.EXTRA_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.sos_alarm_default_message)
    }

    /**
     * إذا لم تعمل الخدمة (مثلاً منع النظام تشغيلها من الخلفية) نطلقها من هنا،
     * لأن هذه الشاشة في المقدمة ولا تخضع لقيود بدء الخدمات.
     */
    private fun ensureAlarmServiceRunning() {
        if (EmergencyAlarmService.isAlarming) return
        try {
            val serviceIntent = Intent(this, EmergencyAlarmService::class.java).apply {
                action = EmergencyAlarmService.ACTION_START_ALARM
                putExtra(EmergencyAlarmService.EXTRA_ALERT_TYPE, alertType)
                putExtra(EmergencyAlarmService.EXTRA_MESSAGE, alertMessage)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e("EmergencyAlarmActivity", "Failed to start alarm service", e)
        }
    }

    private fun dismissAlarm() {
        EmergencyAlarmService.stop(this)
    }

    /** إضاءة الشاشة وعرض الإنذار فوق شاشة القفل. */
    private fun configureForEmergencyDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
