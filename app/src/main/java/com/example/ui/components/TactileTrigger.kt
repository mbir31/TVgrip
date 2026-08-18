package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TVGripApplication
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripTextPrimary

@Composable
fun TactileTrigger(
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isAnalog: Boolean = true,
    testTag: String = "trigger_button"
) {
    val haptics = remember { runCatching { TVGripApplication.instance.hapticFeedbackHelper }.getOrNull() }
    var triggerValue by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(testTag)
            .width(86.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Button3DTop, Button3DBottom)
                )
            )
            .border(
                1.5.dp,
                if (triggerValue > 0.05f) GripOrangeBright else GripCardBorder,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(isAnalog) {
                if (isAnalog) {
                    detectDragGestures(
                        onDragStart = {
                            isPressed = true
                            haptics?.performClick()
                        },
                        onDragEnd = {
                            isPressed = false
                            triggerValue = 0f
                            onValueChange(0f)
                        },
                        onDragCancel = {
                            isPressed = false
                            triggerValue = 0f
                            onValueChange(0f)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount.y / 60f
                            val newVal = (triggerValue + delta).coerceIn(0f, 1f)
                            triggerValue = newVal
                            onValueChange(newVal)
                        }
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            triggerValue = 1.0f
                            onValueChange(1.0f)
                            haptics?.performClick()
                            tryAwaitRelease()
                            isPressed = false
                            triggerValue = 0f
                            onValueChange(0f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Fill gauge for analog pull
        if (triggerValue > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(triggerValue)
                    .align(Alignment.CenterStart)
                    .background(GripOrangeBright.copy(alpha = 0.35f))
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (triggerValue > 0.05f) GripOrangeBright else GripTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            if (isAnalog && triggerValue > 0.05f) {
                Text(
                    text = "${(triggerValue * 100).toInt()}%",
                    color = GripCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
