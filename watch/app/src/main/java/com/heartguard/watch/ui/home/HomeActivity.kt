package com.heartguard.watch.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.heartguard.watch.ui.theme.HeartGuardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_SKIN_TEMPERATURE",
        "android.permission.health.READ_OXYGEN_SATURATION",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
        "com.samsung.permission.SSENSOR"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            android.util.Log.i("HomeActivity", "All permissions granted")
        } else {
            android.util.Log.w("HomeActivity", "Some permissions denied: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestRequiredPermissions()

        setContent {
            HeartGuardTheme {
                HomeScreen()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}
