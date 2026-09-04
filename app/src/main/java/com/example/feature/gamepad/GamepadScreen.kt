package com.example.feature.gamepad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.ButtonLayoutType
import com.example.core.model.ControllerButton
import com.example.core.model.ControllerMode
import com.example.core.model.ControllerProfile
import com.example.core.model.PlayerSlot
import com.example.core.model.TvKey
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.PlayerSlotSelector
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TactileDpad
import com.example.ui.components.TactileJoystick
import com.example.ui.components.TactileTrigger
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DPressedBottom
import com.example.ui.theme.Button3DPressedTop
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripDivider
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrange
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripPurple
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary
import com.example.ui.theme.Primary3DBottom
import com.example.ui.theme.Primary3DTop

@Composable
fun GamepadScreen(
    onNavigateBack: () -> Unit,
    viewModel: GamepadViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopForegroundSensors()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top HUD Bar
            GamepadHudBar(
                state = state,
                onNavigateBack = onNavigateBack,
                onSelectSlot = { viewModel.selectPlayerSlot(it) },
                onOpenPlayerSettings = { viewModel.openPlayerSheet() },
                onToggleLock = { viewModel.toggleLock() },
                onToggleTurbo = { viewModel.toggleTurbo() },
                onOpenProfiles = { profileMenuExpanded = true },
                onSetMode = { viewModel.setControllerMode(it) }
            )

            // Gamepad Controller Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when (state.activeMode) {
                    ControllerMode.STANDARD -> StandardGamepadLayout(
                        state = state,
                        onButtonDown = { viewModel.onButtonDown(it) },
                        onButtonUp = { viewModel.onButtonUp(it) },
                        onLeftStickMove = { x, y -> viewModel.onLeftStickMove(x, y) },
                        onRightStickMove = { x, y -> viewModel.onRightStickMove(x, y) },
                        onTriggerL2Change = { viewModel.onTriggerL2Change(it) },
                        onTriggerR2Change = { viewModel.onTriggerR2Change(it) }
                    )
                    ControllerMode.RACING_STEERING -> RacingSteeringLayout(
                        state = state,
                        onSetGas = { viewModel.setGasPedal(it) },
                        onSetBrake = { viewModel.setBrakePedal(it) },
                        onGearUp = { viewModel.gearUp() },
                        onGearDown = { viewModel.gearDown() },
                        onCalibrate = { viewModel.calibrateMotionSteering() },
                        onButtonDown = { viewModel.onButtonDown(it) },
                        onButtonUp = { viewModel.onButtonUp(it) }
                    )
                    ControllerMode.RETRO_DPAD -> RetroGamepadLayout(
                        state = state,
                        onButtonDown = { viewModel.onButtonDown(it) },
                        onButtonUp = { viewModel.onButtonUp(it) }
                    )
                    ControllerMode.CUSTOM -> StandardGamepadLayout(
                        state = state,
                        onButtonDown = { viewModel.onButtonDown(it) },
                        onButtonUp = { viewModel.onButtonUp(it) },
                        onLeftStickMove = { x, y -> viewModel.onLeftStickMove(x, y) },
                        onRightStickMove = { x, y -> viewModel.onRightStickMove(x, y) },
                        onTriggerL2Change = { viewModel.onTriggerL2Change(it) },
                        onTriggerR2Change = { viewModel.onTriggerR2Change(it) }
                    )
                }

                // Profile Dropdown
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false },
                    modifier = Modifier.background(GripCardElevated)
                ) {
                    Text(
                        text = "Controller Profiles",
                        color = Color(state.activePlayerSlot.colorHex),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = GripDivider)
                    state.savedProfiles.forEach { prof ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = prof.name,
                                    color = if (prof.id == state.activeProfile.id) Color(state.activePlayerSlot.colorHex) else GripTextPrimary,
                                    fontWeight = if (prof.id == state.activeProfile.id) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                viewModel.selectProfile(prof)
                                profileMenuExpanded = false
                            }
                        )
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }

        // Local player-slot / button-layout preset sheet
        if (state.isPlayerSheetOpen) {
            PlayerSlotSheet(
                activeSlot = state.activePlayerSlot,
                playerSlots = state.playerSlots,
                onSelectSlot = { viewModel.selectPlayerSlot(it) },
                onTestRumble = { viewModel.testRumble(it) },
                onDismiss = { viewModel.closePlayerSheet() }
            )
        }
    }
}

