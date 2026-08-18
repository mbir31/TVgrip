package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.DeviceConnectionState
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripPurple
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun HomeScreen(
    onNavigateToRemote: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    onNavigateToGamepad: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            TopHeader(
                title = "TVGrip",
                connectedDevice = state.connectedDevice,
                onNavigateToDevices = onNavigateToDevices,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDiagnostics = onNavigateToDiagnostics,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToPairing = onNavigateToPairing
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TV Status Card
                if (state.connectedDevice != null && state.connectedDevice?.connectionState == DeviceConnectionState.CONNECTED) {
                    ConnectedTvBanner(
                        device = state.connectedDevice!!,
                        onSwitchClick = onNavigateToDevices
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Three Large Primary 3D Tactile Buttons
                    TactileButton(
                        onClick = onNavigateToRemote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                        isPrimary = true,
                        icon = Icons.Default.Tv,
                        iconSize = 28.dp,
                        text = "TV REMOTE",
                        shape = RoundedCornerShape(18.dp),
                        testTag = "home_button_remote"
                    )

                    TactileButton(
                        onClick = onNavigateToKeyboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                        accentColor = GripOrangeBright,
                        icon = Icons.Default.Keyboard,
                        iconSize = 28.dp,
                        text = "TV KEYBOARD",
                        shape = RoundedCornerShape(18.dp),
                        testTag = "home_button_keyboard"
                    )

                    TactileButton(
                        onClick = onNavigateToGamepad,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp),
                        accentColor = GripPurple,
                        icon = Icons.Default.Games,
                        iconSize = 28.dp,
                        text = "GAME CONTROLLER",
                        shape = RoundedCornerShape(18.dp),
                        testTag = "home_button_gamepad"
                    )
                } else {
                    // No TV Connected State
                    NoTvConnectedBanner(
                        isConnecting = state.connectedDevice?.connectionState == DeviceConnectionState.CONNECTING,
                        onPairClick = onNavigateToPairing,
                        onSearchAgain = {
                            viewModel.startDiscovery()
                            onNavigateToPairing()
                        }
                    )
                }
            }

            DeveloperCredit(
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun ConnectedTvBanner(
    device: com.example.core.model.TvDevice,
    onSwitchClick: () -> Unit
) {
    TactileCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSwitchClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.3f), Color(0xFF071220))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Connected TV",
                        tint = GripCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = device.name,
                        color = GripTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ConnectionBadge(
                        connectionState = device.connectionState,
                        pingMs = device.pingMs
                    )
                }
            }
        }
    }
}

@Composable
private fun NoTvConnectedBanner(
    isConnecting: Boolean,
    onPairClick: () -> Unit,
    onSearchAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(GripCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TvOff,
                contentDescription = "No TV Connected",
                tint = GripTextSecondary,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "No TV Connected",
            color = GripTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Connect a compatible Android TV or Google TV to start controlling with TVGrip.",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        TactileButton(
            onClick = onPairClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            isPrimary = true,
            icon = Icons.Default.Tv,
            text = "PAIR A TV",
            shape = RoundedCornerShape(16.dp),
            testTag = "home_button_pair"
        )

        Spacer(modifier = Modifier.height(14.dp))

        TactileButton(
            onClick = onSearchAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            accentColor = GripOrangeBright,
            icon = Icons.Default.Search,
            text = "SEARCH AGAIN",
            shape = RoundedCornerShape(16.dp),
            testTag = "home_button_search"
        )
    }
}
