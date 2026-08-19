package com.example.feature.pairing

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.bluetooth.BluetoothRemoteState
import com.example.core.model.CapabilityLevel
import com.example.core.model.CapabilitySet
import com.example.core.model.TvDevice
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun PairingWizardScreen(
    onNavigateBack: () -> Unit,
    onPairingComplete: () -> Unit,
    viewModel: PairingViewModel = viewModel(),
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
                title = "Pair a TV",
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "pairing_step_content"
                ) { step ->
                    when (step) {
                        PairingStep.INTRO -> StepIntro(
                            onStartWiFlyScan = { viewModel.startScanning() },
                            onStartBluetoothPairing = { viewModel.startBluetoothPairing() }
                        )
                        PairingStep.SCANNING -> StepScanning()
                        PairingStep.SELECT_DEVICE -> StepSelectDevice(
                            devices = state.discoveredDevices,
                            onSelectDevice = { viewModel.selectDevice(it) },
                            onRescan = { viewModel.startScanning() },
                            onSwitchToBluetooth = { viewModel.startBluetoothPairing() },
                            manualIp = state.manualIp,
                            onManualIpChange = { viewModel.setManualIp(it) },
                            onSubmitManualIp = { viewModel.submitManualIp() },
                            isTestingManualIp = state.isTestingManualIp,
                            errorMessage = state.errorMessage
                        )
                        PairingStep.BLUETOOTH_PAIRING -> StepBluetoothPairing(
                            btState = state.bluetoothState,
                            pairedDevices = state.bluetoothPairedDevices,
                            discoveredDevices = state.bluetoothDiscoveredDevices,
                            onConnectBt = { viewModel.connectBluetoothDevice(it) },
                            onRescanBt = { viewModel.startBluetoothPairing() },
                            onSwitchToWifi = { 
                                viewModel.setConnectionMode(ConnectionMode.WIFI_NETWORK)
                                viewModel.startScanning()
                            }
                        )
                        PairingStep.PAIRING_CODE_INPUT -> StepPairingCode(
                            code = state.pairingCode,
                            onCodeChange = { viewModel.setPairingCode(it) },
                            onSubmit = { viewModel.submitPairingCode() },
                            errorMessage = state.errorMessage
                        )
                        PairingStep.CONNECTING -> StepConnecting(deviceName = state.selectedDevice?.name ?: "TV")
                        PairingStep.TESTING_CAPABILITIES -> StepTestingCapabilities()
                        PairingStep.READY -> StepReady(
                            device = state.selectedDevice,
                            capabilities = state.testedCapabilities,
                            onDone = onPairingComplete
                        )
                        PairingStep.ERROR -> StepError(
                            message = state.errorMessage ?: "Connection failed.",
                            onRetry = { viewModel.retry() }
                        )
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun StepIntro(
    onStartWiFlyScan: () -> Unit,
    onStartBluetoothPairing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(GripCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = GripCyan,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connect Your TV",
            color = GripTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose how you'd like to connect to your Smart TV or Android TV box:",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        TactileButton(
            onClick = onStartWiFlyScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            isPrimary = true,
            icon = Icons.Default.Wifi,
            text = "WI-FI / NETWORK (RECOMMENDED)",
            testTag = "pairing_start_wifi"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TactileButton(
            onClick = onStartBluetoothPairing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            accentColor = GripCyan,
            icon = Icons.Default.Bluetooth,
            text = "BLUETOOTH REMOTE (DIRECT HID)",
            testTag = "pairing_start_bluetooth"
        )
    }
}

@Composable
private fun StepScanning() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GripCyan,
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.5.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Searching Wi-Fi Network...",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Discovering Android TV Remote v2 & Google Cast devices...",
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepBluetoothPairing(
    btState: BluetoothRemoteState,
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    onConnectBt: (BluetoothDevice) -> Unit,
    onRescanBt: () -> Unit,
    onSwitchToWifi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bluetooth TV Remote",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select your TV from paired or nearby Bluetooth devices to connect directly as a standard wireless remote.",
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Paired devices
        if (pairedDevices.isNotEmpty()) {
            Text(
                text = "PAIRED BLUETOOTH DEVICES",
                color = GripCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )

            pairedDevices.forEach { dev ->
                BluetoothDeviceCard(device = dev, onClick = { onConnectBt(dev) })
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Discovered devices
        Text(
            text = if (btState == BluetoothRemoteState.SCANNING) "SCANNING FOR NEARBY BLUETOOTH TVS..." else "AVAILABLE BLUETOOTH DEVICES",
            color = GripOrangeBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )

        if (discoveredDevices.isEmpty() && pairedDevices.isEmpty()) {
            TactileCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Put your TV in Bluetooth Pairing Mode under Settings > Remotes & Accessories > Add Accessory.",
                        color = GripTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TactileButton(
                        onClick = onRescanBt,
                        icon = Icons.Default.BluetoothSearching,
                        text = "SCAN BLUETOOTH",
                        accentColor = GripCyan,
                        testTag = "bt_rescan_button"
                    )
                }
            }
        } else {
            discoveredDevices.forEach { dev ->
                BluetoothDeviceCard(device = dev, onClick = { onConnectBt(dev) })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TactileButton(
            onClick = onSwitchToWifi,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Wifi,
            text = "SWITCH TO WI-FI DISCOVERY",
            testTag = "switch_to_wifi_btn"
        )
    }
}

@android.annotation.SuppressLint("MissingPermission")
@Composable
private fun BluetoothDeviceCard(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    TactileCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GripCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = GripCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Bluetooth TV",
                    color = GripTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    color = GripTextSecondary,
                    fontSize = 12.sp
                )
            }
            TactileButton(
                onClick = onClick,
                text = "PAIR",
                isPrimary = true,
                testTag = "bt_pair_${device.address}"
            )
        }
    }
}

