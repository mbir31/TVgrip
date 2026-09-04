package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val connectedDevice: TvDevice? = null,
    val isConnected: Boolean = false,
    val savedDevices: List<TvDevice> = emptyList(),
    val isScanning: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val deviceRepository = app.tvDeviceRepository
    private val discoveryManager = app.discoveryManager
    private val settingsRepository = app.settingsRepository

    val uiState: StateFlow<HomeUiState> = combine(
        connectionManager.connectedDevice,
        deviceRepository.allDevices,
        discoveryManager.isScanning
    ) { connectedDev, savedList, isScanning ->
        HomeUiState(
            connectedDevice = connectedDev,
            isConnected = connectedDev?.connectionState == DeviceConnectionState.CONNECTED,
            savedDevices = savedList,
            isScanning = isScanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        // Reconnect to the preferred/last-connected TV when the app opens if
        // auto-reconnect is enabled. Session-loss and network-change recovery
        // are handled independently by TvConnectionManager.
        viewModelScope.launch {
            val settings = app.settingsRepository.settingsFlow.first()
            if (!settings.autoReconnect) return@launch
            val preferred = deviceRepository.getPreferredDevice()
            if (preferred != null && connectionManager.connectionState.value == DeviceConnectionState.DISCONNECTED) {
                connectionManager.connect(preferred)
            }
        }
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun reconnectCurrentTv() {
        uiState.value.connectedDevice?.let { dev ->
            connectionManager.connect(dev)
        }
    }
}
