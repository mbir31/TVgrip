package com.example.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

enum class BluetoothRemoteState {
    UNAVAILABLE,
    BLUETOOTH_OFF,
    DISCONNECTED,
    SCANNING,
    REGISTERING_HID,
    READY_TO_PAIR,
    CONNECTED
}

/**
 * Standard Bluetooth HID (Human Interface Device) & Bluetooth Remote Profile.
 *
 * Implements standard Bluetooth Consumer Control / Keyboard HID profile,
 * allowing the phone to act directly as a physical Bluetooth remote control for any
 * Android TV, Google TV, Samsung Tizen, LG webOS, or Fire TV.
 */
class BluetoothTvRemoteManager(private val context: Context) {

    private val TAG = "BluetoothTvRemote"

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedBluetoothDevice: BluetoothDevice? = null

    private val _bluetoothState = MutableStateFlow(BluetoothRemoteState.DISCONNECTED)
    val bluetoothState: StateFlow<BluetoothRemoteState> = _bluetoothState.asStateFlow()

    private val _pairedTvDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedTvDevices: StateFlow<List<BluetoothDevice>> = _pairedTvDevices.asStateFlow()

    private val _discoveredTvDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredTvDevices: StateFlow<List<BluetoothDevice>> = _discoveredTvDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    /**
     * HID Report Descriptor for Combo Keyboard + Consumer Control (TV Remote) + Mouse
     */
    private val HID_REPORT_DESCRIPTOR = byteArrayOf(
        // Keyboard (Report ID 1)
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x01.toByte(), //   Report ID (1)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (224 - Left Control)
        0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (231 - Right GUI)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x95.toByte(), 0x08.toByte(), //   Report Count (8)
        0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x81.toByte(), 0x01.toByte(), //   Input (Constant) - Reserved byte
        0x95.toByte(), 0x06.toByte(), //   Report Count (6)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - Key arrays
        0xC0.toByte(),                // End Collection

        // Consumer Controls / TV Remote (Report ID 2)
        0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer Devices)
        0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x02.toByte(), //   Report ID (2)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x26.toByte(), 0x9C.toByte(), 0x02.toByte(), // Logical Maximum (0x029C)
        0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
        0x2A.toByte(), 0x9C.toByte(), 0x02.toByte(), // Usage Maximum (0x029C)
        0x75.toByte(), 0x10.toByte(), //   Report Size (16)
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x81.toByte(), 0x00.toByte(), //   Input (Data, Array)
        0xC0.toByte(),                // End Collection

