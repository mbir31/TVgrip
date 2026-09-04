package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PlayerSlot
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@Composable
fun PlayerSlotSelector(
    activeSlot: PlayerSlot,
    onSelectSlot: (PlayerSlot) -> Unit,
    onOpenPlayerSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(activeSlot.colorHex)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GripCardSurface)
            .border(1.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Player slot buttons
        PlayerSlot.entries.forEach { slot ->
            val isSelected = slot == activeSlot
            val slotColor = Color(slot.colorHex)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) slotColor.copy(alpha = 0.22f) else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSelected) slotColor else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectSlot(slot) }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("player_slot_${slot.label.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Micro LED indicator
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) slotColor else GripTextTertiary)
                    )

                    Text(
                        text = slot.label,
                        color = if (isSelected) slotColor else GripTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        // Player-slot preset launcher icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(activeColor.copy(alpha = 0.15f))
                .clickable { onOpenPlayerSettings() }
                .testTag("open_player_slot_picker"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Player slot presets",
                tint = activeColor,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