@Composable
private fun StepSelectDevice(
    devices: List<TvDevice>,
    onSelectDevice: (TvDevice) -> Unit,
    onRescan: () -> Unit,
    onSwitchToBluetooth: () -> Unit,
    manualIp: String,
    onManualIpChange: (String) -> Unit,
    onSubmitManualIp: () -> Unit,
    isTestingManualIp: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Discovered Wi-Fi TVs",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Select your TV to connect",
            color = GripTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            TactileCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Wi-Fi TV automatically discovered yet.",
                        color = GripTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileButton(
                            onClick = onRescan,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Refresh,
                            text = "SCAN AGAIN",
                            accentColor = GripOrangeBright,
                            testTag = "pairing_rescan_button"
                        )
                        TactileButton(
                            onClick = onSwitchToBluetooth,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Bluetooth,
                            text = "TRY BLUETOOTH",
                            accentColor = GripCyan,
                            testTag = "pairing_try_bt_button"
                        )
                    }
                }
            }
        } else {
            devices.forEach { device ->
                TactileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    onClick = { onSelectDevice(device) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GripCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GripCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                color = GripTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${device.platform} · ${device.host}",
                                color = GripTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TactileButton(
                            onClick = { onSelectDevice(device) },
                            text = "CONNECT",
                            isPrimary = true,
                            testTag = "connect_device_${device.id}"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual IP Fallback
        TactileCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manual Connection (IP Address)",
                    color = GripTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = onManualIpChange,
                    placeholder = { Text("192.168.1.100", color = GripTextTertiary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmitManualIp() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GripCyan,
                        unfocusedBorderColor = GripCardBorder,
                        focusedTextColor = GripTextPrimary,
                        unfocusedTextColor = GripTextPrimary
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        color = GripRed,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TactileButton(
                    onClick = onSubmitManualIp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTestingManualIp,
                    text = if (isTestingManualIp) "TESTING IP..." else "CONNECT TO IP",
                    isPrimary = true,
                    testTag = "submit_manual_ip_button"
                )
            }
        }
    }
}

@Composable
private fun StepPairingCode(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Confirm Pairing Code",
            color = GripTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter the 6-character code or PIN displayed on your TV screen.",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = { Text("Pairing Code", color = GripTextTertiary) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .testTag("pairing_code_input"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GripCyan,
                unfocusedBorderColor = GripCardBorder,
                focusedTextColor = GripTextPrimary,
                unfocusedTextColor = GripTextPrimary
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = GripRed,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        TactileButton(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            isPrimary = true,
            text = "CONFIRM & CONNECT",
            testTag = "submit_pairing_code"
        )
    }
}

@Composable
private fun StepConnecting(deviceName: String) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GripCyan,
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Connecting to $deviceName...",
            color = GripTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Establishing secure connection profile...",
            color = GripTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StepTestingCapabilities() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GripEmerald,
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Calibrating Features...",
            color = GripTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Testing D-Pad, Volume, Air Mouse, & Haptic telemetry",
            color = GripTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StepReady(
    device: TvDevice?,
    capabilities: CapabilitySet?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GripEmerald.copy(alpha = 0.2f))
                .border(2.dp, GripEmerald, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GripEmerald,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connected Successfully!",
            color = GripTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = device?.name ?: "Your TV is ready to control.",
            color = GripTextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        TactileButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            isPrimary = true,
            accentColor = GripEmerald,
            text = "START CONTROLLING TV",
            testTag = "pairing_ready_done_button"
        )
    }
}

@Composable
private fun StepError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = GripRed,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection Failed",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        TactileButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            text = "TRY AGAIN",
            accentColor = GripOrangeBright,
            testTag = "pairing_error_retry_button"
        )
    }
}
