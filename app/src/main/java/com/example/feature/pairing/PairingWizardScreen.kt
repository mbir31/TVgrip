package com.example.feature.pairing

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.CapabilitySet
import com.example.core.model.TvDevice
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
                title = "Pair Wi-Fi TV",
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
                            onStartScan = { viewModel.startScanning() }
                        )
                        PairingStep.SCANNING -> StepScanning()
                        PairingStep.SELECT_DEVICE -> StepSelectDevice(
                            devices = state.discoveredDevices,
                            onSelectDevice = { viewModel.selectDevice(it) },
                            onRescan = { viewModel.startScanning() },
                            manualIp = state.manualIp,
                            onManualIpChange = { viewModel.setManualIp(it) },
                            onSubmitManualIp = { viewModel.connectManualIp() },
                            isTestingManualIp = state.isTestingManualIp
                        )
                        PairingStep.PAIRING_CODE_INPUT -> StepPairingCode(
                            prompt = state.pairingPrompt,
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
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(GripCardElevated)
                .border(1.dp, GripCyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = GripCyan,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Connect Your TV",
            color = GripTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Make sure your phone and TV are connected to the same Wi-Fi network. TVGrip will automatically discover your Android TV or Google TV.",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        TactileButton(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("scan_for_tvs_button"),
            isPrimary = true,
            icon = Icons.Default.Wifi,
            text = "Scan for TVs"
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
            strokeWidth = 3.dp,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scanning Wi-Fi Network...",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Searching for Android TV, Google TV, and Cast devices via mDNS",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepSelectDevice(
    devices: List<TvDevice>,
    onSelectDevice: (TvDevice) -> Unit,
    onRescan: () -> Unit,
    manualIp: String,
    onManualIpChange: (String) -> Unit,
    onSubmitManualIp: () -> Unit,
    isTestingManualIp: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Discovered Devices",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (devices.isEmpty()) {
            TactileCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = GripTextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No TVs discovered automatically yet",
                        color = GripTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Ensure the TV is turned on and on the same Wi-Fi, or enter its IP address below.",
                        color = GripTextTertiary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            devices.forEach { device ->
                TactileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onSelectDevice(device) }
                        .testTag("device_card_${device.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = device.name,
                                    color = GripTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${device.manufacturer} • ${device.host}",
                                    color = GripTextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        TactileButton(
                            onClick = { onSelectDevice(device) },
                            modifier = Modifier.height(38.dp),
                            isPrimary = true,
                            text = "Pair"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct IP Entry Card
        TactileCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Connect by TV IP Address",
                    color = GripTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Found in TV Settings → Network → Wi-Fi",
                    color = GripTextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = onManualIpChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_ip_input"),
                        placeholder = { Text("192.168.1.100", color = GripTextTertiary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmitManualIp() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GripCyan,
                            unfocusedBorderColor = GripCardBorder,
                            focusedTextColor = GripTextPrimary,
                            unfocusedTextColor = GripTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    TactileButton(
                        onClick = onSubmitManualIp,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("submit_manual_ip_button"),
                        isPrimary = true,
                        text = if (isTestingManualIp) "..." else "Connect"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TactileButton(
            onClick = onRescan,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("rescan_tvs_button"),
            isPrimary = false,
            icon = Icons.Default.Refresh,
            text = "Rescan Network"
        )
    }
}

@Composable
private fun StepPairingCode(
    prompt: String,
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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GripCardElevated)
                .border(1.dp, GripCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = GripCyan,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter Pairing Code",
            color = GripTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = prompt,
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Visual PIN Box Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val paddedCode = code.padEnd(6, ' ')
            for (i in 0 until 6) {
                val char = paddedCode[i]
                val isCurrent = i == code.length
                val isFilled = i < code.length

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFilled) GripCardElevated else GripBlack)
                        .border(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) GripCyan else if (isFilled) GripCyan.copy(alpha = 0.5f) else GripCardBorder,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (char != ' ') char.toString() else "",
                        color = GripTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hidden / Overlay Input field
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pairing_pin_input"),
            label = { Text("Code (e.g. 4A8B9C or 123456)") },
            placeholder = { Text("Type the 6 characters from your TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage,
                color = GripRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        TactileButton(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_pairing_code_button"),
            isPrimary = true,
            enabled = code.length == 6,
            text = "Confirm Pairing"
        )
    }
}

@Composable
private fun StepConnecting(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GripCyan,
            strokeWidth = 3.dp,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Authenticating with $deviceName...",
            color = GripTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Verifying cryptographic TLS certificate and registering remote token",
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepTestingCapabilities() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = GripEmerald,
            strokeWidth = 3.dp,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Verifying TV Capabilities...",
            color = GripTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Configuring D-pad, media controls, gamepad mode, and keyboard channels",
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
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
                .background(GripEmerald.copy(alpha = 0.15f))
                .border(2.dp, GripEmerald, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GripEmerald,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connected Successfully!",
            color = GripTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${device?.name ?: "TV"} is paired and ready for full control.",
            color = GripTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        TactileButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("launch_remote_button"),
            isPrimary = true,
            text = "Open Remote Controller"
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GripRed.copy(alpha = 0.15f))
                .border(2.dp, GripRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = GripRed,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pairing Failed",
            color = GripTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            color = GripTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        TactileButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("retry_pairing_button"),
            isPrimary = true,
            icon = Icons.Default.Refresh,
            text = "Try Again"
        )
    }
}
