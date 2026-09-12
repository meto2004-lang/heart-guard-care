package com.heartguard.watch.data.sensor

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccelerometerSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AccelerometerSensorManager"
        const val FALL_THRESHOLD = 25f
    }

    var onFallDetected: (() -> Unit)? = null
    var onAccelerationUpdate: ((Float, Float, Float) -> Unit)? = null

    private var isTracking = false
    private var lastAcceleration = Triple(0f, 0f, 0f)

    fun startTracking() {
        if (isTracking) return
        isTracking = true
        Log.i(TAG, "Accelerometer tracking started")
    }

    fun stopTracking() {
        isTracking = false
        Log.i(TAG, "Accelerometer tracking stopped")
    }

    fun processAccelerationData(x: Float, y: Float, z: Float) {
        onAccelerationUpdate?.invoke(x, y, z)
        checkForFall(x, y, z)
        lastAcceleration = Triple(x, y, z)
    }

    private fun checkForFall(x: Float, y: Float, z: Float) {
        val totalAcceleration = Math.sqrt(
            (x * x + y * y + z * z).toDouble()
        ).toFloat()

        if (totalAcceleration > FALL_THRESHOLD) {
            Log.w(TAG, "Fall detected! Acceleration: $totalAcceleration")
            onFallDetected?.invoke()
        }
    }

    fun isTrackingActive(): Boolean = isTracking
}
