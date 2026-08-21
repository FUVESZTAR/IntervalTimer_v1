package com.example.intervaltimer.wear.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.intervaltimer.shared.model.VibrationPattern

/**
 * Same one-shot, no-loop approach as the phone's VibrationPlayer (spec §15: on the watch,
 * energy discipline matters even more — the vibration only fires at the exact signal moment).
 */
object WatchVibrationPlayer {

    fun play(context: Context, pattern: VibrationPattern) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val durationMs = when (pattern) {
            VibrationPattern.SHORT -> 150L
            VibrationPattern.MEDIUM -> 400L
            VibrationPattern.LONG -> 800L
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