@Composable
private fun GamepadHudBar(
    state: GamepadUiState,
    onNavigateBack: () -> Unit,
    onSelectSlot: (PlayerSlot) -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleTurbo: () -> Unit,
    onOpenProfiles: () -> Unit,
    onSetMode: (ControllerMode) -> Unit
) {
    val playerColor = Color(state.activePlayerSlot.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GripBlack)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("gamepad_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GripTextPrimary
                )
            }

            // Mode Selector Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(GripCardSurface)
                    .border(1.dp, GripCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControllerMode.entries.forEach { mode ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (state.activeMode == mode) playerColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onSetMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    ControllerMode.STANDARD -> "PAD"
                                    ControllerMode.RACING_STEERING -> "RACE"
                                    ControllerMode.RETRO_DPAD -> "RETRO"
                                    ControllerMode.CUSTOM -> "CUSTOM"
                                },
                                color = if (state.activeMode == mode) playerColor else GripTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Center: Player 1-4 Slot Selector Bar
        PlayerSlotSelector(
            activeSlot = state.activePlayerSlot,
            onSelectSlot = onSelectSlot,
            onOpenPlayerSettings = onOpenPlayerSettings
        )

        // Right Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Turbo Button
            TactileButton(
                onClick = onToggleTurbo,
                accentColor = if (state.turboEnabled) GripEmerald else null,
                icon = Icons.Default.ElectricBolt,
                iconSize = 14.dp,
                text = "TURBO",
                modifier = Modifier.height(32.dp),
                testTag = "gamepad_turbo_toggle"
            )

            // Lock Controller Button
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.size(34.dp).testTag("gamepad_lock_toggle")
            ) {
                Icon(
                    imageVector = if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock Gamepad",
                    tint = if (state.isLocked) GripRed else GripTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Profiles Button
            IconButton(
                onClick = onOpenProfiles,
                modifier = Modifier.size(34.dp).testTag("gamepad_profiles_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Profiles",
                    tint = GripTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StandardGamepadLayout(
    state: GamepadUiState,
    onButtonDown: (ControllerButton) -> Unit,
    onButtonUp: (ControllerButton) -> Unit,
    onLeftStickMove: (Float, Float) -> Unit,
    onRightStickMove: (Float, Float) -> Unit,
    onTriggerL2Change: (Float) -> Unit,
    onTriggerR2Change: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Shoulder Triggers Row (L2, L1, Center utilities, R1, R2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TactileTrigger(
                    label = "L2",
                    onValueChange = onTriggerL2Change,
                    testTag = "button_trigger_l2"
                )
                GamepadHoldButton(
                    label = "L1",
                    onDown = { onButtonDown(ControllerButton.L1) },
                    onUp = { onButtonUp(ControllerButton.L1) },
                    width = 64.dp,
                    height = 44.dp,
                    testTag = "button_bumper_l1"
                )
            }

            // Center Menu / Select / Start / Home buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadHoldButton(
                    label = "SEL",
                    onDown = { onButtonDown(ControllerButton.SELECT) },
                    onUp = { onButtonUp(ControllerButton.SELECT) },
                    width = 44.dp,
                    height = 36.dp,
                    testTag = "button_select"
                )
                GamepadHoldButton(
                    label = "HOME",
                    onDown = { onButtonDown(ControllerButton.HOME) },
                    onUp = { onButtonUp(ControllerButton.HOME) },
                    width = 52.dp,
                    height = 36.dp,
                    accentColor = Color(state.activePlayerSlot.colorHex),
                    testTag = "button_gamepad_home"
                )
                GamepadHoldButton(
                    label = "START",
                    onDown = { onButtonDown(ControllerButton.START) },
                    onUp = { onButtonUp(ControllerButton.START) },
                    width = 50.dp,
                    height = 36.dp,
                    testTag = "button_start"
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GamepadHoldButton(
                    label = "R1",
                    onDown = { onButtonDown(ControllerButton.R1) },
                    onUp = { onButtonUp(ControllerButton.R1) },
                    width = 64.dp,
                    height = 44.dp,
                    testTag = "button_bumper_r1"
                )
                TactileTrigger(
                    label = "R2",
                    onValueChange = onTriggerR2Change,
                    testTag = "button_trigger_r2"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Lower Controller Area (Left Stick + D-Pad on Left; Right Stick + ABXY on Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT CONTROL CLUSTER (Left Stick + D-Pad)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                TactileJoystick(
                    onStickMove = onLeftStickMove,
                    size = 142.dp,
                    label = "LS",
                    stickClickLabel = "L3",
                    onStickClick = {
                        onButtonDown(ControllerButton.L3)
                        onButtonUp(ControllerButton.L3)
                    },
                    testTag = "left_joystick"
                )

                TactileDpad(
                    onDirectionClick = {},
                    onDirectionPressChanged = { key, isDown ->
                        val btn = when (key) {
                            TvKey.UP -> ControllerButton.DPAD_UP
                            TvKey.DOWN -> ControllerButton.DPAD_DOWN
                            TvKey.LEFT -> ControllerButton.DPAD_LEFT
                            TvKey.RIGHT -> ControllerButton.DPAD_RIGHT
                            else -> ControllerButton.A
                        }
                        if (isDown) onButtonDown(btn) else onButtonUp(btn)
                    },
                    size = 150.dp
                )
            }

            // RIGHT CONTROL CLUSTER (ABXY Action Diamond + Right Stick)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // ABXY Diamond
                AbxyActionDiamond(
                    layoutType = state.activeProfile.buttonLayout,
                    onButtonDown = onButtonDown,
                    onButtonUp = onButtonUp
                )

                TactileJoystick(
                    onStickMove = onRightStickMove,
                    size = 142.dp,
                    label = "RS",
                    stickClickLabel = "R3",
                    onStickClick = {
                        onButtonDown(ControllerButton.R3)
                        onButtonUp(ControllerButton.R3)
                    },
                    testTag = "right_joystick"
                )
            }
        }
    }
}

@Composable
private fun AbxyActionDiamond(
    layoutType: ButtonLayoutType,
    onButtonDown: (ControllerButton) -> Unit,
    onButtonUp: (ControllerButton) -> Unit
) {
    val topLabel = if (layoutType == ButtonLayoutType.PLAYSTATION) "△" else "Y"
    val bottomLabel = if (layoutType == ButtonLayoutType.PLAYSTATION) "✕" else "A"
    val leftLabel = if (layoutType == ButtonLayoutType.PLAYSTATION) "□" else "X"
    val rightLabel = if (layoutType == ButtonLayoutType.PLAYSTATION) "◯" else "B"

    val topColor = if (layoutType == ButtonLayoutType.PLAYSTATION) GripEmerald else GripOrangeBright
    val bottomColor = if (layoutType == ButtonLayoutType.PLAYSTATION) GripCyan else GripEmerald
    val leftColor = if (layoutType == ButtonLayoutType.PLAYSTATION) GripPurple else GripCyan
    val rightColor = if (layoutType == ButtonLayoutType.PLAYSTATION) GripRed else GripRed

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(Color(0xFF070B12).copy(alpha = 0.5f))
            .border(1.dp, GripCardBorder.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Y / Triangle (Top)
        GamepadRoundButton(
            label = topLabel,
            accentColor = topColor,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 6.dp),
            onDown = { onButtonDown(ControllerButton.Y) },
            onUp = { onButtonUp(ControllerButton.Y) },
            testTag = "button_face_y"
        )

        // A / Cross (Bottom)
        GamepadRoundButton(
            label = bottomLabel,
            accentColor = bottomColor,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-6).dp),
            onDown = { onButtonDown(ControllerButton.A) },
            onUp = { onButtonUp(ControllerButton.A) },
            testTag = "button_face_a"
        )

        // X / Square (Left)
        GamepadRoundButton(
            label = leftLabel,
            accentColor = leftColor,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 6.dp),
            onDown = { onButtonDown(ControllerButton.X) },
            onUp = { onButtonUp(ControllerButton.X) },
            testTag = "button_face_x"
        )

        // B / Circle (Right)
        GamepadRoundButton(
            label = rightLabel,
            accentColor = rightColor,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-6).dp),
            onDown = { onButtonDown(ControllerButton.B) },
            onUp = { onButtonUp(ControllerButton.B) },
            testTag = "button_face_b"
        )
    }
}

