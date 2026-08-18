package com.example.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    var isHapticsEnabled: Boolean = true
    var intensity: Float = 1.0f

    fun performClick() {
        if (!isHapticsEnabled || intensity <= 0f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val amplitude = (VibrationEffect.DEFAULT_AMPLITUDE * intensity.coerceIn(0.1f, 1.0f)).toInt().coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20L)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun performHeavyClick() {
        if (!isHapticsEnabled || intensity <= 0f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(45L)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun performSuccess() {
        if (!isHapticsEnabled || intensity <= 0f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun performError() {
        if (!isHapticsEnabled || intensity <= 0f) return
        try {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 40, 80), -1)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
