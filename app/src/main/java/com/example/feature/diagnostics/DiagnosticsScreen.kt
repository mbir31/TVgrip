package com.example.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel(),
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
                title = "Diagnostics & Audit",
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
                // Real-time Latency Hero Card
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = GripCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Measured Round-Trip Latency (RTT)",
                                color = GripTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (state.currentPingMs > 0) "${state.currentPingMs} ms" else if (state.isConnected) "measuring…" else "---",
                            color = if (state.currentPingMs > 0 && state.currentPingMs <= 25) GripEmerald else GripOrangeBright,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatPill("MIN", if (state.minPingMs > 0) "${state.minPingMs}ms" else "---")
                            StatPill("MAX", if (state.maxPingMs > 0) "${state.maxPingMs}ms" else "---")
                            StatPill("COMMANDS", "${state.packetsSent}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transport Verification Diagnostic Test
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GripEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Real Command Verification",
                                color = GripTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Writes a protocol ping over the active TLS session and checks that the remote socket accepted it. This verifies local transport, not on-TV side effects.",
                            color = GripTextSecondary,
                            fontSize = 12.sp
                        )

                        if (state.lastTestResult != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0C1420))
                                    .border(1.dp, GripCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GripEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.lastTestResult ?: "",
                                        color = GripTextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TactileButton(
                            onClick = { viewModel.runCommandVerificationTest() },
                            isPrimary = true,
                            enabled = !state.isTestingCommand,
                            icon = Icons.Default.NetworkCheck,
                            text = if (state.isTestingCommand) "TRANSMITTING TEST PACKET..." else "TEST TV CONTROL TRANSPORT",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "diagnostics_test_button"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Diagnostic Telemetry Logs
                Text(
                    text = "Live Protocol Telemetry Logs",
                    color = GripTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF060A10))
                        .border(1.dp, GripCardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.recentLogs.takeLast(6).forEach { log ->
                            Text(
                                text = "› $log",
                                color = GripTextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = GripTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = GripTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
