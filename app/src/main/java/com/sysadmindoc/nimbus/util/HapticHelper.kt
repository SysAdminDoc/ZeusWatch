package com.sysadmindoc.nimbus.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sysadmindoc.nimbus.data.model.AlertSeverity

/**
 * Centralized haptic feedback for weather alerts and interactions.
 * Provides severity-appropriate vibration patterns.
 */
object HapticHelper {

    /** Vibrate for a weather alert, intensity based on severity. */
    fun vibrateForAlert(context: Context, severity: AlertSeverity) {
        val vibrator = getVibrator(context) ?: return

        val effect = when (severity) {
            AlertSeverity.EXTREME -> VibrationEffect.createWaveform(
                longArrayOf(0, 400, 200, 400, 200, 400), -1 // Strong triple pulse
            )
            AlertSeverity.SEVERE -> VibrationEffect.createWaveform(
                longArrayOf(0, 300, 150, 300), -1 // Double pulse
            )
            AlertSeverity.MODERATE -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            else -> VibrationEffect.createOneShot(100, 80) // Light tap
        }

        vibrator.vibrate(effect)
    }

    /** Light tap for UI interactions. */
    fun lightTap(context: Context) {
        val vibrator = getVibrator(context) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(30, 60))
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
