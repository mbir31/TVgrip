package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DeviceConnectionState
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrange
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary

@Composable
fun ConnectionBadge(
    connectionState: DeviceConnectionState,
    pingMs: Long = -1L,
    modifier: Modifier = Modifier
) {
    val (dotColor, labelText) = when (connectionState) {
        DeviceConnectionState.CONNECTED -> {
            if (pingMs > 0) {
                GripEmerald to "Connected · $pingMs ms"
            } else {
                GripEmerald to "Connected"
            }
        }
        DeviceConnectionState.CONNECTING -> GripCyan to "Connecting..."
        DeviceConnectionState.PAIRING -> GripOrange to "Pairing..."
        DeviceConnectionState.RECONNECTING -> GripOrange to "Reconnecting..."
        DeviceConnectionState.ERROR -> GripRed to "Connection Error"
        DeviceConnectionState.DISCONNECTED -> GripTextSecondary to "Disconnected"
    }

    val animatedDotColor by animateColorAsState(targetValue = dotColor, label = "dotColor")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GripCardSurface.copy(alpha = 0.85f))
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(animatedDotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                color = GripTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
