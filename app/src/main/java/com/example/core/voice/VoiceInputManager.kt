package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    data object Idle : VoiceState()
    data object Listening : VoiceState()
    data class Recognized(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class VoiceInputManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Voice input is not available on this device.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceState.Listening
                    _partialResult.value = ""
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during recognition."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Voice recognition error ($error)."
                    }
                    _partialResult.value = ""
                    _voiceState.value = VoiceState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    _voiceState.value = VoiceState.Recognized(text)
                    _partialResult.value = text
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    _partialResult.value = text
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _voiceState.value = VoiceState.Error(e.localizedMessage ?: "Could not start voice recognition.")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (_voiceState.value == VoiceState.Listening) {
            _voiceState.value = VoiceState.Idle
        }
    }

    fun resetState() {
        _voiceState.value = VoiceState.Idle
        _partialResult.value = ""
    }
}