        // Mouse (Report ID 3)
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x03.toByte(), //   Report ID (3)
        0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
        0x05.toByte(), 0x09.toByte(), //     Usage Page (Buttons)
        0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
        0x29.toByte(), 0x03.toByte(), //     Usage Maximum (3)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //     Report Size (1)
        0x95.toByte(), 0x03.toByte(), //     Report Count (3)
        0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
        0x75.toByte(), 0x05.toByte(), //     Report Size (5)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x81.toByte(), 0x01.toByte(), //     Input (Constant)
        0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(), //     Usage (X)
        0x09.toByte(), 0x31.toByte(), //     Usage (Y)
        0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8)
        0x95.toByte(), 0x02.toByte(), //     Report Count (2)
        0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
        0xC0.toByte(),                //   End Collection
        0xC0.toByte()                 // End Collection
    )

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _bluetoothState.value = BluetoothRemoteState.DISCONNECTED
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "HID App Status: registered=$registered, pluggedDevice=${pluggedDevice?.address}")
            if (registered) {
                _bluetoothState.value = if (connectedBluetoothDevice != null) BluetoothRemoteState.CONNECTED else BluetoothRemoteState.READY_TO_PAIR
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "HID Device state: ${device.name} ($state)")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedBluetoothDevice = device
                    _connectedDeviceName.value = device.name ?: device.address
                    _bluetoothState.value = BluetoothRemoteState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedBluetoothDevice?.address == device.address) {
                        connectedBluetoothDevice = null
                        _connectedDeviceName.value = null
                        _bluetoothState.value = BluetoothRemoteState.READY_TO_PAIR
                    }
                }
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { dev ->
                        val current = _discoveredTvDevices.value.toMutableList()
                        if (current.none { it.address == dev.address }) {
                            current.add(dev)
                            _discoveredTvDevices.value = current
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_bluetoothState.value == BluetoothRemoteState.SCANNING) {
                        _bluetoothState.value = if (connectedBluetoothDevice != null) BluetoothRemoteState.CONNECTED else BluetoothRemoteState.READY_TO_PAIR
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
    }

    @SuppressLint("MissingPermission")
    fun initialize() {
        if (bluetoothAdapter == null) {
            _bluetoothState.value = BluetoothRemoteState.UNAVAILABLE
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _bluetoothState.value = BluetoothRemoteState.BLUETOOTH_OFF
            return
        }

        refreshPairedDevices()

        // Get Bluetooth HID Device profile proxy
        bluetoothAdapter.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val hid = hidDevice ?: return
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "TVGrip Remote",
            "Smart Bluetooth TV Remote & Controller",
            "TVGrip",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HID_REPORT_DESCRIPTOR
        )
        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        hid.registerApp(sdpSettings, qosSettings, null, Executors.newCachedThreadPool(), hidCallback)
        _bluetoothState.value = BluetoothRemoteState.REGISTERING_HID
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        val paired = bluetoothAdapter.bondedDevices.toList()
        _pairedTvDevices.value = paired
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        _discoveredTvDevices.value = emptyList()
        _bluetoothState.value = BluetoothRemoteState.SCANNING
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        if (_bluetoothState.value == BluetoothRemoteState.SCANNING) {
            _bluetoothState.value = if (connectedBluetoothDevice != null) BluetoothRemoteState.CONNECTED else BluetoothRemoteState.READY_TO_PAIR
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        hid.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val dev = connectedBluetoothDevice ?: return
        hidDevice?.disconnect(dev)
        connectedBluetoothDevice = null
        _connectedDeviceName.value = null
        _bluetoothState.value = BluetoothRemoteState.READY_TO_PAIR
    }

    /**
     * Sends standard Bluetooth TV Remote key press
     */
    @SuppressLint("MissingPermission")
    fun sendTvKey(key: TvKey) {
        val dev = connectedBluetoothDevice ?: return
        val hid = hidDevice ?: return

        val consumerCode = getConsumerCodeForKey(key)
        if (consumerCode != null) {
            // Send Consumer Report (Report ID 2)
            val downReport = byteArrayOf((consumerCode and 0xFF).toByte(), ((consumerCode shr 8) and 0xFF).toByte())
            val upReport = byteArrayOf(0x00, 0x00)

            hid.sendReport(dev, 2, downReport)
            Thread.sleep(30)
            hid.sendReport(dev, 2, upReport)
        } else {
            // Send Standard Keyboard Report (Report ID 1)
            val keyboardCode = getKeyboardCodeForKey(key)
            val downReport = byteArrayOf(0, 0, keyboardCode.toByte(), 0, 0, 0, 0, 0)
            val upReport = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)

            hid.sendReport(dev, 1, downReport)
            Thread.sleep(30)
            hid.sendReport(dev, 1, upReport)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMouseDelta(dx: Int, dy: Int, leftClick: Boolean) {
        val dev = connectedBluetoothDevice ?: return
        val hid = hidDevice ?: return

        val buttons = if (leftClick) 0x01 else 0x00
        val clampedX = dx.coerceIn(-127, 127).toByte()
        val clampedY = dy.coerceIn(-127, 127).toByte()

        val report = byteArrayOf(buttons.toByte(), clampedX, clampedY)
        hid.sendReport(dev, 3, report)
    }

    private fun getConsumerCodeForKey(key: TvKey): Int? {
        return when (key) {
            TvKey.POWER -> 0x0030 // Power
            TvKey.VOLUME_UP -> 0x00E9 // Volume Increment
            TvKey.VOLUME_DOWN -> 0x00EA // Volume Decrement
            TvKey.VOLUME_MUTE -> 0x00E2 // Mute
            TvKey.MEDIA_PLAY_PAUSE -> 0x00CD // Play/Pause
            TvKey.MEDIA_PLAY -> 0x00B0 // Play
            TvKey.MEDIA_PAUSE -> 0x00B1 // Pause
            TvKey.MEDIA_NEXT -> 0x00B5 // Scan Next Track
            TvKey.MEDIA_PREVIOUS -> 0x00B6 // Scan Previous Track
            TvKey.MEDIA_REWIND -> 0x00B4 // Rewind
            TvKey.MEDIA_FAST_FORWARD -> 0x00B3 // Fast Forward
            TvKey.HOME -> 0x0223 // AC Home
            TvKey.BACK -> 0x0224 // AC Back
            TvKey.CHANNEL_UP -> 0x009C // Channel Increment
            TvKey.CHANNEL_DOWN -> 0x009D // Channel Decrement
            TvKey.MENU -> 0x0040 // Menu
            else -> null
        }
    }

    private fun getKeyboardCodeForKey(key: TvKey): Int {
        return when (key) {
            TvKey.UP -> 0x52 // Up Arrow
            TvKey.DOWN -> 0x51 // Down Arrow
            TvKey.LEFT -> 0x50 // Left Arrow
            TvKey.RIGHT -> 0x4F // Right Arrow
            TvKey.CENTER -> 0x28 // Enter
            TvKey.ENTER -> 0x28 // Enter
            TvKey.BACKSPACE -> 0x2A // Delete (Backspace)
            else -> 0x28
        }
    }

    fun isConnected(): Boolean = connectedBluetoothDevice != null
}
