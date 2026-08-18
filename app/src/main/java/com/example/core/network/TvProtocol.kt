package com.example.core.network

import com.example.core.model.CapabilitySet
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice

interface TvProtocol {
    suspend fun connect(device: TvDevice, pairingCode: String? = null): ConnectionResult
    suspend fun sendCommand(command: TvCommand): Boolean
    suspend fun measureLatency(): Long
    suspend fun fetchCapabilities(): CapabilitySet
    suspend fun disconnect()
    fun isConnected(): Boolean
}

sealed class ConnectionResult {
    data class Success(val capabilities: CapabilitySet) : ConnectionResult()
    data class RequiresPairingCode(val prompt: String) : ConnectionResult()
    data class Failed(val reason: String) : ConnectionResult()
}
