package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

data class NetworkStatus(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isEthernet: Boolean,
    val localIpAddress: String?
)

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkStatusFlow: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentStatus())
            }
        }

        // Do not require NET_CAPABILITY_INTERNET: a TV can be controlled on a
        // local-only Wi-Fi network with no upstream internet. We only care that
        // the phone has a usable LAN transport.
        val request = NetworkRequest.Builder().build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(getCurrentStatus())

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    fun getCurrentStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isEthernet = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isConnected = caps != null && (isWifi || isEthernet)
        val ip = getDeviceLocalIp()

        return NetworkStatus(
            isConnected = isConnected,
            isWifi = isWifi,
            isEthernet = isEthernet,
            localIpAddress = ip
        )
    }

    private fun getDeviceLocalIp(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
