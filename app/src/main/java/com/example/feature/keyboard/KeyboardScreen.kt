package com.example.feature.keyboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.TvKey
import com.example.ui.components.DeveloperCredit
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TopHeader
import com.example.ui.theme.GripBlack
import com.example.ui.theme.GripCardBorder
import com.example.ui.theme.GripCardElevated
import com.example.ui.theme.GripCardSurface
import com.example.ui.theme.GripCyan
import com.example.ui.theme.GripEmerald
import com.example.ui.theme.GripOrangeBright
import com.example.ui.theme.GripRed
import com.example.ui.theme.GripTextPrimary
import com.example.ui.theme.GripTextSecondary
import com.example.ui.theme.GripTextTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopForegroundSensors()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleVoiceListening()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GripBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                title = "TV Keyboard",
                connectedDevice = state.connectedDevice,
                showBackButton = true,
                onBackClick = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Input Field Card
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Text to Send",
                                color = GripTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row {
                                // Live Typing Toggle
                                TactileButton(
                                    onClick = { viewModel.toggleLiveTyping() },
                                    accentColor = if (state.liveTypingEnabled) GripEmerald else null,
                                    text = if (state.liveTypingEnabled) "LIVE TYPING ON" else "STREAM OFF",
                                    icon = Icons.Default.FlashOn,
                                    iconSize = 14.dp,
                                    modifier = Modifier.height(34.dp),
                                    testTag = "keyboard_toggle_live"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Mask Toggle
                                IconButton(
                                    onClick = { viewModel.toggleMasked() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Mask text",
                                        tint = GripTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Air mouse is integrated directly in the keyboard surface.
                                TactileButton(
                                    onClick = { viewModel.toggleAirMouse() },
                                    accentColor = if (state.isAirMouseActive) GripCyan else null,
                                    text = if (state.isAirMouseActive) "AIR MOUSE ON" else "AIR MOUSE",
                                    icon = Icons.Default.Mouse,
                                    iconSize = 14.dp,
                                    modifier = Modifier.height(34.dp),
                                    testTag = "keyboard_toggle_air_mouse"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = { viewModel.onTextChanged(it) },
                            placeholder = { Text("Type here to send directly to your TV...", color = GripTextTertiary) },
                            visualTransformation = if (state.isMasked) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("keyboard_text_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendCurrentText() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GripCyan,
                                unfocusedBorderColor = GripCardBorder,
                                focusedTextColor = GripTextPrimary,
                                unfocusedTextColor = GripTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TactileButton(
                                    onClick = { viewModel.pasteFromClipboard() },
                                    icon = Icons.Default.ContentPaste,
                                    text = "PASTE",
                                    iconSize = 16.dp,
                                    modifier = Modifier.height(44.dp),
                                    testTag = "keyboard_paste"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TactileButton(
                                    onClick = { viewModel.sendBackspace() },
                                    icon = Icons.Default.Backspace,
                                    iconSize = 18.dp,
                                    modifier = Modifier.size(44.dp),
                                    testTag = "keyboard_backspace"
                                )
                            }

                            TactileButton(
                                onClick = { viewModel.sendCurrentText() },
                                isPrimary = true,
                                icon = Icons.AutoMirrored.Filled.Send,
                                text = "SEND TO TV",
                                modifier = Modifier.height(44.dp),
                                testTag = "keyboard_send_button"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Dictation Card
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Voice Dictation",
                            color = GripTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.isListeningVoice) "Listening... Speak into phone mic" else "Tap microphone to speak and send to the TV",
                            color = if (state.isListeningVoice) GripCyan else GripTextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        TactileButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleVoiceListening()
                                }
                            },
                            accentColor = if (state.isListeningVoice) GripRed else GripOrangeBright,
                            icon = if (state.isListeningVoice) Icons.Default.MicOff else Icons.Default.Mic,
                            iconSize = 28.dp,
                            text = if (state.isListeningVoice) "STOP LISTENING" else "START VOICE TYPING",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            testTag = "keyboard_voice_button"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Search Phrases
                Text(
                    text = "Quick Phrases & Shortcuts",
                    color = GripTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.recentPhrases.forEach { phrase ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(GripCardSurface)
                                .border(1.dp, GripCardBorder, RoundedCornerShape(20.dp))
                                .clickable { viewModel.selectQuickPhrase(phrase) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = phrase,
                                color = GripCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // TV Navigation Keys Quick Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TactileButton(
                        onClick = { viewModel.sendKey(TvKey.ENTER) },
                        text = "ENTER / OK",
                        isPrimary = true,
                        modifier = Modifier.weight(1f).padding(4.dp),
                        testTag = "keyboard_key_enter"
                    )
                    TactileButton(
                        onClick = { viewModel.sendKey(TvKey.BACK) },
                        text = "BACK",
                        modifier = Modifier.weight(0.8f).padding(4.dp),
                        testTag = "keyboard_key_back"
                    )
                    TactileButton(
                        onClick = { viewModel.sendKey(TvKey.HOME) },
                        text = "HOME",
                        modifier = Modifier.weight(0.8f).padding(4.dp),
                        testTag = "keyboard_key_home"
                    )
                }
            }

            DeveloperCredit(modifier = Modifier.navigationBarsPadding())
        }
    }
}
