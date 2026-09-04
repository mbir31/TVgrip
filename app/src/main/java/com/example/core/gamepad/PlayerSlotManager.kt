package com.example.core.gamepad

import android.content.Context
import android.os.Build
import com.example.core.haptics.HapticFeedbackHelper
import com.example.core.model.PlayerSlotInfo
import com.example.core.model.PlayerSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerSlotManager(
    private val context: Context,
    private val haptics: HapticFeedbackHelper
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _activeSlot = MutableStateFlow(PlayerSlot.PLAYER_1)
    val activeSlot: StateFlow<PlayerSlot> = _activeSlot.asStateFlow()

    private val _playerSlots = MutableStateFlow<List<PlayerSlotInfo>>(emptyList())
    val playerSlots: StateFlow<List<PlayerSlotInfo>> = _playerSlots.asStateFlow()

    private val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    init {
        refreshPlayerSlots(_activeSlot.value)
    }

    fun setPlayerSlot(slot: PlayerSlot) {
        _activeSlot.value = slot
        haptics.performHeavyClick()
        refreshPlayerSlots(slot)
    }

    fun testRumble(slot: PlayerSlot = _activeSlot.value) {
        scope.launch {
            // Pulse pattern corresponding to player slot (P1 = 1 pulse, P2 = 2 pulses, P3 = 3 pulses, P4 = 4 pulses)
            repeat(slot.activeLedCount) {
                haptics.performHeavyClick()
                kotlinx.coroutines.delay(120)
            }
        }
    }

    fun cleanupInactivePresets() {
        refreshPlayerSlots(_activeSlot.value)
    }

    private fun refreshPlayerSlots(currentLocalSlot: PlayerSlot) {
        val slots = PlayerSlot.entries.map { slot ->
            if (slot == currentLocalSlot) {
                PlayerSlotInfo(
                    slot = slot,
                    deviceName = "$deviceModel (This Phone)",
                    isLocalDevice = true,
                    isOccupied = true,
                    pingMs = 8L,
                    batteryPercent = 95
                )
            } else {
                // Local preset: only one phone is actually sending input through
                // the Android TV Remote v2 protocol. These are alternate local
                // player-layout presets, not remote phones.
                PlayerSlotInfo(
                    slot = slot,
                    deviceName = "Local Preset (${slot.fullName})",
                    isLocalDevice = true,
                    isOccupied = false,
                    pingMs = 0L,
                    batteryPercent = 100
                )
            }
        }
        _playerSlots.value = slots
    }
}
