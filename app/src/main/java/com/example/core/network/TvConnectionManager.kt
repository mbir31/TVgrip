package com.example.core.network

import android.util.Log
import com.example.TVGripApplication
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

/**
 * Owns the lifecycle and health of the active Android TV Remote session.
 *
 * The public connection state only becomes CONNECTED after the underlying
 * TvProtocol reports an authenticated, ready session. Lost sessions trigger a
 * bounded exponential-backoff reconnect instead of silently remaining green.
 */
class TvConnectionManager {

    private val TAG = "TvConnectionManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectedDevice = MutableStateFlow<TvDevice?>(null)
    val connectedDevice: StateFlow<TvDevice?> = _connectedDevice.asStateFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    fun isConnected(): Boolean =
        _connectionState.value == DeviceConnectionState.CONNECTED &&
            activeProtocol?.isConnected() == true

    private val _capabilities = MutableStateFlow(CapabilitySet.DEFAULT_ANDROID_TV)
    val capabilities: StateFlow<CapabilitySet> = _capabilities.asStateFlow()

    private val _measuredPingMs = MutableStateFlow<Long>(-1L)
    val measuredPingMs: StateFlow<Long> = _measuredPingMs.asStateFlow()

    private val _packetCountSent = MutableStateFlow(0L)
    val packetCountSent: StateFlow<Long> = _packetCountSent.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    @Volatile
    private var activeProtocol: TvProtocol? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var desiredDevice: TvDevice? = null
    @Volatile
    private var manualDisconnect: Boolean = false
    @Volatile
    private var lastNetworkAvailable: Boolean? = null

    init {
        logInfo("TVGrip Connection Manager initialized.")
    }

    /**
     * Notifies the manager that the local network changed (Wi-Fi switched,
     * network came back, etc.). If we were intentionally connected to an
     * Android TV and the network is now usable again, this restarts the
     * bounded reconnect loop without requiring the user to tap Connect.
     */
    fun onNetworkChanged(isNetworkAvailable: Boolean) {
        if (manualDisconnect || desiredDevice == null) return
        if (lastNetworkAvailable == isNetworkAvailable) return
        lastNetworkAvailable = isNetworkAvailable
        logInfo("Network availability changed: $isNetworkAvailable")

        if (!isNetworkAvailable) {
            stopPingLoop()
            val device = desiredDevice
            if (_connectionState.value == DeviceConnectionState.CONNECTED && device != null) {
                _connectionState.value = DeviceConnectionState.RECONNECTING
                _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.RECONNECTING)
                logInfo("Network lost; waiting for connectivity before reconnecting.")
            }
            return
        }

