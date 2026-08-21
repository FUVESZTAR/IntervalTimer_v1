package com.example.intervaltimer.wear.communication

import com.example.intervaltimer.shared.communication.WearMessagePaths
import com.example.intervaltimer.wear.vibration.WatchVibrationPlayer
import com.example.intervaltimer.shared.model.VibrationPattern
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Handles messages coming FROM the phone (mode B: "Telefon + Galaxy Watch" in spec §14).
 * This is entirely separate from WatchTimerEngine (the standalone mode C engine) — a
 * phone-driven signal just buzzes the watch once, it never starts/stops the watch's own
 * standalone timer, so the two modes can never conflict or interfere with each other.
 */
class PhoneListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearMessagePaths.SIGNAL_FIRED -> {
                // Phone already decided sound/vibration for itself; the watch just gets a
                // short default buzz to confirm the signal, keeping this path lightweight
                // and not requiring the full config payload for every single tick.
                WatchVibrationPlayer.play(applicationContext, VibrationPattern.SHORT)
            }
            WearMessagePaths.STATE_SYNC -> {
                runCatching {
                    val json = JSONObject(String(event.data))
                    // Reserved for a future "mirror phone state on watch screen" view;
                    // intentionally not wired into WatchTimerEngine (see class doc above).
                    json.optString("runState")
                }
            }
        }
    }
}
