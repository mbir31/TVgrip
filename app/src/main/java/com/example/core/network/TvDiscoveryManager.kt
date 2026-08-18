package com.example.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.core.model.CapabilityLevel
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class TvDiscoveryManager(private val context: Context) {

    private val TAG = "TvDiscoveryManager"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    // Common Android TV, Google TV, ADB, and Cast mDNS service types
    private val serviceTypes = listOf(
        "_androidtvremote2._tcp.",
        "_googlecast._tcp.",
        "_adb-tls-pairing._tcp.",
        "_adb-tls-connect._tcp.",
        "_tvgrip._tcp.",
        "_airplay._tcp."
    )

    private var subnetScanJob: kotlinx.coroutines.Job? = null

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        // 1. Start mDNS / NSD Service Discoveries
        serviceTypes.forEach { serviceType ->
            try {
                val listener = createDiscoveryListener(serviceType)
                activeListeners.add(listener)
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
                Log.d(TAG, "Started discovery for $serviceType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery for $serviceType", e)
            }
        }

        // 2. Parallel Fast Local Subnet Ping Sweep for Android TV Ports (6466, 6467, 5555, 8008)
        subnetScanJob = CoroutineScope(Dispatchers.IO).launch {
            scanLocalSubnet()
        }
    }

    fun stopDiscovery() {
        if (!_isScanning.value) return
        _isScanning.value = false
        subnetScanJob?.cancel()
        subnetScanJob = null

        activeListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery listener", e)
            }
        }
        activeListeners.clear()
        Log.d(TAG, "Stopped all TV discoveries")
    }

    private suspend fun scanLocalSubnet() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wifiManager?.dhcpInfo
            val gatewayIp = if (dhcp != null && dhcp.gateway != 0) {
                val ip = dhcp.gateway
                "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}"
            } else {
                "192.168.1"
            }

            // Target likely TV IP host ranges concurrently
            kotlinx.coroutines.coroutineScope {
                (1..254).chunked(32).forEach { chunk ->
                    chunk.map { hostNum ->
                        launch {
                            val ip = "$gatewayIp.$hostNum"
                            checkAndroidTvHost(ip)
                        }
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Subnet sweep completed/interrupted: ${e.message}")
        }
    }

    private suspend fun checkAndroidTvHost(ip: String) {
        val standardPorts = listOf(6466, 6467, 5555, 8008)
        for (port in standardPorts) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 400)
                socket.close()

                val id = "tv_net_${ip.replace(".", "_")}_$port"
                val platformName = when (port) {
                    6466, 6467 -> "Android TV / Google TV"
                    8008 -> "Google Cast / TV"
                    5555 -> "Android TV (ADB Bridge)"
                    else -> "Smart TV"
                }

                val detectedDevice = TvDevice(
                    id = id,
                    name = "Android TV ($ip)",
                    manufacturer = "Android TV",
                    model = "Network Host",
                    platform = platformName,
                    serviceType = "_androidtvremote2._tcp.",
                    host = ip,
                    port = port,
                    protocolType = if (port == 6466 || port == 6467) ProtocolType.ANDROID_TV_REMOTE_V2 else ProtocolType.TVGRIP_COMPANION,
                    connectionState = DeviceConnectionState.DISCONNECTED,
                    capabilities = CapabilitySet.DEFAULT_ANDROID_TV
                )

                _discoveredDevices.update { list ->
                    if (list.none { it.host == ip }) list + detectedDevice else list
                }
                break
            } catch (_: Exception) {
                // Not reachable on this port
            }
        }
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
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
                    val port = serviceInfo.port
                    val cleanName = serviceInfo.serviceName.replace("\\032", " ").trim()
                    val id = "tv_${hostAddress.replace(".", "_")}_$port"

                    val isCompanion = serviceInfo.serviceType.contains("tvgrip")
                    val protocol = if (isCompanion) ProtocolType.TVGRIP_COMPANION else ProtocolType.ANDROID_TV_REMOTE_V2

                    val capabilities = if (isCompanion) {
                        CapabilitySet.FULLY_FEATURED
                    } else {
                        CapabilitySet.DEFAULT_ANDROID_TV
                    }

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
                        port = if (port > 0) port else 6466,
                        protocolType = protocol,
                        connectionState = DeviceConnectionState.DISCONNECTED,
                        capabilities = capabilities
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

    suspend fun testManualIp(ip: String, port: Int = 6466): TvDevice? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2000)
                socket.close()

                val id = "tv_manual_${ip.replace(".", "_")}_$port"
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
                Log.d(TAG, "Manual IP test failed for $ip:$port: ${e.message}")
                null
            }
        }
    }
}
