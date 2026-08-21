package com.example.intervaltimer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.intervaltimer.communication.WatchCommunicationManager
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        receiverScope.launch {
            try {
                TimerEngine.hydrateFromPersistence(context.applicationContext)
                when (intent.action) {
                    NotificationHelper.ACTION_STOP -> {
                        TimerEngine.stop(context)
                        NotificationHelper.cancel(context)
                        context.stopService(Intent(context, TimerForegroundService::class.java))
                        WatchCommunicationManager.sendStateSync(context)
                    }
                    NotificationHelper.ACTION_PAUSE_RESUME -> {
                        if (TimerEngine.currentState() == TimerRunState.RUNNING) {
                            TimerEngine.pause(context)
                        } else {
                            TimerEngine.start(context, TimerEngine.snapshot.value.config)
                        }
                        NotificationHelper.updateOngoingNotification(context)
                        WatchCommunicationManager.sendStateSync(context)
                    }
                }
            } finally {
                receiverScope.cancel()
                pendingResult.finish()
            }
        }
    }
}
