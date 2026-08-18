package com.example.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AirMouseConfig
import com.example.core.model.MotionSteeringConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvgrip_settings")

data class AppSettings(
    val nightMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val hapticsIntensity: Float = 1.0f,
    val privateInputMode: Boolean = false,
    val resumeLastModeOnLaunch: Boolean = false,
    val lastUsedMode: String = "HOME",
    val autoReconnect: Boolean = true,
    val batterySaverMode: Boolean = false,
    val compactRemoteDefault: Boolean = false,
    val airMouseConfig: AirMouseConfig = AirMouseConfig(),
    val motionSteeringConfig: MotionSteeringConfig = MotionSteeringConfig(),
    val activeProfileId: String = "preset_classic"
)

class SettingsRepository(private val context: Context) {

    private val KEY_NIGHT_MODE = booleanPreferencesKey("night_mode")
    private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    private val KEY_HAPTICS_INTENSITY = floatPreferencesKey("haptics_intensity")
    private val KEY_PRIVATE_INPUT = booleanPreferencesKey("private_input")
    private val KEY_RESUME_LAST_MODE = booleanPreferencesKey("resume_last_mode")
    private val KEY_LAST_USED_MODE = stringPreferencesKey("last_used_mode")
    private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    private val KEY_BATTERY_SAVER = booleanPreferencesKey("battery_saver")
    private val KEY_COMPACT_REMOTE = booleanPreferencesKey("compact_remote")
    private val KEY_AIR_MOUSE_SENSITIVITY = floatPreferencesKey("air_mouse_sensitivity")
    private val KEY_AIR_MOUSE_SMOOTHING = floatPreferencesKey("air_mouse_smoothing")
    private val KEY_AIR_MOUSE_DEADZONE = floatPreferencesKey("air_mouse_deadzone")
    private val KEY_AIR_MOUSE_ACCEL = floatPreferencesKey("air_mouse_accel")
    private val KEY_AIR_MOUSE_INVERT_X = booleanPreferencesKey("air_mouse_invert_x")
    private val KEY_AIR_MOUSE_INVERT_Y = booleanPreferencesKey("air_mouse_invert_y")
    private val KEY_MOTION_STEERING_SENSITIVITY = floatPreferencesKey("motion_steering_sensitivity")
    private val KEY_MOTION_STEERING_DEADZONE = floatPreferencesKey("motion_steering_deadzone")
    private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            nightMode = prefs[KEY_NIGHT_MODE] ?: false,
            reducedMotion = prefs[KEY_REDUCED_MOTION] ?: false,
            hapticFeedbackEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
            hapticsIntensity = prefs[KEY_HAPTICS_INTENSITY] ?: 1.0f,
            privateInputMode = prefs[KEY_PRIVATE_INPUT] ?: false,
            resumeLastModeOnLaunch = prefs[KEY_RESUME_LAST_MODE] ?: false,
            lastUsedMode = prefs[KEY_LAST_USED_MODE] ?: "HOME",
            autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: true,
            batterySaverMode = prefs[KEY_BATTERY_SAVER] ?: false,
            compactRemoteDefault = prefs[KEY_COMPACT_REMOTE] ?: false,
            airMouseConfig = AirMouseConfig(
                sensitivity = prefs[KEY_AIR_MOUSE_SENSITIVITY] ?: 1.4f,
                smoothing = prefs[KEY_AIR_MOUSE_SMOOTHING] ?: 0.65f,
                deadZone = prefs[KEY_AIR_MOUSE_DEADZONE] ?: 0.04f,
                acceleration = prefs[KEY_AIR_MOUSE_ACCEL] ?: 1.15f,
                invertX = prefs[KEY_AIR_MOUSE_INVERT_X] ?: false,
                invertY = prefs[KEY_AIR_MOUSE_INVERT_Y] ?: false
            ),
            motionSteeringConfig = MotionSteeringConfig(
                sensitivity = prefs[KEY_MOTION_STEERING_SENSITIVITY] ?: 1.2f,
                deadZone = prefs[KEY_MOTION_STEERING_DEADZONE] ?: 0.05f
            ),
            activeProfileId = prefs[KEY_ACTIVE_PROFILE_ID] ?: "preset_classic"
        )
    }

    suspend fun updateNightMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NIGHT_MODE] = enabled }
    }

    suspend fun updateReducedMotion(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REDUCED_MOTION] = enabled }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun updateHapticsIntensity(intensity: Float) {
        context.dataStore.edit { it[KEY_HAPTICS_INTENSITY] = intensity }
    }

    suspend fun updatePrivateInput(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PRIVATE_INPUT] = enabled }
    }

    suspend fun updateResumeLastMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_LAST_MODE] = enabled }
    }

    suspend fun updateLastUsedMode(mode: String) {
        context.dataStore.edit { it[KEY_LAST_USED_MODE] = mode }
    }

    suspend fun updateAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun updateBatterySaver(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BATTERY_SAVER] = enabled }
    }

    suspend fun updateCompactRemote(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COMPACT_REMOTE] = enabled }
    }

    suspend fun updateAirMouseConfig(config: AirMouseConfig) {
        context.dataStore.edit {
            it[KEY_AIR_MOUSE_SENSITIVITY] = config.sensitivity
            it[KEY_AIR_MOUSE_SMOOTHING] = config.smoothing
            it[KEY_AIR_MOUSE_DEADZONE] = config.deadZone
            it[KEY_AIR_MOUSE_ACCEL] = config.acceleration
            it[KEY_AIR_MOUSE_INVERT_X] = config.invertX
            it[KEY_AIR_MOUSE_INVERT_Y] = config.invertY
        }
    }

    suspend fun updateMotionSteeringConfig(config: MotionSteeringConfig) {
        context.dataStore.edit {
            it[KEY_MOTION_STEERING_SENSITIVITY] = config.sensitivity
            it[KEY_MOTION_STEERING_DEADZONE] = config.deadZone
        }
    }

    suspend fun updateActiveProfileId(profileId: String) {
        context.dataStore.edit { it[KEY_ACTIVE_PROFILE_ID] = profileId }
    }
}