@Composable
private fun GamepadRoundButton(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    onDown: () -> Unit,
    onUp: () -> Unit,
    testTag: String
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(testTag)
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (isPressed) {
                    Brush.radialGradient(listOf(accentColor, Color.Black))
                } else {
                    Brush.verticalGradient(listOf(Button3DTop, Button3DBottom))
                }
            )
            .border(
                1.5.dp,
                if (isPressed) accentColor else accentColor.copy(alpha = 0.7f),
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onDown()
                        tryAwaitRelease()
                        isPressed = false
                        onUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun GamepadHoldButton(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 58.dp,
    height: Dp = 40.dp,
    accentColor: Color? = null,
    onDown: () -> Unit,
    onUp: () -> Unit,
    testTag: String
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(testTag)
            .size(width, height)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPressed) {
                    Brush.verticalGradient(listOf(Button3DPressedTop, Button3DPressedBottom))
                } else {
                    Brush.verticalGradient(listOf(Button3DTop, Button3DBottom))
                }
            )
            .border(
                1.dp,
                if (isPressed) (accentColor ?: GripCyan) else GripCardBorder,
                RoundedCornerShape(10.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onDown()
                        tryAwaitRelease()
                        isPressed = false
                        onUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) (accentColor ?: GripCyan) else GripTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RacingSteeringLayout(
    state: GamepadUiState,
    onSetGas: (Float) -> Unit,
    onSetBrake: (Float) -> Unit,
    onGearUp: () -> Unit,
    onGearDown: () -> Unit,
    onCalibrate: () -> Unit,
    onButtonDown: (ControllerButton) -> Unit,
    onButtonUp: (ControllerButton) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Brake Pedal & Downshift & Handbrake
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GamepadHoldButton(
                label = "GEAR -",
                onDown = onGearDown,
                onUp = {},
                width = 90.dp,
                height = 48.dp,
                accentColor = GripOrangeBright,
                testTag = "racing_gear_down"
            )

            TactileTrigger(
                label = "BRAKE",
                onValueChange = onSetBrake,
                modifier = Modifier.width(100.dp).height(120.dp),
                testTag = "racing_brake_pedal"
            )

            GamepadHoldButton(
                label = "HANDBRAKE (A)",
                onDown = { onButtonDown(ControllerButton.A) },
                onUp = { onButtonUp(ControllerButton.A) },
                width = 110.dp,
                height = 46.dp,
                accentColor = GripRed,
                testTag = "racing_handbrake"
            )
        }

        // Center: Steering Wheel Angle Dial & Gear Readout
        Column(
            modifier = Modifier.weight(1.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF070E1A))
                    .border(2.dp, GripCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Wheel rotation indicator needle
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .rotate(state.steeringAngle * 45f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopCenter)
                            .clip(CircleShape)
                            .background(GripOrangeBright)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.currentGear == -1) "R" else if (state.currentGear == 0) "N" else "GEAR ${state.currentGear}",
                        color = GripCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "TILT STEERING",
                        color = GripTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TactileButton(
                onClick = onCalibrate,
                text = "CENTER TILT",
                accentColor = GripCyan,
                modifier = Modifier.height(38.dp),
                testTag = "racing_center_tilt"
            )
        }

        // Right Side: Gas Pedal & Upshift & Boost / Nitrous
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GamepadHoldButton(
                label = "GEAR +",
                onDown = onGearUp,
                onUp = {},
                width = 90.dp,
                height = 48.dp,
                accentColor = GripEmerald,
                testTag = "racing_gear_up"
            )

            TactileTrigger(
                label = "GAS ACCEL",
                onValueChange = onSetGas,
                modifier = Modifier.width(100.dp).height(120.dp),
                testTag = "racing_gas_pedal"
            )

            GamepadHoldButton(
                label = "NITROUS (X)",
                onDown = { onButtonDown(ControllerButton.X) },
                onUp = { onButtonUp(ControllerButton.X) },
                width = 110.dp,
                height = 46.dp,
                accentColor = GripCyan,
                testTag = "racing_nitrous"
            )
        }
    }
}

