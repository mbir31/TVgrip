package com.example.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AirMouseConfig
import com.example.core.model.MotionSteeringConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvgrip_settings")

data class AppSettings(
    val hapticFeedbackEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val batterySaverMode: Boolean = false,
    val airMouseConfig: AirMouseConfig = AirMouseConfig(),
    val motionSteeringConfig: MotionSteeringConfig = MotionSteeringConfig()
)

class SettingsRepository(private val context: Context) {

    private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    private val KEY_BATTERY_SAVER = booleanPreferencesKey("battery_saver")
    private val KEY_AIR_MOUSE_SENSITIVITY = floatPreferencesKey("air_mouse_sensitivity")
    private val KEY_AIR_MOUSE_SMOOTHING = floatPreferencesKey("air_mouse_smoothing")
    private val KEY_AIR_MOUSE_DEADZONE = floatPreferencesKey("air_mouse_deadzone")
    private val KEY_AIR_MOUSE_ACCEL = floatPreferencesKey("air_mouse_accel")
    private val KEY_AIR_MOUSE_INVERT_X = booleanPreferencesKey("air_mouse_invert_x")
    private val KEY_AIR_MOUSE_INVERT_Y = booleanPreferencesKey("air_mouse_invert_y")
    private val KEY_MOTION_STEERING_SENSITIVITY = floatPreferencesKey("motion_steering_sensitivity")
    private val KEY_MOTION_STEERING_DEADZONE = floatPreferencesKey("motion_steering_deadzone")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            hapticFeedbackEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
            autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: true,
            batterySaverMode = prefs[KEY_BATTERY_SAVER] ?: false,
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
            )
        )
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun updateAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun updateBatterySaver(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BATTERY_SAVER] = enabled }
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
}
