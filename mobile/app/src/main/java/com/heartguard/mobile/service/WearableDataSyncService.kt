package com.heartguard.mobile.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.shared.constants.AlertConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableDataSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthDataHolder: HealthDataHolder
) {
    companion object {
        private const val TAG = "WearableDataSyncService"
        private const val SYNC_INTERVAL_MS = 3000L
        private const val SERVICE_TYPE = "_heartguard._tcp."
        private const val WATCH_PORT = 8765
    }

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var discoveredHost: String? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "NSD discovery started")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "NSD resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host.hostAddress
                    Log.d(TAG, "NSD service resolved: $host:${serviceInfo.port}")
                    discoveredHost = host
                }
            })
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
            discoveredHost = null
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "NSD discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "NSD discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "NSD discovery stop failed: $errorCode")
        }
    }

    fun startPeriodicSync() {
        if (syncJob?.isActive == true) return
        startNsdDiscovery()
        syncJob = syncScope.launch {
            while (isActive) {
                try {
                    syncAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
        Log.i(TAG, "Periodic data sync started")
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
        stopNsdDiscovery()
        Log.i(TAG, "Periodic data sync stopped")
    }

    private fun startNsdDiscovery() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start NSD discovery", e)
        }
    }

    private fun stopNsdDiscovery() {
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop NSD discovery", e)
        }
    }

    private suspend fun syncAll() {
        var connected = false
        try {
            val nodes = nodeClient.connectedNodes.await()
            connected = nodes.isNotEmpty()
            healthDataHolder.setWatchConnected(connected)
            Log.d(TAG, "Watch connected: ${nodes.size} nodes")

            if (connected) {
                syncViaWearDataClient()
                for (node in nodes) {
                    try {
                        syncViaHttp(discoveredHost ?: return)
                    } catch (_: Exception) {}
                }
            } else {
                val host = discoveredHost
                if (host != null) {
                    syncViaHttp(host)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check connectivity", e)
            healthDataHolder.setWatchConnected(false)
        }
    }

    private suspend fun syncViaWearDataClient() {
        try {
            val uri = android.net.Uri.Builder()
                .scheme("wear")
                .path(AlertConstants.HEALTH_DATA_PATH)
                .build()
            val dataItems = dataClient.getDataItems(uri).await()

            for (i in 0 until dataItems.count) {
                val dataItem = dataItems[i]
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

                val heartRate = if (dataMap.containsKey(AlertConstants.EXTRA_HEART_RATE)) {
                    dataMap.getInt(AlertConstants.EXTRA_HEART_RATE)
                } else null

                val temperature = if (dataMap.containsKey(AlertConstants.EXTRA_TEMPERATURE)) {
                    dataMap.getFloat(AlertConstants.EXTRA_TEMPERATURE)
                } else null

                val motion = if (dataMap.containsKey(AlertConstants.EXTRA_MOTION)) {
                    dataMap.getFloat(AlertConstants.EXTRA_MOTION)
                } else null

                Log.d(TAG, "Wear API sync: HR=$heartRate, Temp=$temperature, Motion=$motion")
                healthDataHolder.updateHealthData(heartRate, temperature, motion)
            }
            dataItems.release()
        } catch (e: Exception) {
            Log.d(TAG, "Wear API sync failed: ${e.message}")
        }
    }

    private suspend fun syncViaHttp(host: String) {
        try {
            val url = URL("http://$host:$WATCH_PORT/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                val heartRate = if (json.has(AlertConstants.EXTRA_HEART_RATE)) {
                    json.getInt(AlertConstants.EXTRA_HEART_RATE)
                } else null

                val temperature = if (json.has(AlertConstants.EXTRA_TEMPERATURE)) {
                    json.getDouble(AlertConstants.EXTRA_TEMPERATURE).toFloat()
                } else null

                val motion = if (json.has(AlertConstants.EXTRA_MOTION)) {
                    json.getDouble(AlertConstants.EXTRA_MOTION).toFloat()
                } else null

                Log.d(TAG, "HTTP sync success from $host: HR=$heartRate, Temp=$temperature")
                healthDataHolder.setWatchConnected(true)
                healthDataHolder.updateHealthData(heartRate, temperature, motion)
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "HTTP sync failed from $host: ${e.message}")
        }
    }
}
