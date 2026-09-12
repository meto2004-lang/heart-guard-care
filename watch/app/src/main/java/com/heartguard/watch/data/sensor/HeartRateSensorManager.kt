package com.heartguard.watch.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRateSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {
    companion object {
        private const val TAG = "HeartRateSensorManager"
        const val HR_HIGH_THRESHOLD = 120
        const val HR_LOW_THRESHOLD = 50
        const val HR_CRITICAL_HIGH = 150
        const val HR_CRITICAL_LOW = 40
    }

    var onHeartRateUpdate: ((Int, Int) -> Unit)? = null
    var onHeartRateAnomaly: ((String, Int) -> Unit)? = null

    private var isTracking = false
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    fun startTracking() {
        if (isTracking) return
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isTracking = true
            Log.i(TAG, "Heart rate tracking started")
        } else {
            Log.w(TAG, "Heart rate sensor not available on this device")
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        sensorManager.unregisterListener(this)
        isTracking = false
        Log.i(TAG, "Heart rate tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_HEART_RATE) {
                val hrValue = it.values[0].toInt()
                val hrStatus = it.accuracy
                if (hrValue > 0) {
                    onHeartRateUpdate?.invoke(hrValue, hrStatus)
                    checkAnomaly(hrValue)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Heart rate sensor accuracy changed: $accuracy")
    }

    private fun checkAnomaly(hr: Int) {
        when {
            hr >= HR_CRITICAL_HIGH -> onHeartRateAnomaly?.invoke("CRITICAL_HIGH", hr)
            hr >= HR_HIGH_THRESHOLD -> onHeartRateAnomaly?.invoke("HIGH", hr)
            hr <= HR_CRITICAL_LOW -> onHeartRateAnomaly?.invoke("CRITICAL_LOW", hr)
            hr <= HR_LOW_THRESHOLD -> onHeartRateAnomaly?.invoke("LOW", hr)
        }
    }

    fun isTrackingActive(): Boolean = isTracking
}
