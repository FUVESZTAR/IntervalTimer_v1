package com.example.intervaltimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.intervaltimer.audio.SoundPlayer
import com.example.intervaltimer.communication.WatchCommunicationManager
import com.example.intervaltimer.notification.NotificationHelper
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.vibration.VibrationPlayer
import kotlinx.coroutines.runBlocking

/**
 * Receives the AlarmManager broadcast when an interval elapses.
 *
 * Holds a short-lived partial wake lock ONLY for the few hundred ms needed to actually
 * play the sound/vibration and hand off to the next alarm — never longer. This is the
 * one and only place in the app that acquires a wake lock, and it always releases it.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runBlocking { TimerEngine.hydrateFromPersistence(context.applicationContext) }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "IntervalTimer:SignalWakeLock"
        )
        wakeLock.acquire(5_000L) // safety timeout; released explicitly below anyway

        try {
            val config = TimerEngine.snapshot.value.config

            if (config.phoneEnabled) {
                when (config.signalType) {
                    SignalType.SOUND_ONLY -> SoundPlayer.play(context, config.soundPattern)
                    SignalType.VIBRATION_ONLY -> VibrationPlayer.play(context, config.vibrationPattern)
                    SignalType.SOUND_AND_VIBRATION -> {
                        SoundPlayer.play(context, config.soundPattern)
                        VibrationPlayer.play(context, config.vibrationPattern)
                    }
                    SignalType.SILENT -> Unit
                }
            }

            if (config.watchEnabled) {
                // Fire-and-forget; if the watch is unreachable this silently no-ops
                // (see WatchCommunicationManager) and the phone timer is unaffected.
                WatchCommunicationManager.sendSignalFired(context)
            }

            // Reschedule immediately so the next alarm is set before this receiver returns.
            TimerEngine.onSignalFired(context)
            NotificationHelper.updateOngoingNotification(context)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
