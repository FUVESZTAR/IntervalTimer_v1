package com.example.intervaltimer.notification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.timer.TimerEngine
import kotlinx.coroutines.runBlocking

/**
 * Deliberately minimal. This service does NOT run a countdown loop — its only jobs are:
 *  1. Satisfy Android's requirement that a "long-running background task" show a
 *     foreground notification (keeps the process from being killed while a timer
 *     is RUNNING/PAUSED).
 *  2. Render the current TimerEngine snapshot into that notification.
 * All actual timing is done by AlarmManager waking TimerAlarmReceiver, which calls
 * NotificationHelper.updateOngoingNotification() to refresh the text shown here.
 */
class TimerForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runBlocking { TimerEngine.hydrateFromPersistence(applicationContext) }
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildOngoingNotification(this))

        // If the engine has already moved to STOPPED (e.g. race with a STOP action),
        // there's nothing left for this service to do.
        if (TimerEngine.currentState() == TimerRunState.STOPPED) {
            stopSelf()
        }
        // START_STICKY: if the OS kills the process under memory pressure, restart the
        // service so an active timer keeps its foreground presence; the next AlarmManager
        // firing will still occur regardless (alarms survive process death), this just
        // ensures the notification/UI recovers promptly.
        return START_STICKY
    }

    fun stopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
