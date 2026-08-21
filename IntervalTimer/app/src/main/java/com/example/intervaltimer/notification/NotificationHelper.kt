package com.example.intervaltimer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.intervaltimer.timer.TimerEngine
import com.example.intervaltimer.ui.MainActivity
import com.example.intervaltimer.shared.model.TimerRunState
import java.util.concurrent.TimeUnit

object NotificationHelper {

    const val CHANNEL_ID = "interval_timer_active"
    const val NOTIFICATION_ID = 42

    const val ACTION_STOP = "com.example.intervaltimer.action.STOP"
    const val ACTION_PAUSE_RESUME = "com.example.intervaltimer.action.PAUSE_RESUME"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // LOW importance: no sound/heads-up from the notification itself — the *signal*
        // (sound/vibration) is played explicitly by SoundPlayer/VibrationPlayer, not by
        // this channel, so we don't double-notify the user.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aktív időzítő",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Az aktív intervallum-időzítő állapota és hátralévő ideje"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildOngoingNotification(context: Context): android.app.Notification {
        val snapshot = TimerEngine.snapshot.value
        val remaining = TimerEngine.remainingMillisNow()

        val stateText = when (snapshot.runState) {
            TimerRunState.RUNNING -> "Aktív"
            TimerRunState.PAUSED -> "Szünetel"
            else -> "Leállítva"
        }

        val contentText = if (remaining != null) {
            "Hátralévő idő: ${formatDuration(remaining)}"
        } else {
            "Nincs ütemezett jelzés"
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResumeIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_PAUSE_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResumeLabel = if (snapshot.runState == TimerRunState.RUNNING) "Szünet" else "Folytatás"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Interval Timer – $stateText")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(snapshot.runState != TimerRunState.STOPPED)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, pauseResumeLabel, pauseResumeIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    fun updateOngoingNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildOngoingNotification(context))
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
