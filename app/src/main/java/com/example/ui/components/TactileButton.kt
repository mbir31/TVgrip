package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TVGripApplication
import com.example.ui.theme.Button3DBottom
import com.example.ui.theme.Button3DPressedBottom
import com.example.ui.theme.Button3DPressedTop
import com.example.ui.theme.Button3DTop
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.Primary3DBottom
import com.example.ui.theme.Primary3DText
import com.example.ui.theme.Primary3DTop

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onPressChanged: ((Boolean) -> Unit)? = null,
    isPrimary: Boolean = false,
    accentColor: Color? = null,
    text: String? = null,
    icon: ImageVector? = null,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    testTag: String = "tactile_button",
    depth: Dp = 4.dp
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptics = remember { runCatching { TVGripApplication.instance.hapticFeedbackHelper }.getOrNull() }

    val pressOffset by animateFloatAsState(
        targetValue = if (isPressed) depth.value else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1000f),
        label = "pressOffset"
    )

    val topColor = when {
        !enabled -> Color(0xFF18202A)
        isPressed && isPrimary -> Primary3DBottom
        isPressed -> Button3DPressedTop
        isPrimary -> Primary3DTop
        accentColor != null -> accentColor.copy(alpha = 0.25f)
        else -> Button3DTop
    }

    val bottomColor = when {
        !enabled -> Color(0xFF0C1016)
        isPressed && isPrimary -> Primary3DBottom.copy(alpha = 0.8f)
        isPressed -> Button3DPressedBottom
        isPrimary -> Primary3DBottom
        accentColor != null -> accentColor.copy(alpha = 0.10f)
        else -> Button3DBottom
    }

    val borderColor = when {
        !enabled -> Color(0xFF151D28)
        isPressed -> accentColor ?: if (isPrimary) Primary3DTop else GripCardBorder.copy(alpha = 0.5f)
        isPrimary -> Primary3DTop
        accentColor != null -> accentColor.copy(alpha = 0.7f)
        else -> GripCardBorder
    }

    val contentColor = when {
        !enabled -> GripTextSecondary.copy(alpha = 0.4f)
        isPrimary -> Primary3DText
        accentColor != null -> accentColor
        else -> GripTextPrimary
    }

    val accessibilityDescription = if (text == null && icon != null) "Button action" else null

    Box(
        modifier = modifier
            .testTag(testTag)
            .semantics {
                role = Role.Button
                if (accessibilityDescription != null) contentDescription = accessibilityDescription
                if (!enabled) disabled()
            }
            .offset { IntOffset(0, pressOffset.toInt()) }
            .shadow(
                elevation = if (isPressed || !enabled) 0.dp else depth,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(topColor, bottomColor))
            )
            .border(
                width = if (isPrimary || accentColor != null) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChanged?.invoke(true)
                        haptics?.performClick()
                        val released = tryAwaitRelease()
                        isPressed = false
                        onPressChanged?.invoke(false)
                    },
                    onTap = {
                        if (enabled) {
                            onClick()
                        }
                    },
                    onLongPress = {
                        if (enabled && onLongClick != null) {
                            haptics?.performHeavyClick()
                            onLongClick()
                        }
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text ?: "Button action",
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (icon != null && text != null) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
