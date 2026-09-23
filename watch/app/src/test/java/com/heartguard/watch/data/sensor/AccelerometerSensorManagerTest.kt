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

        replayFall(manager, sensor, startNs = 1_000_000_000L)
        assertEquals(1, alerts)
        assertTrue(updates > 10)

        engine.stopMonitoring()
        assertFalse(shadowOf(platformManager).hasListener(manager))
        assertFalse(engine.isMonitoringActive())
        replayFall(manager, sensor, startNs = 2_000_000_000L)
        assertEquals(1, alerts)

        engine.startMonitoring()
        replayFall(manager, sensor, startNs = 3_000_000_000L)
        assertEquals(2, alerts)
        engine.stopMonitoring()
    }

    @Test
    fun puttingTheWatchDownDoesNotAlert() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(platformManager).addSensor(sensor)
        val manager = AccelerometerSensorManager(context)
        var alerts = 0
        manager.onFallDetected = { alerts++ }
        manager.startTracking()
        manager.onSensorChanged(event(sensor, 0f, 0f, 30f, 1_000_000_000L))
        manager.onSensorChanged(event(sensor, 0f, 0f, 9.81f, 1_020_000_000L))
        assertEquals(0, alerts)
        manager.stopTracking()
    }

    @Test
    fun ignoresOtherSensorsAndNonFiniteSamples() {
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(platformManager).addSensor(accelerometer)
        val manager = AccelerometerSensorManager(context)
        var alerts = 0
        manager.onFallDetected = { alerts++ }
        manager.startTracking()
        manager.onSensorChanged(event(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE), 0f, 0f, 30f, 1L))
        manager.onSensorChanged(event(accelerometer, Float.NaN, 0f, 0f, 2L))
        manager.onSensorChanged(null)
        assertEquals(0, alerts)
        manager.stopTracking()
    }

    private fun replayFall(manager: AccelerometerSensorManager, sensor: Sensor, startNs: Long) {
        var t = startNs
        fun step(x: Float, y: Float, z: Float) {
            manager.onSensorChanged(event(sensor, x, y, z, t))
            t += 20_000_000L
        }
        repeat(3) { step(0f, 0f, 9.81f) }
        repeat(8) { step(0f, 0f, 0.4f) }
        step(0f, 0f, 32f)
        repeat(45) { step(0f, 0f, 9.7f) }
    }

    private fun event(
        sensor: Sensor,
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long
    ): SensorEvent {
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        return constructor.newInstance(3).apply {
            this.sensor = sensor
            timestamp = timestampNs
            values[0] = x
            values[1] = y
            values[2] = z
        }
    }
}
