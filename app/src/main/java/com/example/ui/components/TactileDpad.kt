package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TVGripApplication
import com.example.core.model.TvKey
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DPressedBottom
import com.example.ui.theme.Button3DPressedTop
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripTextPrimary

@Composable
fun TactileDpad(
    onDirectionClick: (TvKey) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    onDirectionPressChanged: ((TvKey, Boolean) -> Unit)? = null
) {
    val haptics = remember { runCatching { TVGripApplication.instance.hapticFeedbackHelper }.getOrNull() }

    var activeKey by remember { mutableStateOf<TvKey?>(null) }

    Box(
        modifier = modifier
            .size(size)
            .testTag("tactile_dpad")
            .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        GripCardElevated,
                        Color(0xFF070C14),
                        Color.Black
                    )
                )
            )
            .border(2.dp, GripCardBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Up Button
        DpadSectorButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 12.dp),
            isPressed = activeKey == TvKey.UP,
            testTag = "dpad_up",
            tapEmitsClick = onDirectionPressChanged == null,
            onPress = {
                activeKey = TvKey.UP
                onDirectionPressChanged?.invoke(TvKey.UP, true)
                haptics?.performClick()
            },
            onRelease = {
                activeKey = null
                onDirectionPressChanged?.invoke(TvKey.UP, false)
            },
            onClick = { onDirectionClick(TvKey.UP) }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "DPAD Up",
                tint = if (activeKey == TvKey.UP) GripCyan else GripTextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Down Button
        DpadSectorButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-12).dp),
            isPressed = activeKey == TvKey.DOWN,
            testTag = "dpad_down",
            tapEmitsClick = onDirectionPressChanged == null,
            onPress = {
                activeKey = TvKey.DOWN
                onDirectionPressChanged?.invoke(TvKey.DOWN, true)
                haptics?.performClick()
            },
            onRelease = {
                activeKey = null
                onDirectionPressChanged?.invoke(TvKey.DOWN, false)
            },
            onClick = { onDirectionClick(TvKey.DOWN) }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "DPAD Down",
                tint = if (activeKey == TvKey.DOWN) GripCyan else GripTextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Left Button
        DpadSectorButton(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 12.dp),
            isPressed = activeKey == TvKey.LEFT,
            testTag = "dpad_left",
            tapEmitsClick = onDirectionPressChanged == null,
            onPress = {
                activeKey = TvKey.LEFT
                onDirectionPressChanged?.invoke(TvKey.LEFT, true)
                haptics?.performClick()
            },
            onRelease = {
                activeKey = null
                onDirectionPressChanged?.invoke(TvKey.LEFT, false)
            },
            onClick = { onDirectionClick(TvKey.LEFT) }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "DPAD Left",
                tint = if (activeKey == TvKey.LEFT) GripCyan else GripTextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Right Button
        DpadSectorButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-12).dp),
            isPressed = activeKey == TvKey.RIGHT,
            testTag = "dpad_right",
            tapEmitsClick = onDirectionPressChanged == null,
            onPress = {
                activeKey = TvKey.RIGHT
                onDirectionPressChanged?.invoke(TvKey.RIGHT, true)
                haptics?.performClick()
            },
            onRelease = {
                activeKey = null
                onDirectionPressChanged?.invoke(TvKey.RIGHT, false)
            },
            onClick = { onDirectionClick(TvKey.RIGHT) }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "DPAD Right",
                tint = if (activeKey == TvKey.RIGHT) GripCyan else GripTextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Center OK / Select Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .testTag("dpad_center")
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (activeKey == TvKey.CENTER) {
                            listOf(GripCyan, Color(0xFF00838F))
                        } else {
                            listOf(Button3DTop, Button3DBottom)
                        }
                    )
                )
                .border(
                    2.dp,
                    if (activeKey == TvKey.CENTER) GripCyan else GripOrangeBright.copy(alpha = 0.8f),
                    CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            activeKey = TvKey.CENTER
                            onDirectionPressChanged?.invoke(TvKey.CENTER, true)
                            haptics?.performHeavyClick()
                            tryAwaitRelease()
                            activeKey = null
                            onDirectionPressChanged?.invoke(TvKey.CENTER, false)
                        },
                        onTap = {
                            if (onDirectionPressChanged == null) {
                                onDirectionClick(TvKey.CENTER)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                color = if (activeKey == TvKey.CENTER) Color.Black else GripTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun DpadSectorButton(
    modifier: Modifier = Modifier,
    isPressed: Boolean,
    testTag: String,
    tapEmitsClick: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .testTag(testTag)
            .clip(CircleShape)
            .background(
                if (isPressed) {
                    Brush.verticalGradient(listOf(Button3DPressedTop, Button3DPressedBottom))
                } else {
                    Brush.verticalGradient(listOf(Button3DTop.copy(alpha = 0.5f), Button3DBottom.copy(alpha = 0.5f)))
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                    onTap = {
                        // When press tracking is enabled a press/release already
                        // performs the key. Emitting an additional SHORT click on
                        // the same tap would double-activate the key.
                        if (tapEmitsClick) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
