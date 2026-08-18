package com.example.core.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class TvCommand {
    data class KeyPress(val key: TvKey) : TvCommand()
    data class KeyDown(val key: TvKey) : TvCommand()
    data class KeyUp(val key: TvKey) : TvCommand()
    data class SendText(val text: String) : TvCommand()
    data class TextString(val text: String) : TvCommand()
    data class PointerMove(val dx: Float = 0f, val dy: Float = 0f, val deltaX: Float = dx, val deltaY: Float = dy) : TvCommand()
    data class PointerClick(val isRightClick: Boolean = false, val isLongPress: Boolean = false) : TvCommand()
    data class PointerScroll(val scrollY: Float) : TvCommand()
    data class LaunchApp(val packageName: String) : TvCommand()
    data class GamepadUpdate(val state: GamepadState, val playerSlot: PlayerSlot = state.playerSlot) : TvCommand()
    data object Ping : TvCommand()
}
