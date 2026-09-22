package com.heartguard.watch.data.sensor

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FallDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accelerometerSensorManager: AccelerometerSensorManager
) {
    companion object {
        private const val TAG = "FallDetectionEngine"
    }

    var onFallDetected: (() -> Unit)? = null

    private var isMonitoring = false

    fun startMonitoring() {
        if (isMonitoring) return
        accelerometerSensorManager.onFallDetected = {
            Log.w(TAG, "Fall detected by accelerometer")
            onFallDetected?.invoke()
        }

        accelerometerSensorManager.startTracking()
        isMonitoring = accelerometerSensorManager.isTrackingActive()
        if (isMonitoring) {
            Log.i(TAG, "Fall detection monitoring started")
        } else {
            accelerometerSensorManager.onFallDetected = null
            Log.w(TAG, "Fall detection unavailable: accelerometer did not start")
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        accelerometerSensorManager.stopTracking()
        accelerometerSensorManager.onFallDetected = null
        Log.i(TAG, "Fall detection monitoring stopped")
    }

    fun isMonitoringActive(): Boolean = isMonitoring
}
