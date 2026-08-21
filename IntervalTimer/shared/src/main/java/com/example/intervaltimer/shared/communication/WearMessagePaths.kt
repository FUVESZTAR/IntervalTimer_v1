package com.example.intervaltimer.shared.communication

/**
 * Message paths used over the Wearable Data Layer MessageClient between
 * the phone app and the Wear OS app. Using MessageClient (not DataClient/SyncClient)
 * because these are small, one-shot, low-frequency, "fire an event" style payloads —
 * exactly the energy-efficient use case MessageClient is designed for.
 *
 * Payloads are tiny JSON strings (no network, no persistent connection required;
 * Play Services on both ends manages the Bluetooth channel opportunistically).
 */
object WearMessagePaths {
    /** Phone -> Watch: "a signal fired right now, buzz/notify" */
    const val SIGNAL_FIRED = "/interval_timer/signal_fired"

    /** Phone -> Watch: full config + run state, so the watch UI can mirror it */
    const val STATE_SYNC = "/interval_timer/state_sync"

    /** Watch -> Phone: user pressed STOP on the watch complication/app */
    const val REQUEST_STOP = "/interval_timer/request_stop"

    /** Watch -> Phone: user pressed PAUSE/RESUME on the watch */
    const val REQUEST_PAUSE_RESUME = "/interval_timer/request_pause_resume"

    /** Bidirectional heartbeat-free presence check (sent once on connect, not polled) */
    const val HELLO = "/interval_timer/hello"
}
