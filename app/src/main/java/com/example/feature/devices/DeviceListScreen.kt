package com.example.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.DeviceConnectionState
import com.example.core.model.TvDevice
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun DeviceListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToReport: (String) -> Unit,
    viewModel: DeviceListViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renamingDevice by remember { mutableStateOf<TvDevice?>(null) }
    var renameText by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                title = "My Paired TVs",
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    TactileButton(
                        onClick = onNavigateToPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        isPrimary = true,
                        icon = Icons.Default.Add,
                        text = "PAIR A NEW TV",
                        testTag = "devices_pair_new_button"
                    )
                }

                if (state.devices.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.TvOff,
                                contentDescription = null,
                                tint = GripTextTertiary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No paired TVs yet",
                                color = GripTextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    items(state.devices, key = { it.id }) { device ->
                        val isConnected = state.connectedDeviceId == device.id

                        TactileCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(if (isConnected) GripCyan.copy(alpha = 0.2f) else GripCardElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isConnected) GripCyan else GripTextSecondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = device.name,
                                                    color = GripTextPrimary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (device.isPreferred) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = "Preferred",
                                                        tint = GripOrangeBright,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${device.platform} · ${device.host}",
                                                color = GripTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // Action buttons
                                    Row {
                                        IconButton(
                                            onClick = {
                                                renamingDevice = device
                                                renameText = device.name
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = GripTextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteDevice(device.id) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = GripRed.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isConnected) {
                                        TactileButton(
                                            onClick = { viewModel.disconnect() },
                                            accentColor = GripRed,
                                            text = "DISCONNECT",
                                            modifier = Modifier.height(40.dp),
                                            testTag = "disconnect_${device.id}"
                                        )
                                    } else {
                                        TactileButton(
                                            onClick = { viewModel.connectDevice(device) },
                                            isPrimary = true,
                                            text = "CONNECT",
                                            modifier = Modifier.height(40.dp),
                                            testTag = "connect_${device.id}"
                                        )
                                    }

                                    Row {
                                        if (!device.isPreferred) {
                                            TactileButton(
                                                onClick = { viewModel.setPreferred(device.id) },
                                                icon = Icons.Default.StarOutline,
                                                text = "FAVORITE",
                                                iconSize = 16.dp,
                                                modifier = Modifier.height(40.dp),
                                                testTag = "favorite_${device.id}"
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        TactileButton(
                                            onClick = { onNavigateToReport(device.id) },
                                            text = "AUDIT",
                                            accentColor = GripOrangeBright,
                                            modifier = Modifier.height(40.dp),
                                            testTag = "audit_${device.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }

        // Rename Dialog
        if (renamingDevice != null) {
            AlertDialog(
                onDismissRequest = { renamingDevice = null },
                containerColor = GripCardElevated,
                title = { Text("Rename TV", color = GripTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GripCyan,
                            unfocusedBorderColor = GripCardBorder,
                            focusedTextColor = GripTextPrimary,
                            unfocusedTextColor = GripTextPrimary
                        )
                    )
                },
                confirmButton = {
                    TactileButton(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameDevice(renamingDevice!!.id, renameText.trim())
                            }
                            renamingDevice = null
                        },
                        text = "SAVE",
                        isPrimary = true
                    )
                },
                dismissButton = {
                    TactileButton(
                        onClick = { renamingDevice = null },
                        text = "CANCEL"
                    )
                }
            )
        }
    }
}
