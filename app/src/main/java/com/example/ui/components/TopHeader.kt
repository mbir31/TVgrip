package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvDevice
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripDivider
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary

@Composable
fun TopHeader(
    title: String = "TVGrip",
    connectedDevice: TvDevice? = null,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onNavigateToDevices: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPairing: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(GripBlack)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBackButton && onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("header_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GripTextPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GripCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "TVGrip",
                            tint = GripCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Text(
                        text = title,
                        color = GripTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    if (connectedDevice != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = connectedDevice.name,
                                color = GripTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (connectedDevice.connectionState == DeviceConnectionState.CONNECTED) {
                                Spacer(modifier = Modifier.width(6.dp))
                                ConnectionBadge(
                                    connectionState = connectedDevice.connectionState,
                                    pingMs = connectedDevice.pingMs
                                )
                            }
                        }
                    }
                }
            }

            // Top-right overflow menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("header_overflow_menu")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = GripTextPrimary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(GripCardElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("My TVs", color = GripTextPrimary, fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToDevices?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("+ Pair New TV", color = GripCyan, fontWeight = FontWeight.Bold) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToPairing?.invoke()
                        }
                    )
                    HorizontalDivider(color = GripDivider)
                    DropdownMenuItem(
                        text = { Text("Settings", color = GripTextPrimary) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToSettings?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Diagnostics", color = GripTextPrimary) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToDiagnostics?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("About TVGrip", color = GripOrangeBright) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToAbout?.invoke()
                        }
                    )
                }
            }
        }
    }
}
