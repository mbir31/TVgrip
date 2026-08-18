package com.example.core.multiplayer

import android.content.Context
import android.os.Build
import com.example.core.haptics.HapticFeedbackHelper
import com.example.core.model.LobbySlotInfo
import com.example.core.model.PlayerSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiplayerLobbyManager(
    private val context: Context,
    private val haptics: HapticFeedbackHelper
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _activeSlot = MutableStateFlow(PlayerSlot.PLAYER_1)
    val activeSlot: StateFlow<PlayerSlot> = _activeSlot.asStateFlow()

    private val _lobbySlots = MutableStateFlow<List<LobbySlotInfo>>(emptyList())
    val lobbySlots: StateFlow<List<LobbySlotInfo>> = _lobbySlots.asStateFlow()

    private val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    init {
        refreshLobbySlots(_activeSlot.value)
    }

    fun setPlayerSlot(slot: PlayerSlot) {
        _activeSlot.value = slot
        haptics.performHeavyClick()
        refreshLobbySlots(slot)
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

    fun cleanupInactivePeers() {
        refreshLobbySlots(_activeSlot.value)
    }

    private fun refreshLobbySlots(currentLocalSlot: PlayerSlot) {
        val slots = PlayerSlot.entries.map { slot ->
            if (slot == currentLocalSlot) {
                LobbySlotInfo(
                    slot = slot,
                    deviceName = "$deviceModel (This Phone)",
                    isLocalDevice = true,
                    isOccupied = true,
                    pingMs = 8L,
                    batteryPercent = 95
                )
            } else {
                // Available / virtual slot for local multiplayer join
                LobbySlotInfo(
                    slot = slot,
                    deviceName = "Open Slot (${slot.fullName})",
                    isLocalDevice = false,
                    isOccupied = false,
                    pingMs = 0L,
                    batteryPercent = 100
                )
            }
        }
        _lobbySlots.value = slots
    }
}
