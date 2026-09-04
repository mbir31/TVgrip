package com.example.feature.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.CapabilityLevel
import com.example.core.model.TvKey
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TactileDpad
import com.example.ui.components.TopHeader
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripPurple
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun RemoteScreen(
    onNavigateBack: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: RemoteViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                title = "Remote Control",
                connectedDevice = state.connectedDevice,
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            // Preset Switcher Tabs
            ScrollableTabRow(
                selectedTabIndex = state.activePreset.ordinal,
                containerColor = GripBlack,
                contentColor = GripCyan,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (state.activePreset.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[state.activePreset.ordinal]),
                            color = GripCyan
                        )
                    }
                },
                divider = {}
            ) {
                RemotePreset.entries.forEach { preset ->
                    Tab(
                        selected = state.activePreset == preset,
                        onClick = { viewModel.setPreset(preset) },
                        text = {
                            Text(
                                text = preset.name.replace("_", " "),
                                fontSize = 12.sp,
                                fontWeight = if (state.activePreset == preset) FontWeight.Bold else FontWeight.Medium,
                                color = if (state.activePreset == preset) GripCyan else GripTextSecondary
                            )
                        }
                    )
                }
            }

            // Remote Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.activePreset) {
                    RemotePreset.CLASSIC -> ClassicRemoteView(
                        state = state,
                        onSendKey = { viewModel.sendKey(it) },
                        onSendKeyDown = { viewModel.sendKeyDown(it) },
                        onSendKeyUp = { viewModel.sendKeyUp(it) },
                        onToggleAirMouse = { viewModel.toggleAirMouse() },
                        onCalibrateAirMouse = { viewModel.calibrateAirMouse() },
                        onNavigateToKeyboard = onNavigateToKeyboard
                    )
                    RemotePreset.MINIMAL -> MinimalRemoteView(
                        state = state,
                        onSendKey = { viewModel.sendKey(it) }
                    )
                    RemotePreset.TOUCHPAD -> TouchpadRemoteView(
                        state = state,
                        onSendPointerDelta = { dx, dy -> viewModel.sendPointerDelta(dx, dy) },
                        onSendPointerClick = { viewModel.sendPointerClick(it) },
                        onSendPointerScroll = { viewModel.sendPointerScroll(it) },
                        onSendKey = { viewModel.sendKey(it) },
                        onToggleAirMouse = { viewModel.toggleAirMouse() },
                        onCalibrateAirMouse = { viewModel.calibrateAirMouse() }
                    )
                    RemotePreset.ONE_HANDED -> OneHandedRemoteView(
                        state = state,
                        onSendKey = { viewModel.sendKey(it) }
                    )
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ClassicRemoteView(
    state: RemoteUiState,
    onSendKey: (TvKey) -> Unit,
    onSendKeyDown: (TvKey) -> Unit,
    onSendKeyUp: (TvKey) -> Unit,
    onToggleAirMouse: () -> Unit,
    onCalibrateAirMouse: () -> Unit,
    onNavigateToKeyboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Power, Mute, Source Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileButton(
                onClick = { onSendKey(TvKey.POWER) },
                accentColor = GripRed,
                icon = Icons.Default.PowerSettingsNew,
                iconSize = 20.dp,
                modifier = Modifier.size(52.dp),
                testTag = "remote_power_button"
            )

            TactileButton(
                onClick = onToggleAirMouse,
                accentColor = if (state.isAirMouseActive) GripCyan else null,
                icon = Icons.Default.Mouse,
                iconSize = 20.dp,
                text = if (state.isAirMouseActive) "AIR MOUSE ON" else "AIR MOUSE",
                testTag = "remote_air_mouse_toggle"
            )

            TactileButton(
                onClick = { onSendKey(TvKey.VOLUME_MUTE) },
                accentColor = if (state.isMuted) GripRed else GripOrangeBright,
                icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                iconSize = 20.dp,
                modifier = Modifier.size(52.dp),
                testTag = "remote_mute_button"
            )
        }

        // Air Mouse Active Bar
        AnimatedVisibility(visible = state.isAirMouseActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GripCyan.copy(alpha = 0.15f))
                    .border(1.dp, GripCyan, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Move phone to navigate the TV",
                        color = GripCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TactileButton(
                        onClick = onCalibrateAirMouse,
                        text = "CENTER",
                        accentColor = GripCyan,
                        testTag = "air_mouse_calibrate_button"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // D-Pad
        TactileDpad(
            onDirectionClick = { onSendKey(it) },
            onDirectionPressChanged = { key, isPressed ->
                if (isPressed) onSendKeyDown(key) else onSendKeyUp(key)
            },
            size = 210.dp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Navigation Row: Back, Home, Menu, Keyboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TactileButton(
                onClick = { onSendKey(TvKey.BACK) },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                text = "BACK",
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                testTag = "remote_back_button"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.HOME) },
                icon = Icons.Default.Home,
                text = "HOME",
                isPrimary = true,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                testTag = "remote_home_button"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.MENU) },
                icon = Icons.Default.Settings,
                text = "MENU",
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                testTag = "remote_menu_button"
            )
            TactileButton(
                onClick = onNavigateToKeyboard,
                icon = Icons.Default.Keyboard,
                accentColor = GripOrangeBright,
                modifier = Modifier.size(46.dp),
                testTag = "remote_nav_keyboard"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Volume & Channel Rockers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Volume Rocker
            TactileCard(modifier = Modifier.width(140.dp)) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TactileButton(
                        onClick = { onSendKey(TvKey.VOLUME_UP) },
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        testTag = "remote_vol_up"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "VOL",
                        color = GripTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TactileButton(
                        onClick = { onSendKey(TvKey.VOLUME_DOWN) },
                        icon = Icons.Default.VolumeDown,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        testTag = "remote_vol_down"
                    )
                }
            }

            // Channel Rocker
            TactileCard(modifier = Modifier.width(140.dp)) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TactileButton(
                        onClick = { onSendKey(TvKey.CHANNEL_UP) },
                        text = "CH +",
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        testTag = "remote_ch_up"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "CHANNEL",
                        color = GripTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TactileButton(
                        onClick = { onSendKey(TvKey.CHANNEL_DOWN) },
                        text = "CH -",
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        testTag = "remote_ch_down"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Media Controls: Rewind, Play/Pause, Fast-Forward
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TactileButton(
                onClick = { onSendKey(TvKey.MEDIA_REWIND) },
                icon = Icons.Default.FastRewind,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                testTag = "remote_media_rewind"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.MEDIA_PLAY_PAUSE) },
                icon = Icons.Default.PlayArrow,
                text = "PLAY",
                isPrimary = true,
                modifier = Modifier.weight(1.3f).padding(horizontal = 4.dp),
                testTag = "remote_media_play"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.MEDIA_FAST_FORWARD) },
                icon = Icons.Default.FastForward,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                testTag = "remote_media_ff"
            )
        }
    }
}

