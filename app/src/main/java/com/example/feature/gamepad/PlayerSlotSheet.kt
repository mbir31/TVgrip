package com.example.feature.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.core.model.PlayerSlotInfo
import com.example.core.model.PlayerSlot
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripDivider
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripPurple
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSlotSheet(
    activeSlot: PlayerSlot,
    playerSlots: List<PlayerSlotInfo>,
    onSelectSlot: (PlayerSlot) -> Unit,
    onTestRumble: (PlayerSlot) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GripBlack,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(activeSlot.colorHex).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = "Player slot",
                            tint = Color(activeSlot.colorHex),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Controller Player Slot",
                            color = GripTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Local button-layout presets (P1 - P4)",
                            color = GripTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("player_slots_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GripTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LOCAL PLAYER PRESETS",
                color = Color(activeSlot.colorHex),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Player Slot Cards
            slots.forEach { slotInfo ->
                val isCurrent = slotInfo.slot == activeSlot
                val slotColor = Color(slotInfo.slot.colorHex)

                TactileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    borderColor = if (isCurrent) slotColor else GripCardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Player LED & Info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Player Badge Circle
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(slotColor.copy(alpha = 0.2f))
                                    .border(2.dp, slotColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = slotInfo.slot.label,
                                    color = slotColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = slotInfo.slot.fullName,
                                        color = GripTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(slotColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "YOU (ACTIVE)",
                                                color = slotColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Port LED dots
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        repeat(slotInfo.slot.activeLedCount) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(slotColor)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = if (isCurrent) "Preset ${slotInfo.slot.slotIndex + 1} • 8ms • ${slotInfo.slot.accentName}" else "Preset ${slotInfo.slot.slotIndex + 1} • Local",
                                        color = GripTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Right: Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Test rumble
                            IconButton(
                                onClick = { onTestRumble(slotInfo.slot) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = "Test Rumble",
                                    tint = if (isCurrent) slotColor else GripTextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (!isCurrent) {
                                TactileButton(
                                    onClick = { onSelectSlot(slotInfo.slot) },
                                    text = "USE",
                                    accentColor = slotColor,
                                    modifier = Modifier.height(34.dp),
                                    testTag = "use_slot_${slotInfo.slot.label.lowercase()}"
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = slotColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Protocol note banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GripCardSurface)
                    .border(1.dp, GripCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "🔧 Protocol note",
                        color = GripTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android TV Remote v2 only exposes key injection. TVGrip sends real D-pad, ABXY, shoulder, trigger and system key events; analog sticks/triggers are mapped to directional/button key events instead of fake analog packets.",
                        color = GripTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
