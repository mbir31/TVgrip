package com.example.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.ControllerProfile
import com.example.core.model.ControllerProfileType

@Entity(tableName = "controller_profiles")
data class ControllerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val stickDeadZone: Float,
    val stickSensitivity: Float,
    val triggerDeadZone: Float,
    val triggerSensitivity: Float,
    val invertLeftY: Boolean,
    val invertRightY: Boolean,
    val isHapticsEnabled: Boolean,
    val isTurboEnabled: Boolean,
    val turboIntervalMs: Long,
    val motionSteeringEnabled: Boolean,
    val motionSensitivity: Float
) {
    fun toDomain(): ControllerProfile {
        val profileType = runCatching { ControllerProfileType.valueOf(type) }.getOrDefault(ControllerProfileType.CLASSIC)
        return ControllerProfile(
            id = id,
            name = name,
            type = profileType,
            stickDeadZone = stickDeadZone,
            stickSensitivity = stickSensitivity,
            triggerDeadZone = triggerDeadZone,
            triggerSensitivity = triggerSensitivity,
            invertLeftY = invertLeftY,
            invertRightY = invertRightY,
            isHapticsEnabled = isHapticsEnabled,
            isTurboEnabled = isTurboEnabled,
            turboIntervalMs = turboIntervalMs,
            motionSteeringEnabled = motionSteeringEnabled,
            motionSensitivity = motionSensitivity,
            buttonMappings = ControllerProfile.defaultMappings(profileType)
        )
    }

    companion object {
        fun fromDomain(profile: ControllerProfile): ControllerProfileEntity {
            return ControllerProfileEntity(
                id = profile.id,
                name = profile.name,
                type = profile.type.name,
                stickDeadZone = profile.stickDeadZone,
                stickSensitivity = profile.stickSensitivity,
                triggerDeadZone = profile.triggerDeadZone,
                triggerSensitivity = profile.triggerSensitivity,
                invertLeftY = profile.invertLeftY,
                invertRightY = profile.invertRightY,
                isHapticsEnabled = profile.isHapticsEnabled,
                isTurboEnabled = profile.isTurboEnabled,
                turboIntervalMs = profile.turboIntervalMs,
                motionSteeringEnabled = profile.motionSteeringEnabled,
                motionSensitivity = profile.motionSensitivity
            )
        }
    }
}
