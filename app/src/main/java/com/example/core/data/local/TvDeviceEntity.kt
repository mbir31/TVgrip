package com.example.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.CapabilityLevel
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.ProtocolType
import com.example.core.model.TvDevice
import com.example.core.security.SecureValueStore

@Entity(tableName = "tv_devices")
data class TvDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val platform: String,
    val osVersion: String,
    val serviceType: String,
    val host: String,
    val port: Int,
    val protocolType: String,
    val lastConnectedAt: Long,
    val isPreferred: Boolean,
    val isFavorite: Boolean,
    val remoteSupported: String,
    val volumeSupported: String,
    val powerSupported: String,
    val keyboardSupported: String,
    val touchpadSupported: String,
    val airMouseSupported: String,
    val gamepadSupported: String,
    val motionSupported: String,
    val serverCertSha256: String?
) {
    fun toDomain(): TvDevice {
        return TvDevice(
            id = id,
            name = name,
            manufacturer = manufacturer,
            model = model,
            platform = platform,
            osVersion = osVersion,
            serviceType = serviceType,
            host = host,
            port = port,
            protocolType = runCatching { ProtocolType.valueOf(protocolType) }.getOrDefault(ProtocolType.ANDROID_TV_REMOTE_V2),
            connectionState = DeviceConnectionState.DISCONNECTED,
            capabilities = CapabilitySet(
                remoteNavigation = parseLevel(remoteSupported),
                volumeControl = parseLevel(volumeSupported),
                power = parseLevel(powerSupported),
                keyboardInput = parseLevel(keyboardSupported),
                touchpad = parseLevel(touchpadSupported),
                airMouse = parseLevel(airMouseSupported),
                gameController = parseLevel(gamepadSupported),
                motionSteering = parseLevel(motionSupported)
            ),
            lastConnectedAt = lastConnectedAt,
            isPreferred = isPreferred,
            isFavorite = isFavorite,
            serverCertSha256 = SecureValueStore.decrypt(serverCertSha256)
        )
    }

    private fun parseLevel(value: String): CapabilityLevel {
        return runCatching { CapabilityLevel.valueOf(value) }.getOrDefault(CapabilityLevel.SUPPORTED)
    }

    companion object {
        fun fromDomain(device: TvDevice): TvDeviceEntity {
            return TvDeviceEntity(
                id = device.id,
                name = device.name,
                manufacturer = device.manufacturer,
                model = device.model,
                platform = device.platform,
                osVersion = device.osVersion,
                serviceType = device.serviceType,
                host = device.host,
                port = device.port,
                protocolType = device.protocolType.name,
                lastConnectedAt = device.lastConnectedAt,
                isPreferred = device.isPreferred,
                isFavorite = device.isFavorite,
                remoteSupported = device.capabilities.remoteNavigation.name,
                volumeSupported = device.capabilities.volumeControl.name,
                powerSupported = device.capabilities.power.name,
                keyboardSupported = device.capabilities.keyboardInput.name,
                touchpadSupported = device.capabilities.touchpad.name,
                airMouseSupported = device.capabilities.airMouse.name,
                gamepadSupported = device.capabilities.gameController.name,
                motionSupported = device.capabilities.motionSteering.name,
                serverCertSha256 = device.serverCertSha256?.let { SecureValueStore.encrypt(it) ?: it }
            )
        }
    }
}
