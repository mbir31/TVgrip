package com.example.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val connectedDevice: TvDevice? = null,
    val isConnected: Boolean = false,
    val currentPingMs: Long = 0L,
    val minPingMs: Long = 0L,
    val maxPingMs: Long = 0L,
    val packetsSent: Long = 0L,
    val isTestingCommand: Boolean = false,
    val lastTestResult: String? = null,
    val recentLogs: List<String> = emptyList()
)

class DiagnosticsViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val haptics = app.hapticFeedbackHelper

    private val _pingHistory = MutableStateFlow<List<Long>>(emptyList())
    private val _isTestingCommand = MutableStateFlow(false)
    private val _lastTestResult = MutableStateFlow<String?>(null)
    private val _logs = MutableStateFlow<List<String>>(
        listOf(
            "TVGrip Network Diagnostic Initialized",
            "Android TV Remote v2 protocol loaded",
            "NSD discovery ready"
        )
    )

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        connectionManager.connectedDevice,
        connectionManager.measuredPingMs,
        _pingHistory,
        connectionManager.packetCountSent,
        _isTestingCommand,
        _lastTestResult,
        _logs
    ) { params: Array<Any?> ->
        val dev = params[0] as? TvDevice
        val measuredPing = params[1] as Long
        @Suppress("UNCHECKED_CAST")
        val pings = params[2] as List<Long>
        val sent = params[3] as Long
        val testing = params[4] as Boolean
        val testRes = params[5] as? String
        @Suppress("UNCHECKED_CAST")
        val logList = params[6] as List<String>

        val activePing = if (measuredPing > 0) measuredPing else (pings.lastOrNull() ?: 0L)
        val validPings = pings.filter { it > 0 }
        val minP = if (validPings.isNotEmpty()) validPings.minOrNull() ?: activePing else activePing
        val maxP = if (validPings.isNotEmpty()) validPings.maxOrNull() ?: activePing else activePing

        DiagnosticsUiState(
            connectedDevice = dev,
            isConnected = dev?.connectionState == DeviceConnectionState.CONNECTED,
            currentPingMs = activePing,
            minPingMs = minP,
            maxPingMs = maxP,
            packetsSent = sent,
            isTestingCommand = testing,
            lastTestResult = testRes,
            recentLogs = logList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState()
    )

    init {
        // Collect real measured RTT latency from Connection Manager
        viewModelScope.launch {
            connectionManager.measuredPingMs.collect { ping ->
                if (ping > 0) {
                    val current = _pingHistory.value.toMutableList()
                    if (current.size > 25) current.removeAt(0)
                    current.add(ping)
                    _pingHistory.value = current
                }
            }
        }
    }

    /**
     * Transport diagnostic: writes a protocol ping frame to the active remote
     * socket. This verifies the write path only; it cannot prove the TV acted
     * on a key until a human observes the TV.
     */
    fun runCommandVerificationTest() {
        haptics.performClick()
        val isConn = connectionManager.connectionState.value == DeviceConnectionState.CONNECTED
        if (!isConn) {
            _lastTestResult.value = "Device is not connected. Pair or connect first."
            addLog("Test aborted: No active TV connection.")
            return
        }

        _isTestingCommand.value = true
        _lastTestResult.value = "Sending protocol ping..."
        addLog("Initiating transport test (remote_ping_request).")

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            connectionManager.sendCommand(TvCommand.Ping)
            delay(400)
            val duration = System.currentTimeMillis() - startTime
            _isTestingCommand.value = false
            _lastTestResult.value =
                "Protocol ping written to the TLS socket (${duration}ms local write loop). " +
                    "Check the TV for the expected remote effect to complete on-device verification."
            addLog("Transport test: remote_ping_request frame written.")
        }
    }

    private fun addLog(msg: String) {
        val current = _logs.value.toMutableList()
        current.add(msg)
        if (current.size > 50) current.removeAt(0)
        _logs.value = current
    }
}
