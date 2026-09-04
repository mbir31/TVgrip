package com.example.core.model

import androidx.compose.runtime.Immutable

enum class PlayerSlot(
    val slotIndex: Int,
    val label: String,
    val fullName: String,
    val colorHex: Long,
    val accentName: String,
    val activeLedCount: Int
) {
    PLAYER_1(0, "P1", "Player 1 (Host)", 0xFF00E5FF, "Neon Cyan", 1),
    PLAYER_2(1, "P2", "Player 2", 0xFFFF6D00, "Solar Orange", 2),
    PLAYER_3(2, "P3", "Player 3", 0xFF00E676, "Emerald Green", 3),
    PLAYER_4(3, "P4", "Player 4", 0xFFD500F9, "Electric Violet", 4);

    companion object {
        fun fromIndex(index: Int): PlayerSlot = entries.getOrElse(index.coerceIn(0, 3)) { PLAYER_1 }
    }
}

@Immutable
data class PlayerSlotInfo(
    val slot: PlayerSlot,
    val deviceName: String,
    val isLocalDevice: Boolean = false,
    val isOccupied: Boolean = false,
    val pingMs: Long = 0L,
    val batteryPercent: Int = 100
)

@Immutable
data class GamepadState(
    var playerSlot: PlayerSlot = PlayerSlot.PLAYER_1,
    var leftStickX: Float = 0f,
    var leftStickY: Float = 0f,
    var isL3Pressed: Boolean = false,

    var rightStickX: Float = 0f,
    var rightStickY: Float = 0f,
    var isR3Pressed: Boolean = false,

    var isDpadUp: Boolean = false,
    var isDpadDown: Boolean = false,
    var isDpadLeft: Boolean = false,
    var isDpadRight: Boolean = false,

    var isAPressed: Boolean = false,
    var isBPressed: Boolean = false,
    var isXPressed: Boolean = false,
    var isYPressed: Boolean = false,

    var isL1Pressed: Boolean = false,
    var isR1Pressed: Boolean = false,

    var triggerL2: Float = 0f,
    var triggerR2: Float = 0f,

    var isStartPressed: Boolean = false,
    var isSelectPressed: Boolean = false,
    var isHomePressed: Boolean = false,
    var isBackPressed: Boolean = false,

    var tiltSteer: Float = 0f,
    var tiltPitch: Float = 0f,

    var throttle: Float = 0f,
    var brake: Float = 0f,
    var handbrake: Boolean = false,
    var gear: Int = 1,

    val buttons: MutableMap<ControllerButton, Boolean> = mutableMapOf()
) {
    val l2Value: Float get() = triggerL2
    val r2Value: Float get() = triggerR2
}

enum class ControllerButton(val label: String, val defaultTvKey: TvKey) {
    A("A", TvKey.BUTTON_A),
    B("B", TvKey.BUTTON_B),
    X("X", TvKey.BUTTON_X),
    Y("Y", TvKey.BUTTON_Y),
    L1("L1", TvKey.BUTTON_L1),
    R1("R1", TvKey.BUTTON_R1),
    L2("L2", TvKey.BUTTON_L2),
    R2("R2", TvKey.BUTTON_R2),
    L3("L3", TvKey.BUTTON_THUMBL),
    R3("R3", TvKey.BUTTON_THUMBR),
    DPAD_UP("UP", TvKey.UP),
    DPAD_DOWN("DOWN", TvKey.DOWN),
    DPAD_LEFT("LEFT", TvKey.LEFT),
    DPAD_RIGHT("RIGHT", TvKey.RIGHT),
    START("START", TvKey.BUTTON_START),
    SELECT("SELECT", TvKey.BUTTON_SELECT),
    HOME("HOME", TvKey.HOME),
    MENU("MENU", TvKey.MENU),
    BACK("BACK", TvKey.BACK)
}

enum class ControllerMode {
    STANDARD,
    RACING_STEERING,
    RETRO_DPAD,
    CUSTOM
}

enum class ControllerProfileType {
    CLASSIC,
    XBOX,
    PLAYSTATION,
    NINTENDO,
    RETRO,
    FPS,
    RACING,
    PLATFORMER,
    CLOUD_GAMING,
    CUSTOM
}

enum class ButtonLayoutType {
    XBOX,
    PLAYSTATION,
    NINTENDO
}

