package com.example.feature.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                title = "Settings",
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // General Settings
                Text(text = "Feedback & Connection", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Vibration, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Haptic Tactile Feedback", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "3D physical vibrations on key presses", color = GripTextTertiary, fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = settings.hapticFeedbackEnabled,
                                onCheckedChange = { viewModel.updateHapticFeedback(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GripCyan, checkedTrackColor = GripCyan.copy(alpha = 0.3f)),
                                modifier = Modifier.testTag("settings_switch_haptics")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Auto-Reconnect to Preferred TV", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "Reconnects to the last paired TV when the app opens", color = GripTextTertiary, fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = settings.autoReconnect,
                                onCheckedChange = { viewModel.updateAutoReconnect(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GripCyan, checkedTrackColor = GripCyan.copy(alpha = 0.3f)),
                                modifier = Modifier.testTag("settings_switch_reconnect")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Battery Saver Low-Power Polling", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "Throttles background sensor polling", color = GripTextTertiary, fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = settings.batterySaverMode,
                                onCheckedChange = { viewModel.updateBatterySaver(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GripCyan, checkedTrackColor = GripCyan.copy(alpha = 0.3f)),
                                modifier = Modifier.testTag("settings_switch_battery")
                            )
                        }
                    }
                }

                // Sensor Tuning
                Text(text = "Sensor Calibration & Sensitivity", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Mouse, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Air Mouse Navigation Sensitivity", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = settings.airMouseConfig.sensitivity,
                            onValueChange = { viewModel.updateAirMouseSensitivity(it) },
                            valueRange = 0.5f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = GripCyan, activeTrackColor = GripCyan),
                            modifier = Modifier.testTag("slider_air_mouse")
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Slow (0.5x)", color = GripTextTertiary, fontSize = 11.sp)
                            Text(text = "${String.format("%.1f", settings.airMouseConfig.sensitivity)}x", color = GripCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Fast (3.0x)", color = GripTextTertiary, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Games, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Racing Motion Steering Sensitivity", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = settings.motionSteeringConfig.sensitivity,
                            onValueChange = { viewModel.updateMotionSteeringSensitivity(it) },
                            valueRange = 0.5f..2.5f,
                            colors = SliderDefaults.colors(thumbColor = GripCyan, activeTrackColor = GripCyan),
                            modifier = Modifier.testTag("slider_steering")
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Smooth (0.5x)", color = GripTextTertiary, fontSize = 11.sp)
                            Text(text = "${String.format("%.1f", settings.motionSteeringConfig.sensitivity)}x", color = GripCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Agile (2.5x)", color = GripTextTertiary, fontSize = 11.sp)
                        }
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}
