package com.heartguard.watch.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.heartguard.shared.constants.SensorConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccelerometerSensorManager @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {
    companion object {
        private const val TAG = "AccelerometerSensorManager"
        const val FALL_THRESHOLD = SensorConstants.ACCELEROMETER_FALL_THRESHOLD
    }

    var onFallDetected: (() -> Unit)? = null
    var onAccelerationUpdate: ((Float, Float, Float) -> Unit)? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val impactDetector = FallImpactDetector()
    private var isTracking = false

    fun startTracking() {
        if (isTracking) return
        val sensor = accelerometer
        if (sensor == null) {
            Log.w(TAG, "Accelerometer not available; fall detection cannot start")
            return
        }
        impactDetector.reset()
        try {
            // ~50 Hz is fast enough to observe short impacts, unlike a UI-rate
            // listener. No batching: emergency events should be handled promptly.
            isTracking = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (isTracking) {
                Log.i(TAG, "Accelerometer tracking started")
            } else {
                Log.w(TAG, "Accelerometer listener registration failed")
            }
        } catch (e: SecurityException) {
            isTracking = false
            Log.e(TAG, "Accelerometer access denied", e)
        }
    }

    fun stopTracking() {
        isTracking = false
        sensorManager.unregisterListener(this)
        impactDetector.reset()
        Log.i(TAG, "Accelerometer tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        val elapsedMs = if (event.timestamp > 0L) {
            event.timestamp / 1_000_000L
        } else {
            SystemClock.elapsedRealtime()
        }
        processAccelerationData(event.values[0], event.values[1], event.values[2], elapsedMs)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accelerometer accuracy changed: $accuracy")
    }

    fun processAccelerationData(x: Float, y: Float, z: Float) {
        // Ignore callbacks already queued when tracking was stopped.
        if (!isTracking || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        onAccelerationUpdate?.invoke(x, y, z)
        if (impactDetector.process(x, y, z, SystemClock.elapsedRealtime())) {
            Log.w(TAG, "Possible fall detected by accelerometer impact")
            onFallDetected?.invoke()
        }
    }

    fun isTrackingActive(): Boolean = isTracking
}
