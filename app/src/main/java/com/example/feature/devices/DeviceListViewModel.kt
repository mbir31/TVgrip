package com.example.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.TvDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceListUiState(
    val devices: List<TvDevice> = emptyList(),
    val connectedDeviceId: String? = null,
    val isScanning: Boolean = false
)

class DeviceListViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val deviceRepository = app.tvDeviceRepository
    private val connectionManager = app.connectionManager
    private val discoveryManager = app.discoveryManager
    private val haptics = app.hapticFeedbackHelper

    val uiState: StateFlow<DeviceListUiState> = combine(
        deviceRepository.allDevices,
        connectionManager.connectedDevice,
        discoveryManager.isScanning
    ) { list, connected, scanning ->
        DeviceListUiState(
            devices = list,
            connectedDeviceId = connected?.id,
            isScanning = scanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceListUiState()
    )

    fun connectDevice(device: TvDevice) {
        haptics.performClick()
        connectionManager.connect(device)
    }

    fun disconnect() {
        haptics.performClick()
        connectionManager.disconnect()
    }

    fun setPreferred(deviceId: String) {
        haptics.performClick()
        viewModelScope.launch {
            deviceRepository.setPreferredDevice(deviceId)
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        viewModelScope.launch {
            deviceRepository.renameDevice(deviceId, newName)
        }
    }

    fun deleteDevice(deviceId: String) {
        haptics.performHeavyClick()
        viewModelScope.launch {
            if (uiState.value.connectedDeviceId == deviceId) {
                connectionManager.disconnect()
            }
            deviceRepository.deleteDevice(deviceId)
        }
    }
}
