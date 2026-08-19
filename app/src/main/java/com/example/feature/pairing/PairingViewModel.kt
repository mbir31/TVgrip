package com.example.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvDevice
import com.example.core.network.PairingResult
import com.example.core.network.TvPairingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PairingStep {
    INTRO,
    SCANNING,
    SELECT_DEVICE,
    PAIRING_CODE_INPUT,
    CONNECTING,
    TESTING_CAPABILITIES,
    READY,
    ERROR
}

data class PairingUiState(
    val step: PairingStep = PairingStep.INTRO,
    val discoveredDevices: List<TvDevice> = emptyList(),
    val selectedDevice: TvDevice? = null,
    val pairingCode: String = "",
    val errorMessage: String? = null,
    val testedCapabilities: CapabilitySet? = null,
    val isScanning: Boolean = false,
    val manualIp: String = "",
    val isTestingManualIp: Boolean = false
)

class PairingViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val discoveryManager = app.discoveryManager
    private val connectionManager = app.connectionManager
    private val deviceRepository = app.tvDeviceRepository
    private val pairingService = TvPairingService(app)

    private val _step = MutableStateFlow(PairingStep.INTRO)
    private val _selectedDevice = MutableStateFlow<TvDevice?>(null)
    private val _pairingCode = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _testedCapabilities = MutableStateFlow<CapabilitySet?>(null)
    private val _manualIp = MutableStateFlow("")
    private val _isTestingManualIp = MutableStateFlow(false)

    val uiState: StateFlow<PairingUiState> = combine(
        _step,
        discoveryManager.discoveredDevices,
        _selectedDevice,
        _pairingCode,
        _errorMessage,
        _testedCapabilities,
        discoveryManager.isScanning
    ) { params: Array<Any?> ->
        val step = params[0] as PairingStep
        @Suppress("UNCHECKED_CAST")
        val devices = params[1] as List<TvDevice>
        val selected = params[2] as? TvDevice
        val code = params[3] as String
        val err = params[4] as? String
        val caps = params[5] as? CapabilitySet
        val isScanning = params[6] as Boolean
        PairingUiState(
            step = step,
            discoveredDevices = devices,
            selectedDevice = selected,
            pairingCode = code,
            errorMessage = err,
            testedCapabilities = caps,
            isScanning = isScanning,
            manualIp = _manualIp.value,
            isTestingManualIp = _isTestingManualIp.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PairingUiState()
    )

    fun startScanning() {
        _errorMessage.value = null
        _step.value = PairingStep.SCANNING
        discoveryManager.startDiscovery()
        viewModelScope.launch {
            delay(1500)
            _step.value = PairingStep.SELECT_DEVICE
        }
    }

    fun selectDevice(device: TvDevice) {
        _selectedDevice.value = device
        _errorMessage.value = null
        // Trigger TV pairing request on port 6467 to pop up the code on TV screen
        viewModelScope.launch {
            _step.value = PairingStep.CONNECTING
            when (val res = pairingService.startPairing(device)) {
                is PairingResult.CodePromptReceived -> {
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
                is PairingResult.Success -> {
                    initiateConnection(device, null)
                }
                is PairingResult.Failed -> {
                    _errorMessage.value = res.error
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
            }
        }
    }

    fun setPairingCode(code: String) {
        _pairingCode.value = code
    }

    fun setManualIp(ip: String) {
        _manualIp.value = ip
    }

    fun submitManualIp() {
        val ip = _manualIp.value.trim()
        if (ip.isBlank()) {
            _errorMessage.value = "Please enter a valid IP address."
            return
        }
        _isTestingManualIp.value = true
        viewModelScope.launch {
            val device = discoveryManager.testManualIp(ip)
            _isTestingManualIp.value = false
            if (device != null) {
                selectDevice(device)
            } else {
                _errorMessage.value = "Could not reach TV at $ip:6466. Ensure TV is powered on and connected to the same Wi-Fi."
            }
        }
    }

    fun submitPairingCode() {
        val device = _selectedDevice.value ?: return
        val code = _pairingCode.value.trim()
        if (code.isBlank()) {
            _errorMessage.value = "Please enter the pairing code displayed on your TV."
            return
        }
        _step.value = PairingStep.CONNECTING
        viewModelScope.launch {
            pairingService.confirmPairingCode(code)
            initiateConnection(device, code)
        }
    }

    private fun initiateConnection(device: TvDevice, code: String?) {
        _step.value = PairingStep.CONNECTING
        _errorMessage.value = null

        connectionManager.connect(device, code) { success, resultMessage ->
            viewModelScope.launch {
                if (success) {
                    _step.value = PairingStep.TESTING_CAPABILITIES
                    delay(800)
                    val caps = connectionManager.capabilities.value
                    _testedCapabilities.value = caps

                    // Save paired TV to database
                    val paired = device.copy(
                        connectionState = DeviceConnectionState.CONNECTED,
                        capabilities = caps,
                        lastConnectedAt = System.currentTimeMillis(),
                        isPreferred = true
                    )
                    deviceRepository.saveDevice(paired)
                    deviceRepository.setPreferredDevice(paired.id)

                    _step.value = PairingStep.READY
                } else if (resultMessage != null && resultMessage.startsWith("PAIRING_REQUIRED")) {
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                } else {
                    _errorMessage.value = resultMessage ?: "Connection failed. Please check network and try again."
                    _step.value = PairingStep.ERROR
                }
            }
        }
    }

    fun retry() {
        _step.value = PairingStep.INTRO
        _errorMessage.value = null
        discoveryManager.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        pairingService.disconnect()
    }
}