@Composable
private fun RetroGamepadLayout(
    state: GamepadUiState,
    onButtonDown: (ControllerButton) -> Unit,
    onButtonUp: (ControllerButton) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large D-Pad
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TactileDpad(
                onDirectionClick = {},
                onDirectionPressChanged = { key, isDown ->
                    val btn = when (key) {
                        TvKey.UP -> ControllerButton.DPAD_UP
                        TvKey.DOWN -> ControllerButton.DPAD_DOWN
                        TvKey.LEFT -> ControllerButton.DPAD_LEFT
                        TvKey.RIGHT -> ControllerButton.DPAD_RIGHT
                        else -> ControllerButton.A
                    }
                    if (isDown) onButtonDown(btn) else onButtonUp(btn)
                },
                size = 190.dp
            )
        }

        // Center Select & Start
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GamepadHoldButton(
                label = "SELECT",
                onDown = { onButtonDown(ControllerButton.SELECT) },
                onUp = { onButtonUp(ControllerButton.SELECT) },
                width = 64.dp,
                height = 36.dp,
                testTag = "retro_select"
            )
            GamepadHoldButton(
                label = "START",
                onDown = { onButtonDown(ControllerButton.START) },
                onUp = { onButtonUp(ControllerButton.START) },
                width = 64.dp,
                height = 36.dp,
                accentColor = GripOrangeBright,
                testTag = "retro_start"
            )
        }

        // Large 2-4 Face Buttons (B & A)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GamepadRoundButton(
                    label = "Y",
                    accentColor = GripOrangeBright,
                    size = 54.dp,
                    onDown = { onButtonDown(ControllerButton.Y) },
                    onUp = { onButtonUp(ControllerButton.Y) },
                    testTag = "retro_y"
                )
                GamepadRoundButton(
                    label = "X",
                    accentColor = GripCyan,
                    size = 54.dp,
                    onDown = { onButtonDown(ControllerButton.X) },
                    onUp = { onButtonUp(ControllerButton.X) },
                    testTag = "retro_x"
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GamepadRoundButton(
                    label = "B",
                    accentColor = GripRed,
                    size = 54.dp,
                    onDown = { onButtonDown(ControllerButton.B) },
                    onUp = { onButtonUp(ControllerButton.B) },
                    testTag = "retro_b"
                )
                GamepadRoundButton(
                    label = "A",
                    accentColor = GripEmerald,
                    size = 54.dp,
                    onDown = { onButtonDown(ControllerButton.A) },
                    onUp = { onButtonUp(ControllerButton.A) },
                    testTag = "retro_a"
                )
            }
        }
    }
}
