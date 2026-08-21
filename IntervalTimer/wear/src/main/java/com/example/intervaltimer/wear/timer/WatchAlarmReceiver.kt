package com.example.intervaltimer.wear.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.wear.vibration.WatchVibrationPlayer

class WatchAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalTimerWatch:SignalWakeLock")
        wakeLock.acquire(5_000L)
        try {
            val config = WatchTimerEngine.snapshot.value.config
            // Watch hardware has no dedicated alarm speaker suitable for tones in most
            // Wear OS 3+ devices' default audio routing for background apps, so the
            // standalone watch timer signals via vibration regardless of SOUND settings —
            // documented limitation, see README "Ismert Wear OS korlátozások".
            if (config.signalType != SignalType.SILENT) {
                WatchVibrationPlayer.play(context, config.vibrationPattern)
            }
            WatchTimerEngine.onSignalFired(context)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
