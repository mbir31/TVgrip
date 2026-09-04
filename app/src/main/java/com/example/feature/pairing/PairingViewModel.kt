package com.example.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvDevice
import com.example.core.network.PairingResult
import com.example.core.network.TvPairingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val isTestingManualIp: Boolean = false,
    val pairingPrompt: String = "Enter the 6-character code shown on your TV screen"
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
    private val _pairingPrompt = MutableStateFlow("Enter the 6-character code shown on your TV screen")

    val uiState: StateFlow<PairingUiState> = combine(
        _step,
        discoveryManager.discoveredDevices,
        _selectedDevice,
        _pairingCode,
        _errorMessage,
        _testedCapabilities,
        discoveryManager.isScanning,
        _manualIp,
        _isTestingManualIp,
        _pairingPrompt
    ) { params: Array<Any?> ->
        val step = params[0] as PairingStep
        @Suppress("UNCHECKED_CAST")
        val devices = params[1] as List<TvDevice>
        val selected = params[2] as? TvDevice
        val code = params[3] as String
        val err = params[4] as? String
        val caps = params[5] as? CapabilitySet
        val isScanning = params[6] as Boolean
        val manualIp = params[7] as String
        val isTestingManual = params[8] as Boolean
        val prompt = params[9] as String

        PairingUiState(
            step = step,
            discoveredDevices = devices,
            selectedDevice = selected,
            pairingCode = code,
            errorMessage = err,
            testedCapabilities = caps,
            isScanning = isScanning,
            manualIp = manualIp,
            isTestingManualIp = isTestingManual,
            pairingPrompt = prompt
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
            delay(4000)
            _step.value = PairingStep.SELECT_DEVICE
        }
    }

    fun selectDevice(device: TvDevice) {
        _selectedDevice.value = device
        _errorMessage.value = null
        _step.value = PairingStep.CONNECTING

        viewModelScope.launch {
            when (val result = pairingService.startPairing(device)) {
                is PairingResult.CodePromptReceived -> {
                    _pairingPrompt.value = result.promptMessage
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
                is PairingResult.Success -> {
                    onPairingSuccess(device, result.serverCertSha256)
                }
                is PairingResult.Failed -> {
                    _errorMessage.value = result.error
                    _step.value = PairingStep.ERROR
                }
            }
        }
    }

    fun setPairingCode(code: String) {
        val filtered = code.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
        _pairingCode.value = filtered
    }

    fun submitPairingCode() {
        val code = _pairingCode.value.trim()
        if (code.length != 6) {
            _errorMessage.value = "Please enter the exact 6-character hexadecimal code shown on your TV screen."
            return
        }

        val device = _selectedDevice.value ?: return
        _errorMessage.value = null
        _step.value = PairingStep.CONNECTING

        viewModelScope.launch {
            when (val result = pairingService.confirmPairingCode(code)) {
                is PairingResult.Success -> {
                    onPairingSuccess(device, result.serverCertSha256)
                }
                is PairingResult.Failed -> {
                    _errorMessage.value = result.error
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
                is PairingResult.CodePromptReceived -> {
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
            }
        }
    }

    private suspend fun onPairingSuccess(device: TvDevice, serverCertSha256: String?) {
        _step.value = PairingStep.TESTING_CAPABILITIES

        val pairedDevice = device.copy(
            isFavorite = true,
            port = 6466,
            connectionState = DeviceConnectionState.CONNECTING,
            serverCertSha256 = serverCertSha256?.takeIf { it.isNotBlank() }
        )
        deviceRepository.saveDevice(pairedDevice)
        deviceRepository.setPreferredDevice(pairedDevice.id)

        // Connect the authenticated remote control session on port 6466.
        connectionManager.connect(pairedDevice)

        // Wait until the protocol reports a genuinely authenticated session
        // (remote_start received) or an error. Pairing already succeeded, so a
        // timeout simply means the remote session is still attempting to start.
        val finalState = withTimeoutOrNull(15_000L) {
            connectionManager.connectionState.first { state ->
                state == DeviceConnectionState.CONNECTED ||
                    state == DeviceConnectionState.ERROR ||
                    state == DeviceConnectionState.RECONNECTING
            }
        }

        _testedCapabilities.value = connectionManager.capabilities.value

        when (finalState) {
            DeviceConnectionState.ERROR -> {
                _errorMessage.value = connectionManager.lastError.value
                    ?: "Pairing succeeded but the remote control session could not be established."
                _step.value = PairingStep.ERROR
            }
            DeviceConnectionState.CONNECTED, DeviceConnectionState.RECONNECTING -> {
                _step.value = PairingStep.READY
            }
            null -> {
                _errorMessage.value =
                    "Pairing succeeded, but the Android TV Remote session did not become ready within 15 seconds. " +
                        "Make sure the TV is still on the same Wi-Fi network and press Retry to reconnect."
                _step.value = PairingStep.ERROR
            }
            else -> _step.value = PairingStep.ERROR
        }
    }

    fun setManualIp(ip: String) {
        _manualIp.value = ip
    }

    fun connectManualIp() {
        val ip = _manualIp.value.trim()
        if (ip.isBlank()) return

        _isTestingManualIp.value = true
        viewModelScope.launch {
            val reachable = discoveryManager.testManualIp(ip, 6467)
            _isTestingManualIp.value = false

            val device = TvDevice(
                id = "manual_${ip.replace('.', '_')}",
                name = "Android TV ($ip)",
                model = "Android TV",
                manufacturer = "Google TV",
                host = ip,
                port = 6466,
                connectionState = DeviceConnectionState.DISCONNECTED
            )
            if (reachable == null) {
                _errorMessage.value =
                    "Could not reach the TV pairing port (6467) at $ip. Confirm the IP is correct, the TV is on, and it is on the same Wi-Fi network."
                _step.value = PairingStep.ERROR
                return@launch
            }
            selectDevice(device)
        }
    }

    fun retry() {
        _errorMessage.value = null
        _pairingCode.value = ""
        pairingService.disconnect()
        startScanning()
    }

    override fun onCleared() {
        super.onCleared()
        pairingService.disconnect()
        discoveryManager.stopDiscovery()
    }
}
