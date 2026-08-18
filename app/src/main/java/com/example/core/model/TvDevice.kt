package com.example.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvDevice(
    val id: String,
    val name: String,
    val manufacturer: String = "Google / Android TV",
    val model: String = "Android TV Device",
    val platform: String = "Android TV",
    val osVersion: String = "Android TV 14",
    val serviceType: String = "_androidtvremote2._tcp",
    val host: String,
    val port: Int = 6466,
    val protocolType: ProtocolType = ProtocolType.ANDROID_TV_REMOTE_V2,
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val capabilities: CapabilitySet = CapabilitySet.DEFAULT_ANDROID_TV,
    val lastConnectedAt: Long = 0L,
    val isPreferred: Boolean = false,
    val isFavorite: Boolean = false,
    val pingMs: Long = -1L
)

enum class ProtocolType {
    ANDROID_TV_REMOTE_V2,
    GOOGLE_CAST_REMOTING,
    TVGRIP_COMPANION,
    GENERIC_ADB_LOCAL
}

enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

@Immutable
data class CapabilitySet(
    val remoteNavigation: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val volumeControl: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val mute: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val power: CapabilityLevel = CapabilityLevel.LIMITED,
    val mediaControl: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val keyboardInput: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val clipboardSync: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val touchpad: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val airMouse: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val appLaunch: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val gameController: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val motionSteering: CapabilityLevel = CapabilityLevel.LIMITED,
    val voiceInput: CapabilityLevel = CapabilityLevel.SUPPORTED,
    val currentAppDetection: CapabilityLevel = CapabilityLevel.LIMITED
) {
    companion object {
        val DEFAULT_ANDROID_TV = CapabilitySet()
        val FULLY_FEATURED = CapabilitySet(
            power = CapabilityLevel.SUPPORTED,
            motionSteering = CapabilityLevel.SUPPORTED,
            currentAppDetection = CapabilityLevel.SUPPORTED
        )
    }
}

enum class CapabilityLevel {
    SUPPORTED,
    LIMITED,
    UNSUPPORTED
}

enum class TvKey(val code: Int, val label: String) {
    UP(19, "DPAD_UP"),
    DOWN(20, "DPAD_DOWN"),
    LEFT(21, "DPAD_LEFT"),
    RIGHT(22, "DPAD_RIGHT"),
    CENTER(23, "DPAD_CENTER"),
    BACK(4, "BACK"),
    HOME(3, "HOME"),
    MENU(82, "MENU"),
    VOLUME_UP(24, "VOLUME_UP"),
    VOLUME_DOWN(25, "VOLUME_DOWN"),
    VOLUME_MUTE(164, "VOLUME_MUTE"),
    POWER(26, "POWER"),
    INPUT_SOURCE(178, "INPUT"),
    CHANNEL_UP(166, "CHANNEL_UP"),
    CHANNEL_DOWN(167, "CHANNEL_DOWN"),
    MEDIA_PLAY_PAUSE(85, "MEDIA_PLAY_PAUSE"),
    MEDIA_PLAY(126, "MEDIA_PLAY"),
    MEDIA_PAUSE(127, "MEDIA_PAUSE"),
    MEDIA_PREVIOUS(88, "MEDIA_PREV"),
    MEDIA_NEXT(87, "MEDIA_NEXT"),
    MEDIA_REWIND(89, "MEDIA_REWIND"),
    MEDIA_FAST_FORWARD(90, "MEDIA_FAST_FORWARD"),
    TV_GUIDE(172, "GUIDE"),
    SUBTITLE(175, "SUBTITLE"),
    SETTINGS(176, "SETTINGS"),
    RECENT_APPS(187, "RECENT_APPS"),
    ENTER(66, "ENTER"),
    BACKSPACE(67, "BACKSPACE")
}

@Immutable
data class TVAppItem(
    val id: String,
    val name: String,
    val packageName: String,
    val iconResName: String? = null,
    val isFavorite: Boolean = false
)
