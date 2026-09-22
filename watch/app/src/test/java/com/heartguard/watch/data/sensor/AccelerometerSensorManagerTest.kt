package com.heartguard.watch.data.sensor

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AccelerometerSensorManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val platformManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test
    fun missingAccelerometerDoesNotReportActiveMonitoring() {
        val manager = AccelerometerSensorManager(context)
        val engine = FallDetectionEngine(context, manager)
        engine.startMonitoring()
        assertFalse(manager.isTrackingActive())
        assertFalse(engine.isMonitoringActive())
    }

    @Test
    fun realSensorCallbackReachesEngineAndStopsAfterUnregister() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(platformManager).addSensor(sensor)
        val manager = AccelerometerSensorManager(context)
        val engine = FallDetectionEngine(context, manager)
        var alerts = 0
        var updates = 0
        engine.onFallDetected = { alerts++ }
        manager.onAccelerationUpdate = { _, _, _ -> updates++ }

        engine.startMonitoring()
        engine.startMonitoring() // Starting twice must not reset impact suppression.
        assertTrue(engine.isMonitoringActive())
        assertTrue(shadowOf(platformManager).hasListener(manager))

        manager.onSensorChanged(event(sensor, 30f))
        manager.onSensorChanged(event(sensor, 31f))
        assertEquals(1, alerts)
        assertEquals(2, updates)

        engine.stopMonitoring()
        assertFalse(shadowOf(platformManager).hasListener(manager))
        assertFalse(engine.isMonitoringActive())
        manager.onSensorChanged(event(sensor, 30f))
        assertEquals(1, alerts)
        assertEquals(2, updates)

        engine.startMonitoring()
        manager.onSensorChanged(event(sensor, 30f))
        assertEquals(2, alerts)
        engine.stopMonitoring()
    }

    @Test
    fun ignoresOtherSensorsAndNonFiniteSamples() {
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(platformManager).addSensor(accelerometer)
        val manager = AccelerometerSensorManager(context)
        var alerts = 0
        manager.onFallDetected = { alerts++ }
        manager.startTracking()
        manager.onSensorChanged(event(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE), 30f))
        manager.onSensorChanged(event(accelerometer, Float.NaN))
        manager.onSensorChanged(null)
        assertEquals(0, alerts)
        manager.stopTracking()
    }

    private fun event(sensor: Sensor, z: Float): SensorEvent {
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        return constructor.newInstance(3).apply {
            this.sensor = sensor
            values[0] = 0f
            values[1] = 0f
            values[2] = z
        }
    }
}
