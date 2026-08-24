package com.example.feature.pairing

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.bluetooth.BluetoothRemoteState
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
    BLUETOOTH_PAIRING,
    PAIRING_CODE_INPUT,
    CONNECTING,
    TESTING_CAPABILITIES,
    READY,
    ERROR
}

enum class ConnectionMode {
    WIFI_NETWORK,
    BLUETOOTH_HID
}

data class PairingUiState(
    val step: PairingStep = PairingStep.INTRO,
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_NETWORK,
    val discoveredDevices: List<TvDevice> = emptyList(),
    val selectedDevice: TvDevice? = null,
    val pairingCode: String = "",
    val errorMessage: String? = null,
    val testedCapabilities: CapabilitySet? = null,
    val isScanning: Boolean = false,
    val manualIp: String = "",
    val isTestingManualIp: Boolean = false,
    val bluetoothState: BluetoothRemoteState = BluetoothRemoteState.DISCONNECTED,
    val bluetoothPairedDevices: List<BluetoothDevice> = emptyList(),
    val bluetoothDiscoveredDevices: List<BluetoothDevice> = emptyList(),
    val bluetoothConnectedName: String? = null
)

class PairingViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val discoveryManager = app.discoveryManager
    private val connectionManager = app.connectionManager
    private val bluetoothManager = app.bluetoothTvRemoteManager
    private val deviceRepository = app.tvDeviceRepository
    private val pairingService = TvPairingService(app)

    private val _step = MutableStateFlow(PairingStep.INTRO)
    private val _connectionMode = MutableStateFlow(ConnectionMode.WIFI_NETWORK)
    private val _selectedDevice = MutableStateFlow<TvDevice?>(null)
    private val _pairingCode = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _testedCapabilities = MutableStateFlow<CapabilitySet?>(null)
    private val _manualIp = MutableStateFlow("")
    private val _isTestingManualIp = MutableStateFlow(false)

    val uiState: StateFlow<PairingUiState> = combine(
        _step,
        _connectionMode,
        discoveryManager.discoveredDevices,
        _selectedDevice,
        _pairingCode,
        _errorMessage,
        _testedCapabilities,
        discoveryManager.isScanning,
        bluetoothManager.bluetoothState,
        bluetoothManager.pairedTvDevices,
        bluetoothManager.discoveredTvDevices,
        bluetoothManager.connectedDeviceName
    ) { params: Array<Any?> ->
        val step = params[0] as PairingStep
        val mode = params[1] as ConnectionMode
        @Suppress("UNCHECKED_CAST")
        val devices = params[2] as List<TvDevice>
        val selected = params[3] as? TvDevice
        val code = params[4] as String
        val err = params[5] as? String
        val caps = params[6] as? CapabilitySet
        val isScanning = params[7] as Boolean
        val btState = params[8] as BluetoothRemoteState
        @Suppress("UNCHECKED_CAST")
        val btPaired = params[9] as List<BluetoothDevice>
        @Suppress("UNCHECKED_CAST")
        val btDiscovered = params[10] as List<BluetoothDevice>
        val btName = params[11] as? String

        PairingUiState(
            step = step,
            connectionMode = mode,
            discoveredDevices = devices,
            selectedDevice = selected,
            pairingCode = code,
            errorMessage = err,
            testedCapabilities = caps,
            isScanning = isScanning,
            manualIp = _manualIp.value,
            isTestingManualIp = _isTestingManualIp.value,
            bluetoothState = btState,
            bluetoothPairedDevices = btPaired,
            bluetoothDiscoveredDevices = btDiscovered,
            bluetoothConnectedName = btName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PairingUiState()
    )

    fun setConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
    }

    fun startScanning() {
        _errorMessage.value = null
        if (_connectionMode.value == ConnectionMode.BLUETOOTH_HID) {
            _step.value = PairingStep.BLUETOOTH_PAIRING
            bluetoothManager.initialize()
            bluetoothManager.startDiscovery()
        } else {
            _step.value = PairingStep.SCANNING
            discoveryManager.startDiscovery()
            viewModelScope.launch {
                delay(1500)
                _step.value = PairingStep.SELECT_DEVICE
            }
        }
    }

    fun startBluetoothPairing() {
        _connectionMode.value = ConnectionMode.BLUETOOTH_HID
        _step.value = PairingStep.BLUETOOTH_PAIRING
        bluetoothManager.initialize()
        bluetoothManager.startDiscovery()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun connectBluetoothDevice(device: BluetoothDevice) {
        _errorMessage.value = null
        _step.value = PairingStep.CONNECTING
        bluetoothManager.connectToDevice(device)
        
        viewModelScope.launch {
            delay(1200)
            val tvDevice = TvDevice(
                id = "bt_${device.address.replace(":", "")}",
                name = device.name ?: "Bluetooth TV (${device.address})",
                host = device.address,
                port = 0,
                platform = "Bluetooth HID Remote",
                connectionState = DeviceConnectionState.CONNECTED
            )
            deviceRepository.saveDevice(tvDevice)
            deviceRepository.setPreferredDevice(tvDevice.id)
            _selectedDevice.value = tvDevice
            _step.value = PairingStep.READY
        }
    }

    fun selectDevice(device: TvDevice) {
        _selectedDevice.value = device
        _errorMessage.value = null
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
                    _step.value = PairingStep.ERROR
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
            when (val confirmResult = pairingService.confirmPairingCode(code)) {
                is PairingResult.Success -> {
                    initiateConnection(device, code)
                }
                is PairingResult.Failed -> {
                    _errorMessage.value = confirmResult.error
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
                is PairingResult.CodePromptReceived -> {
                    _step.value = PairingStep.PAIRING_CODE_INPUT
                }
            }
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
        bluetoothManager.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        bluetoothManager.stopDiscovery()
        pairingService.disconnect()
    }
}
