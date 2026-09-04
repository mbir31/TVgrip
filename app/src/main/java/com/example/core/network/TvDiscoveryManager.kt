package com.example.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.ProtocolType
import com.example.core.model.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Discovers Android TV / Google TV devices through the real Android TV Remote
 * v2 mDNS service (`_androidtvremote2._tcp`).
 *
 * Other service types (Google Cast, ADB, AirPlay) are intentionally NOT treated
 * as controllable TVs: they either serve a different protocol or are not part
 * of the Android TV Remote v2 pairing/control flow. Advertising a non-Android
 * TV device as controllable would be fake functionality.
 */
class TvDiscoveryManager(private val context: Context) {

    private val TAG = "TvDiscoveryManager"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    /**
     * `_androidtvremote2._tcp` is advertised by the Android TV Remote Service that
     * is pre-installed on Android TV and Google TV devices.
     */
    private val serviceTypes = listOf("_androidtvremote2._tcp.")

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        serviceTypes.forEach { serviceType ->
            try {
                val listener = createDiscoveryListener(serviceType)
                activeListeners.add(listener)
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
                Log.d(TAG, "Started NSD discovery for $serviceType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start NSD discovery for $serviceType", e)
            }
        }
    }

    fun stopDiscovery() {
        if (!_isScanning.value) return
        _isScanning.value = false

        activeListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery listener", e)
            }
        }
        activeListeners.clear()
        Log.d(TAG, "Stopped Android TV discovery")
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Android TV Remote service found: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.update { list ->
                    list.filterNot { it.name == serviceInfo.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: Error code $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: Error code $errorCode")
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val hostAddress = serviceInfo.host?.hostAddress ?: return
                    val cleanName = serviceInfo.serviceName.replace("\\032", " ").trim()
                    val id = "tv_${hostAddress.replace(".", "_")}"

                    val device = TvDevice(
                        id = id,
                        name = cleanName.ifBlank { "Android TV ($hostAddress)" },
                        manufacturer = if (cleanName.contains("Sony", ignoreCase = true)) "Sony"
                        else if (cleanName.contains("TCL", ignoreCase = true)) "TCL"
                        else if (cleanName.contains("Chromecast", ignoreCase = true)) "Google"
                        else if (cleanName.contains("Shield", ignoreCase = true)) "NVIDIA"
                        else "Google / Android TV",
                        model = cleanName,
                        platform = if (cleanName.contains("Google TV", ignoreCase = true)) "Google TV" else "Android TV",
                        serviceType = serviceInfo.serviceType,
                        host = hostAddress,
                        // The Android TV Remote crypto session always uses 6466;
                        // the mDNS advertised port can be the pairing port (6467).
                        port = 6466,
                        protocolType = ProtocolType.ANDROID_TV_REMOTE_V2,
                        connectionState = DeviceConnectionState.DISCONNECTED,
                        capabilities = CapabilitySet.DEFAULT_ANDROID_TV
                    )

                    _discoveredDevices.update { current ->
                        val existingIndex = current.indexOfFirst { it.id == device.id || it.host == device.host }
                        if (existingIndex >= 0) {
                            current.toMutableList().apply { set(existingIndex, device) }
                        } else {
                            current + device
                        }
                    }
                }
            }
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating service resolution", e)
        }
    }

    suspend fun testManualIp(ip: String, port: Int = 6467): TvDevice? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2000)
                socket.close()

                val id = "tv_manual_${ip.replace(".", "_")}"
                val device = TvDevice(
                    id = id,
                    name = "Android TV ($ip)",
                    manufacturer = "Android TV",
                    model = "Manual Host",
                    platform = "Android TV",
                    host = ip,
                    port = port,
                    protocolType = ProtocolType.ANDROID_TV_REMOTE_V2,
                    capabilities = CapabilitySet.DEFAULT_ANDROID_TV
                )

                _discoveredDevices.update { list ->
                    if (list.none { it.host == ip }) list + device else list
                }
                device
            } catch (e: Exception) {
                Log.d(TAG, "Manual IP check failed for $ip:$port: ${e.message}")
                null
            }
        }
    }
}
