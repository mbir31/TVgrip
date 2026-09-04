package com.example.feature.gamepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.CapabilityLevel
import com.example.core.model.ControllerButton
import com.example.core.model.ControllerMode
import com.example.core.model.ControllerProfile
import com.example.core.model.DeviceConnectionState
import com.example.core.model.GamepadState
import com.example.core.model.PlayerSlotInfo
import com.example.core.model.PlayerSlot
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import com.example.core.sensors.MotionSteeringEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GamepadUiState(
    val connectedDevice: TvDevice? = null,
    val isConnected: Boolean = false,
    val activePlayerSlot: PlayerSlot = PlayerSlot.PLAYER_1,
    val playerSlots: List<PlayerSlotInfo> = emptyList(),
    val isPlayerSheetOpen: Boolean = false,
    val activeMode: ControllerMode = ControllerMode.STANDARD,
    val activeProfile: ControllerProfile = ControllerProfile.DEFAULT_STANDARD,
    val savedProfiles: List<ControllerProfile> = emptyList(),
    val isLocked: Boolean = false,
    val turboEnabled: Boolean = false,
    val turboActiveButtons: Set<ControllerButton> = emptySet(),
    val steeringAngle: Float = 0f, // -1.0 .. 1.0 for motion steering
    val gasPedal: Float = 0f,
    val brakePedal: Float = 0f,
    val currentGear: Int = 1,
    val latencyMs: Long = 0L
)

class GamepadViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val profileRepository = app.controllerProfileRepository
    private val steeringEngine = app.motionSteeringEngine
    private val playerSlotManager = app.playerSlotManager
    private val haptics = app.hapticFeedbackHelper

    private val _activeMode = MutableStateFlow(ControllerMode.STANDARD)
    private val _activeProfile = MutableStateFlow(ControllerProfile.DEFAULT_STANDARD)
    private val _isLocked = MutableStateFlow(false)
    private val _turboEnabled = MutableStateFlow(false)
    private val _turboActiveButtons = MutableStateFlow<Set<ControllerButton>>(emptySet())
    private val _steeringAngle = MutableStateFlow(0f)
    private val _gasPedal = MutableStateFlow(0f)
    private val _brakePedal = MutableStateFlow(0f)
    private val _currentGear = MutableStateFlow(1)
    private val _isPlayerSheetOpen = MutableStateFlow(false)

    private val currentGamepadState = GamepadState()
    private var turboJob: Job? = null

    val uiState: StateFlow<GamepadUiState> = combine(
        combine(
            connectionManager.connectedDevice,
            playerSlotManager.activeSlot,
            playerSlotManager.playerSlots,
            _isPlayerSheetOpen,
            _activeMode
        ) { dev, slot, pSlots, playerSheetOpen, mode ->
            FiveParams(dev, slot, pSlots, playerSheetOpen, mode)
        },
        combine(
            _activeProfile,
            profileRepository.allProfiles,
            _isLocked,
            _turboEnabled,
            _turboActiveButtons
        ) { profile, saved, locked, turbo, turboButtons ->
            FiveParams2(profile, saved, locked, turbo, turboButtons)
        },
        combine(
            _steeringAngle,
            _gasPedal,
            _brakePedal,
            _currentGear
        ) { steer, gas, brake, gear ->
            FourParams(steer, gas, brake, gear)
        }
    ) { p1, p2, p3 ->
        val dev = p1.dev
        val slot = p1.slot
        val pSlots = p1.pSlots
        val playerSheetOpen = p1.playerSheetOpen
        val mode = p1.mode

        val profile = p2.profile
        val saved = p2.saved
        val locked = p2.locked
        val turbo = p2.turbo
        val turboButtons = p2.turboButtons

        val steer = p3.steer
        val gas = p3.gas
        val brake = p3.brake
        val gear = p3.gear

        GamepadUiState(
            connectedDevice = dev,
            isConnected = dev?.connectionState == DeviceConnectionState.CONNECTED,
            activePlayerSlot = slot,
            playerSlots = pSlots,
            isPlayerSheetOpen = playerSheetOpen,
            activeMode = mode,
            activeProfile = profile,
            savedProfiles = saved,
            isLocked = locked,
            turboEnabled = turbo,
            turboActiveButtons = turboButtons,
            steeringAngle = steer,
            gasPedal = gas,
            brakePedal = brake,
            currentGear = gear,
            latencyMs = dev?.pingMs ?: 0L
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GamepadUiState()
    )

    private data class FiveParams(
        val dev: TvDevice?,
        val slot: PlayerSlot,
        val pSlots: List<PlayerSlotInfo>,
        val playerSheetOpen: Boolean,
        val mode: ControllerMode
    )

    private data class FiveParams2(
        val profile: ControllerProfile,
        val saved: List<ControllerProfile>,
        val locked: Boolean,
        val turbo: Boolean,
        val turboButtons: Set<ControllerButton>
    )

    private data class FourParams(
        val steer: Float,
        val gas: Float,
        val brake: Float,
        val gear: Int
    )

    init {
        // Load initial profile
        viewModelScope.launch {
            val defaultProf = profileRepository.getDefaultProfile()
            if (defaultProf != null) {
                _activeProfile.value = defaultProf
                _activeMode.value = defaultProf.mode
            }
        }

        // Setup motion steering callback
        steeringEngine.onSteeringUpdate = { angle, accel, pitch ->
            _steeringAngle.value = angle
            if (_activeMode.value == ControllerMode.RACING_STEERING) {
                currentGamepadState.leftStickX = angle
                sendGamepadUpdate()
            }
        }
    }

    fun selectPlayerSlot(slot: PlayerSlot) {
        playerSlotManager.setPlayerSlot(slot)
        currentGamepadState.playerSlot = slot
        sendGamepadUpdate()
    }

    fun testRumble(slot: PlayerSlot = playerSlotManager.activeSlot.value) {
        playerSlotManager.testRumble(slot)
    }

    fun openPlayerSheet() {
        _isPlayerSheetOpen.value = true
        haptics.performClick()
    }

    fun closePlayerSheet() {
        _isPlayerSheetOpen.value = false
    }

    private fun sendGamepadUpdate() {
        currentGamepadState.playerSlot = playerSlotManager.activeSlot.value
        connectionManager.sendCommand(
            TvCommand.GamepadUpdate(
                state = currentGamepadState,
                playerSlot = playerSlotManager.activeSlot.value
            )
        )
    }

    fun onButtonDown(button: ControllerButton) {
        if (_isLocked.value) return
        haptics.performClick()

        val mappedKey = _activeProfile.value.mapping[button] ?: button.defaultTvKey
        currentGamepadState.buttons[button] = true
        connectionManager.sendCommand(TvCommand.KeyDown(mappedKey))

        if (_turboEnabled.value) {
            _turboActiveButtons.value = _turboActiveButtons.value + button
            startTurboLoopIfNeeded()
        }
    }

    fun onButtonUp(button: ControllerButton) {
        if (_isLocked.value) return
        val mappedKey = _activeProfile.value.mapping[button] ?: button.defaultTvKey
        currentGamepadState.buttons[button] = false
        connectionManager.sendCommand(TvCommand.KeyUp(mappedKey))

        _turboActiveButtons.value = _turboActiveButtons.value - button
        if (_turboActiveButtons.value.isEmpty()) {
            stopTurboLoop()
        }
    }

    fun onLeftStickMove(x: Float, y: Float) {
        if (_isLocked.value) return
        currentGamepadState.leftStickX = x
        currentGamepadState.leftStickY = y
        sendGamepadUpdate()
    }

    fun onRightStickMove(x: Float, y: Float) {
        if (_isLocked.value) return
        currentGamepadState.rightStickX = x
        currentGamepadState.rightStickY = y
        sendGamepadUpdate()
    }

    fun onTriggerL2Change(value: Float) {
        if (_isLocked.value) return
        currentGamepadState.triggerL2 = value
        sendGamepadUpdate()
    }

    fun onTriggerR2Change(value: Float) {
        if (_isLocked.value) return
        currentGamepadState.triggerR2 = value
        sendGamepadUpdate()
    }

    fun setGasPedal(value: Float) {
        _gasPedal.value = value
        currentGamepadState.triggerR2 = value
        sendGamepadUpdate()
    }

    fun setBrakePedal(value: Float) {
        _brakePedal.value = value
        currentGamepadState.triggerL2 = value
        sendGamepadUpdate()
    }

    fun gearUp() {
        if (_currentGear.value < 6) {
            _currentGear.value += 1
            haptics.performHeavyClick()
            onButtonDown(ControllerButton.R1)
            viewModelScope.launch {
                delay(60)
                onButtonUp(ControllerButton.R1)
            }
        }
    }

    fun gearDown() {
        if (_currentGear.value > -1) { // -1 is Reverse
            _currentGear.value -= 1
            haptics.performHeavyClick()
            onButtonDown(ControllerButton.L1)
            viewModelScope.launch {
                delay(60)
                onButtonUp(ControllerButton.L1)
            }
        }
    }

    fun setControllerMode(mode: ControllerMode) {
        _activeMode.value = mode
        // The engine's config (including user sensitivity) is managed centrally
        // by SettingsRepository in TVGripApplication, so do not overwrite it here.
        if (mode == ControllerMode.RACING_STEERING) {
            steeringEngine.start()
        } else {
            steeringEngine.stop()
        }
    }

    fun selectProfile(profile: ControllerProfile) {
        _activeProfile.value = profile
        setControllerMode(profile.mode)
    }

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
        haptics.performHeavyClick()
    }

    fun toggleTurbo() {
        _turboEnabled.value = !_turboEnabled.value
        haptics.performClick()
        if (!_turboEnabled.value) {
            stopTurboLoop()
            _turboActiveButtons.value = emptySet()
        }
    }

    fun calibrateMotionSteering() {
        steeringEngine.calibrateCenter()
        haptics.performHeavyClick()
    }

    private fun startTurboLoopIfNeeded() {
        if (turboJob == null || turboJob?.isActive == false) {
            val rateMs = (1000L / _activeProfile.value.turboRateHz).coerceIn(20L, 200L)
            turboJob = viewModelScope.launch {
                while (isActive && _turboActiveButtons.value.isNotEmpty()) {
                    for (btn in _turboActiveButtons.value) {
                        val key = _activeProfile.value.mapping[btn] ?: btn.defaultTvKey
                        connectionManager.sendCommand(TvCommand.KeyDown(key))
                    }
                    delay(rateMs / 2)
                    for (btn in _turboActiveButtons.value) {
                        val key = _activeProfile.value.mapping[btn] ?: btn.defaultTvKey
                        connectionManager.sendCommand(TvCommand.KeyUp(key))
                    }
                    delay(rateMs / 2)
                }
            }
        }
    }

    private fun stopTurboLoop() {
        turboJob?.cancel()
        turboJob = null
    }

    private fun releasePressedButtons() {
        currentGamepadState.buttons.filterValues { it }.forEach { (button, _) ->
            val mappedKey = _activeProfile.value.mapping[button] ?: button.defaultTvKey
            connectionManager.sendCommand(TvCommand.KeyUp(mappedKey))
        }
        currentGamepadState.buttons.replaceAll { _, _ -> false }
    }

    /** Stops motion/steering sensor polling when the app leaves the foreground. */
    fun stopForegroundSensors() {
        steeringEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        steeringEngine.stop()
        stopTurboLoop()
        releasePressedButtons()
    }
}
