package com.heartguard.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heartguard.watch.R
import com.heartguard.watch.data.local.entities.HealthDataEntity
import com.heartguard.watch.data.repository.HealthRepository
import com.heartguard.watch.data.sensor.AccelerometerSensorManager
import com.heartguard.watch.data.sensor.HeartRateSensorManager
import com.heartguard.watch.data.sensor.SkinTemperatureSensorManager
import com.heartguard.watch.data.sensor.FallDetectionEngine
import com.heartguard.watch.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class HealthMonitoringService : android.app.Service() {

    companion object {
        private const val TAG = "HealthMonitoringService"
        private const val CHANNEL_ID = "health_monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    @Inject lateinit var heartRateSensorManager: HeartRateSensorManager
    @Inject lateinit var skinTemperatureSensorManager: SkinTemperatureSensorManager
    @Inject lateinit var fallDetectionEngine: FallDetectionEngine
    @Inject lateinit var emergencyAlertService: EmergencyAlertService
    @Inject lateinit var healthRepository: HealthRepository
    @Inject lateinit var healthDataServer: HealthDataServer

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        healthDataServer.start()

        heartRateSensorManager.onHeartRateUpdate = { hr, status ->
            Log.d(TAG, "Heart rate update received: $hr bpm (accuracy=$status)")
            serviceScope.launch {
                healthRepository.insertHealthData(
                    HealthDataEntity(heartRate = hr, heartRateStatus = status)
                )
                emergencyAlertService.sendHealthDataUpdate(hr, null)
                healthDataServer.updateData(hr = hr, temp = null)
            }
        }

        heartRateSensorManager.onHeartRateAnomaly = { type, hr ->
            Log.w(TAG, "Heart rate anomaly detected: $type ($hr bpm)")
            serviceScope.launch {
                emergencyAlertService.sendHeartRateAlert(type, hr)
            }
        }

        skinTemperatureSensorManager.onTemperatureUpdate = { skinTemp, ambientTemp ->
            Log.d(TAG, "Temperature update received: skin=$skinTemp, ambient=$ambientTemp")
            serviceScope.launch {
                healthRepository.insertHealthData(
                    HealthDataEntity(skinTemperature = skinTemp, ambientTemperature = ambientTemp)
                )
                emergencyAlertService.sendHealthDataUpdate(null, skinTemp)
                healthDataServer.updateData(hr = null, temp = skinTemp)
            }
        }

        skinTemperatureSensorManager.onTemperatureAnomaly = { type, temp ->
            Log.w(TAG, "Temperature anomaly detected: $type ($temp°C)")
            serviceScope.launch {
                emergencyAlertService.sendTemperatureAlert(type, temp)
            }
        }

        fallDetectionEngine.onFallDetected = {
            Log.w(TAG, "Fall detected!")
            serviceScope.launch {
                emergencyAlertService.sendFallAlert()
            }
        }

        heartRateSensorManager.startTracking()
        skinTemperatureSensorManager.startTracking()
        fallDetectionEngine.startMonitoring()

        Log.i(TAG, "Health monitoring started")
    }

    private fun stopMonitoring() {
        heartRateSensorManager.stopTracking()
        skinTemperatureSensorManager.stopTracking()
        fallDetectionEngine.stopMonitoring()
        healthDataServer.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Health monitoring stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "مراقبة الصحة",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "تنبيهات مراقبة الصحة المستمرة"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = createOpenAppIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("حارس القلب نشط")
            .setContentText("جاري مراقبة حساسات الصحة...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = Intent(this, HomeActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HeartGuard::HealthMonitoringWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Also unregister sensors when Android destroys the service without an
        // explicit ACTION_STOP, otherwise singleton listeners retain callbacks.
        heartRateSensorManager.stopTracking()
        skinTemperatureSensorManager.stopTracking()
        fallDetectionEngine.stopMonitoring()
        heartRateSensorManager.onHeartRateUpdate = null
        heartRateSensorManager.onHeartRateAnomaly = null
        skinTemperatureSensorManager.onTemperatureUpdate = null
        skinTemperatureSensorManager.onTemperatureAnomaly = null
        fallDetectionEngine.onFallDetected = null
        healthDataServer.stop()
        serviceScope.cancel()
        releaseWakeLock()
    }
}
