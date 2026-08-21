package com.example.intervaltimer.shared.model

/**
 * Immutable description of "how the timer should behave".
 * Sent phone -> watch as a small serialized payload (see WearMessagePaths).
 *
 * intervalMillis bounds are enforced at the UI layer:
 *   min = 1_000L      (1 second)
 *   max = 86_400_000L (24 hours)
 */
data class TimerConfig(
    val intervalMillis: Long,
    val signalType: SignalType,
    val soundPattern: SoundPattern,
    val vibrationPattern: VibrationPattern,
    val watchEnabled: Boolean = true,
    val phoneEnabled: Boolean = true
) {
    companion object {
        const val MIN_INTERVAL_MILLIS = 1_000L
        const val MAX_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

        val DEFAULT = TimerConfig(
            intervalMillis = 5 * 60 * 1000L, // 5 min
            signalType = SignalType.SOUND_AND_VIBRATION,
            soundPattern = SoundPattern.SHORT_BEEP,
            vibrationPattern = VibrationPattern.SHORT
        )

        fun coerceInterval(millis: Long): Long =
            millis.coerceIn(MIN_INTERVAL_MILLIS, MAX_INTERVAL_MILLIS)
    }

    /** Simple validity check used before persisting or scheduling. */
    fun isValid(): Boolean = intervalMillis in MIN_INTERVAL_MILLIS..MAX_INTERVAL_MILLIS
}

/**
 * Runtime snapshot broadcast internally (StateFlow) and optionally to the watch,
 * so the UI on either device can render "AKTÍV / SZÜNETEL / LEÁLLÍTVA" + countdown.
 */
data class TimerSnapshot(
    val runState: TimerRunState,
    val config: TimerConfig,
    val nextTriggerElapsedRealtime: Long?, // SystemClock.elapsedRealtime() based, null if not running
    val remainingMillisAtPause: Long? = null // stored when paused, so resume can reschedule correctly
)