        if (desiredDevice != null &&
            _connectionState.value != DeviceConnectionState.CONNECTED &&
            _connectionState.value != DeviceConnectionState.CONNECTING
        ) {
            val device = desiredDevice!!
            logInfo("Network restored; reconnecting to ${device.name}.")
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                val result = attemptConnect(device, null)
                if (result.first) {
                    logInfo("Reconnected to ${device.name} after the network change.")
                } else {
                    logError("Reconnect after network change failed: ${result.second}")
                }
            }
        }
    }

    fun connect(device: TvDevice, pairingCode: String? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        // Invalidate any old session for a different TV before switching.
        val previous = activeProtocol
        activeProtocol = null
        stopPingLoop()
        scope.launch { try { previous?.disconnect() } catch (_: Exception) {} }

        desiredDevice = device.copy(connectionState = DeviceConnectionState.CONNECTING)
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null

        _connectionState.value = DeviceConnectionState.CONNECTING
        _lastError.value = null
        _connectedDevice.value = desiredDevice
        logInfo("Connecting to TV: ${device.name} (${device.host}:${device.port}) via ${device.protocolType}")

        scope.launch {
            val result = attemptConnect(device, pairingCode)
            onResult?.invoke(result.first, result.second)
        }
    }

    private suspend fun attemptConnect(device: TvDevice, pairingCode: String?): Pair<Boolean, String?> {
        val protocol = try {
            createProtocol(device, onDisconnected = { onProtocolLost() })
        } catch (e: Exception) {
            _connectionState.value = DeviceConnectionState.ERROR
            _lastError.value = e.message ?: "Unsupported protocol for ${device.name}."
            logError(_lastError.value!!)
            return false to _lastError.value
        }
        val result = protocol.connect(device, pairingCode)
        return when (result) {
            is ConnectionResult.Success -> {
                if (!protocol.isConnected()) {
                    protocol.disconnect()
                    _connectionState.value = DeviceConnectionState.ERROR
                    _lastError.value = "The TV TLS session connected but did not complete the Android TV Remote handshake."
                    logError(_lastError.value!!)
                    false to _lastError.value
                } else {
                    activeProtocol = protocol
                    _connectionState.value = DeviceConnectionState.CONNECTED
                    _capabilities.value = result.capabilities
                    _connectedDevice.value = device.copy(
                        connectionState = DeviceConnectionState.CONNECTED,
                        capabilities = result.capabilities,
                        lastConnectedAt = System.currentTimeMillis()
                    )
                    logInfo("Authenticated remote session established with ${device.name}.")
                    startPingLoop()
                    true to null
                }
            }
            is ConnectionResult.RequiresPairingCode -> {
                _connectionState.value = DeviceConnectionState.PAIRING
                _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.PAIRING)
                logInfo("Pairing code required for ${device.name}: ${result.prompt}")
                false to "PAIRING_REQUIRED:${result.prompt}"
            }
            is ConnectionResult.Failed -> {
                protocol.disconnect()
                _connectionState.value = DeviceConnectionState.ERROR
                _lastError.value = result.reason
                _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.ERROR)
                logError("Connection failed: ${result.reason}")
                false to result.reason
            }
        }
    }

    private fun createProtocol(device: TvDevice, onDisconnected: () -> Unit): TvProtocol {
        return when (device.protocolType) {
            ProtocolType.TVGRIP_COMPANION -> TVGripCompanionProtocol()
            ProtocolType.GOOGLE_CAST_REMOTING,
            ProtocolType.GENERIC_ADB_LOCAL -> throw IllegalArgumentException(
                "${device.protocolType} does not use the Android TV Remote v2 control protocol."
            )
            else -> AndroidTvRemoteProtocol(onDisconnected = onDisconnected)
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
        manualDisconnect = true
        desiredDevice = null
        reconnectJob?.cancel()
        reconnectJob = null
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

    /**
     * Called by the protocol when the socket is lost. Avoids tight loops by
     * using exponential backoff (1s -> 2s -> ... -> 30s max) and stops retrying
     * after 8 attempts until the user reconnects manually.
     */
    private fun onProtocolLost() {
        if (manualDisconnect) return
        if (_connectionState.value == DeviceConnectionState.CONNECTING ||
            _connectionState.value == DeviceConnectionState.DISCONNECTED
        ) {
            // A stale callback from a previous TV attempted while we are already
            // connecting to a different device, or after an intentional disconnect.
            return
        }
        val device = desiredDevice ?: _connectedDevice.value ?: return

        val lostProtocol = activeProtocol
        activeProtocol = null
        scope.launch { try { lostProtocol?.disconnect() } catch (_: Exception) {} }
        stopPingLoop()
        _connectionState.value = DeviceConnectionState.RECONNECTING
        _connectedDevice.value = device.copy(connectionState = DeviceConnectionState.RECONNECTING)
        logInfo("Remote session lost; will retry with backoff.")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            var delayMs = 1000L
            while (isActive && !manualDisconnect && desiredDevice != null) {
                delay(delayMs)
                if (manualDisconnect || desiredDevice == null) break
                attempt += 1
                if (attempt > 8) {
                    logError("Gave up reconnecting after $attempt attempts. Reconnect manually when the TV is reachable.")
                    _connectionState.value = DeviceConnectionState.ERROR
                    _lastError.value = "Lost connection to ${device.name}. Reconnect manually when the TV is back online."
                    break
                }
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
                Log.d(TAG, "Reconnect attempt $attempt for ${device.name}")
                val result = attemptConnect(desiredDevice!!, null)
                if (result.first) {
                    logInfo("Reconnected to ${device.name} after $attempt attempt(s).")
                    break
                }
            }
        }
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch {
            while (isActive && activeProtocol?.isConnected() == true) {
                delay(3000)
                // Send a real protocol ping, then read the round-trip time.
                try {
                    activeProtocol?.sendCommand(TvCommand.Ping)
                } catch (e: Exception) {
                    logError("Ping failed: ${e.message}")
                }
                val latency = activeProtocol?.measureLatency() ?: -1L
                if (latency >= 0) {
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

    private fun logInfo(msg: String) {
        Log.i(TAG, msg)
        appendLog("INFO", msg)
    }

    private fun logError(msg: String) {
        Log.e(TAG, msg)
        appendLog("ERROR", msg)
    }

    private fun appendLog(level: String, msg: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val entry = DiagnosticLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = msg
        )
        _diagnosticLogs.update { (it + entry).takeLast(100) }
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
