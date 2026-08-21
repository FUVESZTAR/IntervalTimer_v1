package com.example.intervaltimer.communication

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.intervaltimer.BuildConfig
import com.example.intervaltimer.shared.communication.WearMessagePaths
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.notification.NotificationHelper
import com.example.intervaltimer.notification.TimerForegroundService
import com.example.intervaltimer.timer.TimerEngine
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Phone-side wrapper around the Wearable Data Layer MessageClient.
 *
 * Deliberately NOT a persistent connection or polling loop: MessageClient sends are
 * one-shot, asynchronous, and Play Services manages the underlying Bluetooth link
 * opportunistically (batches/wakes as needed) — this is the energy-efficient, Google-
 * recommended pattern for phone<->watch events (spec §13/§27).
 *
 * If no watch is connected, send() calls fail silently (logged only in debug) and the
 * phone-side timer is completely unaffected — see TimerAlarmReceiver, which never
 * blocks on this call's result.
 */
object WatchCommunicationManager {

    private const val TAG = "WatchComm"

    fun sendSignalFired(context: Context) {
        val payload = JSONObject().apply {
            put("event", "signal_fired")
            put("timestampMillis", System.currentTimeMillis())
        }.toString().toByteArray()

        sendToAllNodes(context, WearMessagePaths.SIGNAL_FIRED, payload)
    }

    fun sendStateSync(context: Context) {
        val snapshot = TimerEngine.snapshot.value
        val payload = JSONObject().apply {
            put("runState", snapshot.runState.name)
            put("intervalMillis", snapshot.config.intervalMillis)
            put("signalType", snapshot.config.signalType.name)
            put("remainingMillis", TimerEngine.remainingMillisNow() ?: -1L)
        }.toString().toByteArray()

        sendToAllNodes(context, WearMessagePaths.STATE_SYNC, payload)
    }

    private fun sendToAllNodes(context: Context, path: String, payload: ByteArray) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, payload)
                        .addOnFailureListener { e -> logIfVerbose("send failed to ${node.id}: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> logIfVerbose("no connected nodes / lookup failed: ${e.message}") }
    }

    private fun logIfVerbose(message: String) {
        if (BuildConfig.VERBOSE_LOGGING) Log.d(TAG, message)
    }

    /**
     * Receives PAUSE/STOP requests initiated from the watch side.
     * Registered in the manifest as a WearableListenerService.
     */
    class ListenerService : WearableListenerService() {
        override fun onMessageReceived(event: MessageEvent) {
            runBlocking { TimerEngine.hydrateFromPersistence(applicationContext) }

            when (event.path) {
                WearMessagePaths.REQUEST_STOP -> {
                    TimerEngine.stop(applicationContext)
                    NotificationHelper.cancel(applicationContext)
                    stopService(Intent(applicationContext, TimerForegroundService::class.java))
                }
                WearMessagePaths.REQUEST_PAUSE_RESUME -> {
                    if (TimerEngine.currentState() == TimerRunState.RUNNING) {
                        TimerEngine.pause(applicationContext)
                    } else {
                        TimerEngine.start(applicationContext, TimerEngine.snapshot.value.config)
                    }
                    NotificationHelper.updateOngoingNotification(applicationContext)
                }
                WearMessagePaths.HELLO -> sendStateSync(applicationContext)
            }
        }
    }
}
