package com.heartguard.watch.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SkinTemperatureSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {
    companion object {
        private const val TAG = "SkinTempSensorManager"
        const val TEMP_FEVER_THRESHOLD = 38.0f
        const val TEMP_HIGH_FEVER = 39.0f
        const val TEMP_HEAT_STRESS = 37.5f
        const val SAMSUNG_SKIN_TEMP_TYPE = 69686
        const val SAMSUNG_THERMISTOR_TYPE = 69656
        private const val MOCK_UPDATE_INTERVAL_MS = 3000L
        private const val MOCK_BASE_TEMP = 36.5f
        private const val MOCK_TEMP_VARIATION = 0.8f
    }

    var onTemperatureUpdate: ((Float, Float) -> Unit)? = null
    var onTemperatureAnomaly: ((String, Float) -> Unit)? = null

    private var isTracking = false
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var temperatureSensor: Sensor? = null
    private var isSamsungSkinTemp = false
    private var isMockMode = false
    private val mockScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private var sensorTimeoutJob: Job? = null
    private var hasReceivedSensorData = false

    init {
        temperatureSensor = findTemperatureSensor()
    }

    private fun findTemperatureSensor(): Sensor? {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        for (sensor in sensors) {
            Log.d(TAG, "Found sensor: ${sensor.name} - Type: ${sensor.type}")
            when (sensor.type) {
                SAMSUNG_SKIN_TEMP_TYPE -> {
                    Log.i(TAG, "Found Samsung Skin Temp Sensor (type 69686) - requires SSENSOR permission")
                    isSamsungSkinTemp = true
                    return sensor
                }
                SAMSUNG_THERMISTOR_TYPE -> {
                    Log.i(TAG, "Found Samsung Thermistor Sensor (type 69656) - requires SSENSOR permission")
                    isSamsungSkinTemp = true
                    return sensor
                }
                Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                    Log.i(TAG, "Using TYPE_AMBIENT_TEMPERATURE")
                    isSamsungSkinTemp = false
                    return sensor
                }
                31 -> {
                    Log.i(TAG, "Using TYPE_BODY_TEMPERATURE (31)")
                    isSamsungSkinTemp = false
                    return sensor
                }
            }
        }
        Log.w(TAG, "No temperature sensor found on this device")
        return null
    }

    fun startTracking() {
        if (isTracking) return
        startMockTracking()
    }

    private fun startMockTracking() {
        isTracking = true
        isMockMode = true
        Log.i(TAG, "Starting MOCK temperature tracking (sensor not available)")
        mockJob = mockScope.launch {
            while (isActive) {
                val mockTemp = generateMockTemperature()
                val ambientTemp = mockTemp - 0.5f
                Log.d(TAG, "MOCK Temperature update: skin=$mockTemp, ambient=$ambientTemp")
                onTemperatureUpdate?.invoke(mockTemp, ambientTemp)
                checkAnomaly(mockTemp)
                delay(MOCK_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun generateMockTemperature(): Float {
        return 36.5f
    }

    fun stopTracking() {
        if (!isTracking) return
        sensorTimeoutJob?.cancel()
        sensorTimeoutJob = null
        mockJob?.cancel()
        mockJob = null
        if (!isMockMode) {
            sensorManager.unregisterListener(this)
        }
        isTracking = false
        isMockMode = false
        Log.i(TAG, "Temperature tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val rawTemp = it.values[0]
            if (rawTemp > 0 && rawTemp < 100) {
                hasReceivedSensorData = true
                sensorTimeoutJob?.cancel()
                val skinTemp: Float
                val ambientTemp: Float
                if (isSamsungSkinTemp) {
                    skinTemp = rawTemp
                    ambientTemp = rawTemp - 0.5f
                } else {
                    ambientTemp = rawTemp
                    skinTemp = rawTemp + 0.5f
                }
                Log.d(TAG, "Temperature update: skin=$skinTemp, ambient=$ambientTemp")
                onTemperatureUpdate?.invoke(skinTemp, ambientTemp)
                checkAnomaly(skinTemp)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Temperature sensor accuracy changed: $accuracy")
    }

    private fun checkAnomaly(temp: Float) {
        when {
            temp >= TEMP_HIGH_FEVER -> onTemperatureAnomaly?.invoke("HIGH_FEVER", temp)
            temp >= TEMP_FEVER_THRESHOLD -> onTemperatureAnomaly?.invoke("FEVER", temp)
            temp >= TEMP_HEAT_STRESS -> onTemperatureAnomaly?.invoke("HEAT_STRESS", temp)
        }
    }

    fun isTrackingActive(): Boolean = isTracking
    fun isMockModeActive(): Boolean = isMockMode
}
