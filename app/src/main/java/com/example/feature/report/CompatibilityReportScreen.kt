package com.example.feature.report

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TVGripApplication
import com.example.core.model.CapabilityLevel
import com.example.core.model.CapabilitySet
import com.example.core.model.TvDevice
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun CompatibilityReportScreen(
    deviceId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = TVGripApplication.instance
    val devices by app.tvDeviceRepository.allDevices.collectAsState(initial = emptyList())
    val connectedDevice by app.connectionManager.connectedDevice.collectAsState(initial = null)

    val device = devices.firstOrNull { it.id == deviceId } ?: connectedDevice
    val capabilities = device?.capabilities ?: CapabilitySet.DEFAULT_ANDROID_TV

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                title = "Compatibility Report",
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GripCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GripCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = device?.name ?: "Unknown TV",
                                color = GripTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Platform: ${device?.platform ?: "Android TV"} · Host: ${device?.host ?: "192.168.1.x"}",
                                color = GripTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Feature Support Breakdown",
                    color = GripTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FeatureAuditItem("D-Pad & Navigation Keys", capabilities.remoteNavigation, "Real Android TV keycodes over a pinned mutual-TLS session.")
                        FeatureAuditItem("Volume & Mute Control", capabilities.volumeControl, "Volume up/down/mute key injection sent to the TV.")
                        FeatureAuditItem("TV Keyboard Text Sync", capabilities.keyboardInput, "IME batch-edit text injection to the focused TV text field.")
                        FeatureAuditItem("Navigation Touchpad", capabilities.touchpad, "Slide/gyro is translated to real D-pad navigation + select.")
                        FeatureAuditItem("Air Mouse (Gyro)", capabilities.airMouse, "IMU navigation maps to D-pad actions; no absolute pointer stream in the protocol.")
                        FeatureAuditItem("Game Controller", capabilities.gameController, "Button/d-pad key injection; analog sticks map to directional key events.")
                        FeatureAuditItem("Motion Steering", capabilities.motionSteering, "Tilt maps to directional key events for games that accept D-pad steering.")
                        FeatureAuditItem("Power Standby / Wake", capabilities.power, "POWER key injection; actual wake behavior depends on the TV.")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = GripCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Pro Tip for Best Performance", color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "For the most responsive remote, use a 5GHz Wi-Fi band or connect the TV via Ethernet. Actual latency depends on your network and the TV.",
                            color = GripTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun FeatureAuditItem(name: String, level: CapabilityLevel, description: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, color = GripTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            when (level) {
                CapabilityLevel.SUPPORTED -> Text(text = "FULL SUPPORT", color = GripEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                CapabilityLevel.LIMITED -> Text(text = "LIMITED", color = GripOrangeBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                CapabilityLevel.UNSUPPORTED -> Text(text = "UNSUPPORTED", color = GripRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(text = description, color = GripTextTertiary, fontSize = 11.sp)
    }
}
