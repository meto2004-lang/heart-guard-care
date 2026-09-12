package com.heartguard.watch.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.heartguard.shared.constants.AlertConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HealthDataServer"
        const val PORT = 8765
        private const val SERVICE_NAME = "HeartGuardWatch"
        private const val SERVICE_TYPE = "_heartguard._tcp."
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    var latestHeartRate: Int = 0
        private set

    @Volatile
    var latestTemperature: Float = 0f
        private set

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Health data server started on port $PORT")
                registerNsdService()

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: continue
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Error accepting client", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        unregisterNsdService()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server", e)
        }
        serverSocket = null
    }

    fun updateData(hr: Int?, temp: Float?) {
        hr?.let { latestHeartRate = it }
        temp?.let { latestTemperature = it }
    }

    private fun registerNsdService() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                port = PORT
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "NSD service registered: ${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "NSD registration failed: $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "NSD service unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "NSD unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
        }
    }

    private fun unregisterNsdService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister NSD service", e)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = PrintWriter(socket.getOutputStream(), true)

            val requestLine = input.readLine() ?: return
            while (input.readLine()?.isNotEmpty() == true) {}

            val path = requestLine.split(" ").getOrElse(1) { "/" }

            if (path.startsWith("/health")) {
                val json = JSONObject().apply {
                    put(AlertConstants.EXTRA_HEART_RATE, latestHeartRate)
                    put(AlertConstants.EXTRA_TEMPERATURE, latestTemperature.toDouble())
                    put("timestamp", System.currentTimeMillis())
                    put("status", "ok")
                }

                val body = json.toString()
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: ${body.toByteArray().size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    body

                output.print(response)
                output.flush()
                Log.d(TAG, "Sent health data: HR=$latestHeartRate, Temp=$latestTemperature")
            } else if (path.startsWith("/ping")) {
                val body = "{\"status\":\"pong\"}"
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.toByteArray().size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    body

                output.print(response)
                output.flush()
            } else {
                val body = "{\"error\":\"not found\"}"
                val response = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.toByteArray().size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    body

                output.print(response)
                output.flush()
            }

            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
