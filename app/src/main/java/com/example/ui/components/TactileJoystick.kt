package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TVGripApplication
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripTextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun TactileJoystick(
    onStickMove: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    label: String = "LS",
    stickClickLabel: String = "L3",
    onStickClick: (() -> Unit)? = null,
    deadZone: Float = 0.12f,
    sensitivity: Float = 1.0f,
    invertY: Boolean = false,
    testTag: String = "tactile_joystick"
) {
    val haptics = remember { runCatching { TVGripApplication.instance.hapticFeedbackHelper }.getOrNull() }
    val density = LocalDensity.current

    var thumbOffsetX by remember { mutableFloatStateOf(0f) }
    var thumbOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Convert the joystick Dp size to pixels so dragAmount (reported in px) is
    // normalized correctly across screen densities.
    val maxDragDistance = with(density) { size.toPx() } * 0.45f

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .semantics {
                role = Role.Adjustable
                contentDescription = "$label joystick"
            }
            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF101928),
                        Color(0xFF070B12),
                        Color.Black
                    )
                )
            )
            .border(2.dp, if (isDragging) GripCyan else GripCardBorder, CircleShape)
            .pointerInput(deadZone, sensitivity, invertY) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        haptics?.performClick()
                    },
                    onDragEnd = {
                        isDragging = false
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        onStickMove(0f, 0f)
                    },
                    onDragCancel = {
                        isDragging = false
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        onStickMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = thumbOffsetX + dragAmount.x
                        val newY = thumbOffsetY + dragAmount.y
                        val dist = sqrt(newX * newX + newY * newY)

                        val clampedDist = min(dist, maxDragDistance)
                        val angle = atan2(newY, newX)

                        thumbOffsetX = (cos(angle) * clampedDist)
                        thumbOffsetY = (sin(angle) * clampedDist)

                        // Normalize to -1.0 .. 1.0
                        val rawNormalizedX = (thumbOffsetX / maxDragDistance) * sensitivity
                        val rawNormalizedY = (thumbOffsetY / maxDragDistance) * sensitivity

                        val mag = sqrt(rawNormalizedX * rawNormalizedX + rawNormalizedY * rawNormalizedY)
                        val finalX: Float
                        val finalY: Float

                        if (mag < deadZone) {
                            finalX = 0f
                            finalY = 0f
                        } else {
                            val scaledMag = ((mag - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
                            finalX = (rawNormalizedX / mag * scaledMag).coerceIn(-1f, 1f)
                            val yVal = (rawNormalizedY / mag * scaledMag).coerceIn(-1f, 1f)
                            finalY = if (invertY) -yVal else yVal
                        }

                        onStickMove(finalX, finalY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Center crosshair / guide rings
        Box(
            modifier = Modifier
                .size(size * 0.5f)
                .border(1.dp, GripCardBorder.copy(alpha = 0.4f), CircleShape)
        )

        // Thumb Cap with 3D bevel and stick-click detector
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetX.toInt(), thumbOffsetY.toInt()) }
                .size(size * 0.52f)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isDragging) {
                            listOf(Color(0xFF1E3A5F), Color(0xFF0F1E32), Color.Black)
                        } else {
                            listOf(Button3DTop, Button3DBottom, Color.Black)
                        }
                    )
                )
                .border(
                    1.5.dp,
                    if (isDragging) GripCyan else GripCardBorder,
                    CircleShape
                )
                .pointerInput(onStickClick) {
                    if (onStickClick != null) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptics?.performHeavyClick()
                                onStickClick()
                            },
                            onLongPress = {
                                haptics?.performHeavyClick()
                                onStickClick()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isDragging) label else "$label/$stickClickLabel",
                color = if (isDragging) GripCyan else GripTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
