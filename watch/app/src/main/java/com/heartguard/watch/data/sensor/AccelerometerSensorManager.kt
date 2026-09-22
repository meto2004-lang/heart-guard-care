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
import kotlin.math.sqrt

/**
 * مدير حساس التسارع - يكشف السقوط عند تجاوز عتبة معينة.
 *
 * تم إصلاحه ليعمل فعلياً مع SensorManager + تم رفع العتبة لتجربة السقوط المقصود.
 * القيمة الأصلية كانت 25f (~2.5g) والآن 35f (~3.5g) حتى لا يعطي إنذارات كاذبة
 * أثناء الحركة العادية، ويحتاج سقوط أقوى ليتم تفعيله.
 *
 * لتجربة سقوط مقصود:
 *  - ارتد الساعة، اقفز أو اسقط على وسادة / مرتبة
 *  - التسارع يجب أن يتجاوز 35 m/s²
 *  - لو تريد حساسية أعلى للتجربة غيّر FALL_THRESHOLD إلى 20f أو 18f
 */
@Singleton
class AccelerometerSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    companion object {
        private const val TAG = "AccelerometerSensorManager"

        /**
         * عتبة كشف السقوط - قابلة للتعديل للتجربة
         *  - 18f = حساس جداً (يتفعل بالمشي السريع)
         *  - 25f = القيمة الأصلية (2.5g)
         *  - 35f = القيمة الحالية المرفوعة (3.5g) - تحتاج سقوط مقصود قوي
         *  - 45f = قاسي جداً (سقوط عنيف فقط)
         */
        const val FALL_THRESHOLD = 35f

        // منع تكرار الإنذار كل أجزاء من الثانية
        private const val FALL_DEBOUNCE_MS = 3000L
    }

    var onFallDetected: (() -> Unit)? = null
    var onAccelerationUpdate: ((Float, Float, Float) -> Unit)? = null

    private var isTracking = false
    private var lastFallTime = 0L
    private var lastAcceleration = Triple(0f, 0f, 0f)

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val accelerometerSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun startTracking() {
        if (isTracking) return
        val sensor = accelerometerSensor
        if (sensor == null) {
            Log.w(TAG, "Accelerometer sensor not available on this device")
            return
        }
        // SENSOR_DELAY_GAME = أسرع من NORMAL ومهم لكشف السقوط
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        isTracking = true
        Log.i(TAG, "Accelerometer tracking started - threshold=$FALL_THRESHOLD m/s² (~${FALL_THRESHOLD / 9.81f}g)")
    }

    fun stopTracking() {
        if (!isTracking) return
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister listener", e)
        }
        isTracking = false
        Log.i(TAG, "Accelerometer tracking stopped")
    }

    fun processAccelerationData(x: Float, y: Float, z: Float) {
        onAccelerationUpdate?.invoke(x, y, z)
        checkForFall(x, y, z)
        lastAcceleration = Triple(x, y, z)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                processAccelerationData(x, y, z)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accelerometer accuracy changed: $accuracy")
    }

    private fun checkForFall(x: Float, y: Float, z: Float) {
        val totalAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // Debounce لمنع التكرار
        if (now - lastFallTime < FALL_DEBOUNCE_MS) return

        if (totalAcceleration > FALL_THRESHOLD) {
            lastFallTime = now
            Log.w(TAG, "Fall detected! Acceleration: $totalAcceleration > $FALL_THRESHOLD | x=$x y=$y z=$z")
            onFallDetected?.invoke()
        }
    }

    fun isTrackingActive(): Boolean = isTracking

    // للتجربة اليدوية بدون حساس
    fun simulateFallForTesting(acceleration: Float = FALL_THRESHOLD + 5f) {
        Log.w(TAG, "Simulating fall with acceleration: $acceleration")
        onFallDetected?.invoke()
    }
}