@Immutable
data class MotionSteeringConfig(
    val sensitivity: Float = 1.2f,
    val deadZone: Float = 0.05f
)

@Immutable
data class ControllerProfile(
    val id: String,
    val name: String,
    val type: ControllerProfileType = ControllerProfileType.CLASSIC,
    val mode: ControllerMode = ControllerMode.STANDARD,
    val buttonLayout: ButtonLayoutType = ButtonLayoutType.XBOX,
    val stickDeadZone: Float = 0.12f,
    val stickSensitivity: Float = 1.0f,
    val triggerDeadZone: Float = 0.05f,
    val triggerSensitivity: Float = 1.0f,
    val invertLeftY: Boolean = false,
    val invertRightY: Boolean = false,
    val isHapticsEnabled: Boolean = true,
    val isTurboEnabled: Boolean = false,
    val turboIntervalMs: Long = 80L,
    val turboRateHz: Int = 10,
    val motionSteeringEnabled: Boolean = false,
    val motionSensitivity: Float = 1.2f,
    val steeringSensitivity: Float = 1.2f,
    val buttonMappings: Map<String, String> = defaultMappings(type),
    val mapping: Map<ControllerButton, TvKey> = defaultButtonKeyMap()
) {
    companion object {
        fun defaultButtonKeyMap(): Map<ControllerButton, TvKey> {
            return ControllerButton.entries.associateWith { it.defaultTvKey }
        }

        fun defaultMappings(type: ControllerProfileType): Map<String, String> {
            return when (type) {
                ControllerProfileType.XBOX -> mapOf("A" to "A / Jump", "B" to "B / Crouch", "X" to "X / Reload", "Y" to "Y / Swap", "L1" to "LB / Aim", "R1" to "RB / Fire")
                ControllerProfileType.PLAYSTATION -> mapOf("A" to "Cross / Confirm", "B" to "Circle / Back", "X" to "Square / Action", "Y" to "Triangle / Menu", "L1" to "L1", "R1" to "R1")
                ControllerProfileType.NINTENDO -> mapOf("A" to "A / Confirm", "B" to "B / Cancel", "X" to "X", "Y" to "Y", "L1" to "L", "R1" to "R")
                ControllerProfileType.RACING -> mapOf("A" to "Throttle", "B" to "Brake", "X" to "Handbrake", "Y" to "Nitro / Boost", "L1" to "Gear Down", "R1" to "Gear Up")
                else -> mapOf("A" to "A", "B" to "B", "X" to "X", "Y" to "Y", "L1" to "L1", "R1" to "R1", "L2" to "L2", "R2" to "R2")
            }
        }

        val DEFAULT_STANDARD = ControllerProfile(
            id = "preset_classic",
            name = "Classic Android TV",
            type = ControllerProfileType.CLASSIC,
            mode = ControllerMode.STANDARD,
            buttonLayout = ButtonLayoutType.XBOX
        )

        val PRESET_PROFILES = listOf(
            DEFAULT_STANDARD,
            ControllerProfile(
                id = "preset_xbox",
                name = "Xbox Wireless Style",
                type = ControllerProfileType.XBOX,
                mode = ControllerMode.STANDARD,
                buttonLayout = ButtonLayoutType.XBOX
            ),
            ControllerProfile(
                id = "preset_ps",
                name = "PlayStation DualStyle",
                type = ControllerProfileType.PLAYSTATION,
                mode = ControllerMode.STANDARD,
                buttonLayout = ButtonLayoutType.PLAYSTATION
            ),
            ControllerProfile(
                id = "preset_racing",
                name = "Apex Motion Racing",
                type = ControllerProfileType.RACING,
                mode = ControllerMode.RACING_STEERING,
                motionSteeringEnabled = true
            ),
            ControllerProfile(
                id = "preset_retro",
                name = "Retro Arcade Pad",
                type = ControllerProfileType.RETRO,
                mode = ControllerMode.RETRO_DPAD
            )
        )
    }
}

@Immutable
data class AirMouseConfig(
    val sensitivity: Float = 1.4f,
    val smoothing: Float = 0.65f,
    val deadZone: Float = 0.04f,
    val acceleration: Float = 1.15f,
    val invertX: Boolean = false,
    val invertY: Boolean = false
)
