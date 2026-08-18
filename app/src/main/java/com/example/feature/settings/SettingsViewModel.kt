package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.data.repository.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val settingsRepository = app.settingsRepository
    private val haptics = app.hapticFeedbackHelper

    val settingsState: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun updateHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHapticFeedback(enabled)
            if (enabled) haptics.performClick()
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoReconnect(enabled)
        }
    }

    fun updateBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBatterySaver(enabled)
        }
    }

    fun updateAirMouseSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            val current = settingsState.value.airMouseConfig
            settingsRepository.updateAirMouseConfig(current.copy(sensitivity = sensitivity))
        }
    }

    fun updateMotionSteeringSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            val current = settingsState.value.motionSteeringConfig
            settingsRepository.updateMotionSteeringConfig(current.copy(sensitivity = sensitivity))
        }
    }
}
