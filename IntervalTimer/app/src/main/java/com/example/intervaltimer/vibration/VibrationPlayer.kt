package com.example.intervaltimer.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.intervaltimer.shared.model.VibrationPattern

object VibrationPlayer {

    fun play(context: Context, pattern: VibrationPattern) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val durationMs = when (pattern) {
            VibrationPattern.SHORT -> 150L
            VibrationPattern.MEDIUM -> 400L
            VibrationPattern.LONG -> 800L
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
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
