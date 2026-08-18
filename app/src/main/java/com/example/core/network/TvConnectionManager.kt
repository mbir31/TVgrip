package com.example.core.network

import android.util.Log
import com.example.core.model.CapabilityLevel
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.ProtocolType
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: String,
    val level: String,
    val message: String
)

class TvConnectionManager {

    private val TAG = "TvConnectionManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectedDevice = MutableStateFlow<TvDevice?>(null)
    val connectedDevice: StateFlow<TvDevice?> = _connectedDevice.asStateFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _capabilities = MutableStateFlow(CapabilitySet.DEFAULT_ANDROID_TV)
    val capabilities: StateFlow<CapabilitySet> = _capabilities.asStateFlow()

    private val _measuredPingMs = MutableStateFlow<Long>(-1L)
    val measuredPingMs: StateFlow<Long> = _measuredPingMs.asStateFlow()

    private val _packetCountSent = MutableStateFlow(0L)
    val packetCountSent: StateFlow<Long> = _packetCountSent.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private var activeProtocol: TvProtocol? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        logInfo("TVGrip Connection Manager initialized.")
    }

    fun connect(device: TvDevice, pairingCode: String? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        scope.launch {
            _connectionState.value = DeviceConnectionState.CONNECTING
            _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.CONNECTING)
            logInfo("Connecting to TV: ${device.name} (${device.host}:${device.port}) via ${device.protocolType}")

            val protocol: TvProtocol = when (device.protocolType) {
                ProtocolType.TVGRIP_COMPANION -> TVGripCompanionProtocol()
                else -> AndroidTvRemoteProtocol()
            }

            activeProtocol = protocol
            when (val res = protocol.connect(device, pairingCode)) {
                is ConnectionResult.Success -> {
                    _connectionState.value = DeviceConnectionState.CONNECTED
                    _capabilities.value = res.capabilities
                    _connectedDevice.value = device.copy(
                        connectionState = DeviceConnectionState.CONNECTED,
                        capabilities = res.capabilities,
                        lastConnectedAt = System.currentTimeMillis()
                    )
                    logInfo("Connection established with ${device.name}. Capabilities: Remote=${res.capabilities.remoteNavigation}, AirMouse=${res.capabilities.airMouse}")
                    startPingLoop()
                    onResult?.invoke(true, null)
                }
                is ConnectionResult.RequiresPairingCode -> {
                    _connectionState.value = DeviceConnectionState.PAIRING
                    _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.PAIRING)
                    logInfo("Pairing code required for ${device.name}: ${res.prompt}")
                    onResult?.invoke(false, "PAIRING_REQUIRED:${res.prompt}")
                }
                is ConnectionResult.Failed -> {
                    _connectionState.value = DeviceConnectionState.ERROR
                    _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.ERROR)
                    logError("Connection failed: ${res.reason}")
                    onResult?.invoke(false, res.reason)
                }
            }
        }
    }

    fun sendCommand(command: TvCommand) {
        val protocol = activeProtocol
        if (protocol == null || !protocol.isConnected()) {
            return
        }
        scope.launch {
            val success = protocol.sendCommand(command)
            if (success) {
                _packetCountSent.update { it + 1 }
            } else {
                logError("Failed to deliver command to TV: $command")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            stopPingLoop()
            activeProtocol?.disconnect()
            activeProtocol = null
            _connectionState.value = DeviceConnectionState.DISCONNECTED
            _connectedDevice.value = _connectedDevice.value?.copy(connectionState = DeviceConnectionState.DISCONNECTED)
            _measuredPingMs.value = -1L
            logInfo("Disconnected from TV device.")
        }
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch {
            while (isActive && activeProtocol?.isConnected() == true) {
                delay(3000)
                val latency = activeProtocol?.measureLatency() ?: -1L
                if (latency > 0) {
                    _measuredPingMs.value = latency
                    _connectedDevice.value = _connectedDevice.value?.copy(pingMs = latency)
                }
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    fun logInfo(message: String) {
        addLog("INFO", message)
    }

    fun logError(message: String) {
        addLog("ERROR", message)
    }

    private fun addLog(level: String, message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = DiagnosticLogEntry(timeStr, level, message)
        _diagnosticLogs.update { list ->
            (list + entry).takeLast(100)
        }
        Log.d(TAG, "[$level] $message")
    }

    companion object {
        @Volatile
        private var instance: TvConnectionManager? = null

        fun getInstance(): TvConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: TvConnectionManager().also { instance = it }
            }
        }
    }
}
