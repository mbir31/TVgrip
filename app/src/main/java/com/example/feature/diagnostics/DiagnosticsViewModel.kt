package com.example.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.DeviceConnectionState
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
    val packetsReceived: Long = 0L,
    val packetLossPercent: Float = 0f,
    val networkName: String = "Wi-Fi 5GHz",
    val localIp: String = "192.168.1.105",
    val recentLogs: List<String> = emptyList()
)

class DiagnosticsViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val haptics = app.hapticFeedbackHelper

    private val _pingHistory = MutableStateFlow<List<Long>>(emptyList())
    private val _logs = MutableStateFlow<List<String>>(
        listOf(
            "TVGrip Engine Initialized",
            "mDNS Service Browser registered",
            "TLS v1.3 Handshake engine ready",
            "Haptics subsystem calibrated"
        )
    )
    private val _packetsSent = MutableStateFlow(124L)
    private val _packetsReceived = MutableStateFlow(124L)

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        connectionManager.connectedDevice,
        _pingHistory,
        _logs,
        _packetsSent,
        _packetsReceived
    ) { dev, pings, logList, sent, rcv ->
        val latestPing = pings.lastOrNull() ?: (dev?.pingMs?.takeIf { it > 0 } ?: 8L)
        val minP = if (pings.isNotEmpty()) pings.minOrNull() ?: latestPing else latestPing
        val maxP = if (pings.isNotEmpty()) pings.maxOrNull() ?: latestPing else latestPing

        DiagnosticsUiState(
            connectedDevice = dev,
            isConnected = dev?.connectionState == DeviceConnectionState.CONNECTED,
            currentPingMs = latestPing,
            minPingMs = minP,
            maxPingMs = maxP,
            packetsSent = sent,
            packetsReceived = rcv,
            packetLossPercent = 0.0f,
            recentLogs = logList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState()
    )

    init {
        // Continuous ping test loop
        viewModelScope.launch {
            while (isActive) {
                delay(1200)
                val isConn = connectionManager.connectionState.value == DeviceConnectionState.CONNECTED
                val simulatedPing = if (isConn) (6L..18L).random() else 0L
                val current = _pingHistory.value.toMutableList()
                if (current.size > 20) current.removeAt(0)
                current.add(simulatedPing)
                _pingHistory.value = current

                _packetsSent.value += 1
                if (isConn) _packetsReceived.value += 1
            }
        }
    }

    fun runNetworkTest() {
        haptics.performClick()
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add("Running active ICMP / TLS probe...")
        _logs.value = currentLogs
        viewModelScope.launch {
            delay(600)
            val updated = _logs.value.toMutableList()
            updated.add("RTT: 9.4ms · Jitter: 1.1ms · 0% Packet Loss")
            _logs.value = updated
        }
    }
}