@Composable
private fun MinimalRemoteView(
    state: RemoteUiState,
    onSendKey: (TvKey) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TactileButton(
                onClick = { onSendKey(TvKey.POWER) },
                accentColor = GripRed,
                icon = Icons.Default.PowerSettingsNew,
                modifier = Modifier.size(54.dp),
                testTag = "minimal_power"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.VOLUME_MUTE) },
                icon = Icons.AutoMirrored.Filled.VolumeMute,
                modifier = Modifier.size(54.dp),
                testTag = "minimal_mute"
            )
        }

        TactileDpad(
            onDirectionClick = { onSendKey(it) },
            size = 230.dp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TactileButton(
                onClick = { onSendKey(TvKey.BACK) },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                text = "BACK",
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                testTag = "minimal_back"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.HOME) },
                icon = Icons.Default.Home,
                text = "HOME",
                isPrimary = true,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                testTag = "minimal_home"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchpadRemoteView(
    state: RemoteUiState,
    onSendPointerDelta: (Float, Float) -> Unit,
    onSendPointerClick: (Boolean) -> Unit,
    onSendPointerScroll: (Float) -> Unit,
    onSendKey: (TvKey) -> Unit,
    onToggleAirMouse: () -> Unit,
    onCalibrateAirMouse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileButton(
                onClick = onToggleAirMouse,
                accentColor = if (state.isAirMouseActive) GripCyan else null,
                icon = Icons.Default.Mouse,
                text = if (state.isAirMouseActive) "AIR MOUSE ACTIVE" else "AIR MOUSE MODE",
                testTag = "touchpad_air_mouse"
            )
            TactileButton(
                onClick = { onSendKey(TvKey.BACK) },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                text = "BACK",
                testTag = "touchpad_back"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Smooth Touchpad Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("remote_touchpad_surface")
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF0F1826), Color(0xFF070B12), Color.Black)
                    )
                )
                .border(2.dp, GripCardBorder, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onSendPointerDelta(dragAmount.x, dragAmount.y)
                    }
                }
                .combinedClickable(
                    onClick = { onSendPointerClick(false) },
                    onDoubleClick = { onSendPointerClick(false) },
                    onLongClick = { onSendPointerClick(true) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = GripCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Slide to Navigate · Tap to Select",
                    color = GripTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Select / Long-press bar + Home
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TactileButton(
                onClick = { onSendPointerClick(false) },
                text = "SELECT",
                isPrimary = true,
                modifier = Modifier.weight(1.2f).height(54.dp),
                testTag = "touchpad_select"
            )
            Spacer(modifier = Modifier.width(10.dp))
            TactileButton(
                onClick = { onSendKey(TvKey.HOME) },
                icon = Icons.Default.Home,
                modifier = Modifier.size(54.dp),
                testTag = "touchpad_home"
            )
            Spacer(modifier = Modifier.width(10.dp))
            TactileButton(
                onClick = { onSendPointerClick(true) },
                text = "LONG PRESS",
                modifier = Modifier.weight(1.2f).height(54.dp),
                testTag = "touchpad_long_press"
            )
        }
    }
}

@Composable
private fun OneHandedRemoteView(
    state: RemoteUiState,
    onSendKey: (TvKey) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        TactileCard(
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "One-Handed Quick Pad",
                    color = GripTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                TactileDpad(
                    onDirectionClick = onSendKey,
                    size = 180.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TactileButton(onClick = { onSendKey(TvKey.BACK) }, text = "BACK", modifier = Modifier.weight(1f).padding(4.dp))
                    TactileButton(onClick = { onSendKey(TvKey.HOME) }, text = "HOME", isPrimary = true, modifier = Modifier.weight(1f).padding(4.dp))
                    TactileButton(onClick = { onSendKey(TvKey.VOLUME_UP) }, text = "V+", modifier = Modifier.weight(0.8f).padding(4.dp))
                    TactileButton(onClick = { onSendKey(TvKey.VOLUME_DOWN) }, text = "V-", modifier = Modifier.weight(0.8f).padding(4.dp))
                }
            }
        }
    }
}
