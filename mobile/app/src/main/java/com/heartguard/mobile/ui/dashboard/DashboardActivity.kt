package com.heartguard.mobile.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.heartguard.mobile.service.EmergencyAlarmService
import com.heartguard.mobile.ui.contacts.ContactsScreen
import com.heartguard.mobile.ui.theme.HeartGuardMobileTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardActivity : ComponentActivity() {

    /** إشعارات الإنذار تحتاج إذناً صريحاً من Android 13 فما فوق */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            android.util.Log.i(
                "DashboardActivity",
                "POST_NOTIFICATIONS granted=$granted"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        // قناة إشعار الإنذار تُنشأ مبكراً حتى يكون صوتها جاهزاً عند أول نداء طوارئ
        EmergencyAlarmService.ensureAlarmChannel(this)

        setContent {
            HeartGuardMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showContacts by remember { mutableStateOf(false) }

                    if (showContacts) {
                        ContactsScreen(onBack = { showContacts = false })
                    } else {
                        DashboardScreen(
                            onNavigateToContacts = { showContacts = true }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
