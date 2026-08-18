package com.example.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.AirMouseConfig
import com.example.core.model.CapabilityLevel
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RemotePreset {
    CLASSIC,
    MINIMAL,
    TOUCHPAD,
    ONE_HANDED
}

data class RemoteUiState(
    val connectedDevice: TvDevice? = null,
    val isConnected: Boolean = false,
    val capabilities: CapabilitySet = CapabilitySet.DEFAULT_ANDROID_TV,
    val activePreset: RemotePreset = RemotePreset.CLASSIC,
    val isAirMouseActive: Boolean = false,
    val isMuted: Boolean = false,
    val isPowerDialogVisible: Boolean = false,
    val airMouseConfig: AirMouseConfig = AirMouseConfig()
)

class RemoteViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val airMouseEngine = app.airMouseEngine
    private val haptics = app.hapticFeedbackHelper
    private val settingsRepository = app.settingsRepository

    private val _activePreset = MutableStateFlow(RemotePreset.CLASSIC)
    private val _isAirMouseActive = MutableStateFlow(false)
    private val _isMuted = MutableStateFlow(false)

    val uiState: StateFlow<RemoteUiState> = combine(
        connectionManager.connectedDevice,
        connectionManager.capabilities,
        _activePreset,
        _isAirMouseActive,
        _isMuted,
        settingsRepository.settingsFlow
    ) { params: Array<Any?> ->
        val device = params[0] as? TvDevice
        val caps = params[1] as CapabilitySet
        val preset = params[2] as RemotePreset
        val airMouse = params[3] as Boolean
        val muted = params[4] as Boolean
        val settings = params[5] as com.example.core.data.repository.AppSettings
        RemoteUiState(
            connectedDevice = device,
            isConnected = device?.connectionState == DeviceConnectionState.CONNECTED,
            capabilities = caps,
            activePreset = preset,
            isAirMouseActive = airMouse,
            isMuted = muted,
            airMouseConfig = settings.airMouseConfig
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteUiState()
    )

    fun sendKey(key: TvKey) {
        haptics.performClick()
        connectionManager.sendCommand(TvCommand.KeyPress(key))
        if (key == TvKey.VOLUME_MUTE) {
            _isMuted.value = !_isMuted.value
        }
    }

    fun sendKeyDown(key: TvKey) {
        connectionManager.sendCommand(TvCommand.KeyDown(key))
    }

    fun sendKeyUp(key: TvKey) {
        connectionManager.sendCommand(TvCommand.KeyUp(key))
    }

    fun sendPointerDelta(dx: Float, dy: Float) {
        connectionManager.sendCommand(TvCommand.PointerMove(dx, dy))
    }

    fun sendPointerClick(isLongPress: Boolean = false) {
        haptics.performClick()
        connectionManager.sendCommand(TvCommand.PointerClick(isLongPress))
    }

    fun sendPointerScroll(scrollY: Float) {
        connectionManager.sendCommand(TvCommand.PointerScroll(scrollY))
    }

    fun setPreset(preset: RemotePreset) {
        _activePreset.value = preset
        if (preset != RemotePreset.TOUCHPAD && _isAirMouseActive.value) {
            // Keep air mouse state or allow toggling
        }
    }

    fun toggleAirMouse() {
        val newState = !_isAirMouseActive.value
        _isAirMouseActive.value = newState
        if (newState) {
            airMouseEngine.config = uiState.value.airMouseConfig
            airMouseEngine.start()
            haptics.performSuccess()
        } else {
            airMouseEngine.stop()
            haptics.performClick()
        }
    }

    fun calibrateAirMouse() {
        airMouseEngine.calibrateNeutral()
        haptics.performHeavyClick()
    }

    override fun onCleared() {
        super.onCleared()
        airMouseEngine.stop()
    }
}
