package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TVGripColorScheme = darkColorScheme(
    primary = GripCyan,
    onPrimary = Primary3DText,
    primaryContainer = GripCardElevated,
    onPrimaryContainer = GripCyan,
    secondary = GripOrangeBright,
    onSecondary = Color.Black,
    secondaryContainer = GripCardSurface,
    onSecondaryContainer = GripOrange,
    tertiary = GripEmerald,
    onTertiary = Color.Black,
    background = GripBlack,
    onBackground = GripTextPrimary,
    surface = GripDarkSurface,
    onSurface = GripTextPrimary,
    surfaceVariant = GripCardSurface,
    onSurfaceVariant = GripTextSecondary,
    outline = GripCardBorder,
    outlineVariant = GripDivider,
    error = GripRed,
    onError = Color.White
)

@Composable
fun TVGripTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TVGripColorScheme,
        typography = Typography,
        content = content
    )
}
