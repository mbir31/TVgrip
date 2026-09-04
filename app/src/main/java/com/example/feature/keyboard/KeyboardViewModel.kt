package com.example.feature.keyboard

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TVGripApplication
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import com.example.core.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KeyboardUiState(
    val connectedDevice: TvDevice? = null,
    val inputText: String = "",
    val isListeningVoice: Boolean = false,
    val voiceError: String? = null,
    val isMasked: Boolean = false,
    val liveTypingEnabled: Boolean = false,
    val isAirMouseActive: Boolean = false,
    val recentPhrases: List<String> = listOf("Netflix", "YouTube", "Spotify", "Action Movies", "Sci-Fi", "4K HDR")
)

class KeyboardViewModel : ViewModel() {

    private val app = TVGripApplication.instance
    private val connectionManager = app.connectionManager
    private val voiceManager = app.voiceInputManager
    private val haptics = app.hapticFeedbackHelper
    private val airMouseEngine = app.airMouseEngine

    private val _inputText = MutableStateFlow("")
    private val _isMasked = MutableStateFlow(false)
    private val _liveTypingEnabled = MutableStateFlow(false)
    private val _isAirMouseActive = MutableStateFlow(false)

    val uiState: StateFlow<KeyboardUiState> = combine(
        connectionManager.connectedDevice,
        _inputText,
        voiceManager.voiceState,
        _isMasked,
        _liveTypingEnabled,
        _isAirMouseActive
    ) { params: Array<Any?> ->
        val device = params[0] as? TvDevice
        val text = params[1] as String
        val vState = params[2] as VoiceState
        val masked = params[3] as Boolean
        val liveTyping = params[4] as Boolean
        val airMouse = params[5] as Boolean
        val isListening = vState is VoiceState.Listening
        val voiceErr = if (vState is VoiceState.Error) vState.message else null
        KeyboardUiState(
            connectedDevice = device,
            inputText = text,
            isListeningVoice = isListening,
            voiceError = voiceErr,
            isMasked = masked,
            liveTypingEnabled = liveTyping,
            isAirMouseActive = airMouse
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeyboardUiState()
    )

    init {
        viewModelScope.launch {
            voiceManager.partialResult.collect { spoken ->
                if (spoken.isNotEmpty()) {
                    _inputText.value = spoken
                }
            }
        }
    }

    fun onTextChanged(newText: String) {
        val oldText = _inputText.value
        _inputText.value = newText

        if (_liveTypingEnabled.value) {
            if (newText.length > oldText.length) {
                val appended = newText.substring(oldText.length)
                connectionManager.sendCommand(TvCommand.SendText(appended))
            } else if (newText.length < oldText.length) {
                val deletedCount = oldText.length - newText.length
                repeat(deletedCount) {
                    connectionManager.sendCommand(TvCommand.KeyPress(TvKey.BACKSPACE))
                }
            }
        }
    }

    fun sendCurrentText() {
        val text = _inputText.value
        if (text.isNotEmpty()) {
            haptics.performClick()
            connectionManager.sendCommand(TvCommand.SendText(text))
            _inputText.value = ""
        }
    }

    fun sendEnter() {
        haptics.performClick()
        connectionManager.sendCommand(TvCommand.KeyPress(TvKey.ENTER))
    }

    fun sendBackspace() {
        haptics.performClick()
        connectionManager.sendCommand(TvCommand.KeyPress(TvKey.BACKSPACE))
        if (_inputText.value.isNotEmpty()) {
            _inputText.value = _inputText.value.dropLast(1)
        }
    }

    fun sendKey(key: TvKey) {
        haptics.performClick()
        connectionManager.sendCommand(TvCommand.KeyPress(key))
    }

    fun toggleMasked() {
        _isMasked.value = !_isMasked.value
    }

    fun toggleLiveTyping() {
        _liveTypingEnabled.value = !_liveTypingEnabled.value
    }

    fun pasteFromClipboard() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasted = clip.getItemAt(0).text?.toString() ?: ""
            _inputText.value = _inputText.value + pasted
            haptics.performClick()
        }
    }

    fun selectQuickPhrase(phrase: String) {
        _inputText.value = phrase
        sendCurrentText()
    }

    fun toggleVoiceListening() {
        if (voiceManager.voiceState.value is VoiceState.Listening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun toggleAirMouse() {
        val newState = !_isAirMouseActive.value
        _isAirMouseActive.value = newState
        if (newState) {
            airMouseEngine.config = com.example.core.model.AirMouseConfig()
            airMouseEngine.start()
            haptics.performSuccess()
        } else {
            airMouseEngine.stop()
            haptics.performClick()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.stopListening()
        airMouseEngine.stop()
    }
}
