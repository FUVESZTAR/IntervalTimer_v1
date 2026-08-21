package com.example.intervaltimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.intervaltimer.notification.TimerForegroundService
import com.example.intervaltimer.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * See spec §24: restoring across reboot is opt-in (Settings -> "auto-resume after restart")
 * because silently resuming a background timer the user may have forgotten about is a
 * worse experience than a clean IDLE state. Default is OFF.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = SettingsRepository(context.applicationContext)
                val autoRestore = repo.autoRestoreOnBootFlow.first()
                val wasRunning = repo.wasRunningBeforeShutdown()

                if (autoRestore && wasRunning) {
                    val config = repo.configFlow.first()
                    TimerEngine.start(context.applicationContext, config)
                    ContextCompat.startForegroundService(
                        context.applicationContext,
                        Intent(context.applicationContext, TimerForegroundService::class.java)
                    )
                } else {
                    repo.persistRunningState(isRunning = false, nextTriggerWallClockMillis = null)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
