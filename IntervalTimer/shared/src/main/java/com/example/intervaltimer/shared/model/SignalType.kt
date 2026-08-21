package com.example.intervaltimer.shared.model

/**
 * How the user wants to be signalled when an interval elapses.
 * Kept in `shared` so both the phone app and the Wear OS app agree on the same values
 * when a config is sent across the Data Layer.
 */
enum class SignalType {
    VIBRATION_ONLY,
    SOUND_ONLY,
    SOUND_AND_VIBRATION,
    SILENT; // e.g. watch-only visual pulse, or "do not disturb" mode

    companion object {
        fun fromOrdinalSafe(ordinal: Int): SignalType =
            entries.getOrElse(ordinal) { SOUND_AND_VIBRATION }
    }
}

enum class SoundPattern {
    SHORT_BEEP,
    DOUBLE_BEEP,
    SHORT_CHIME,
    LONG_TONE;

    companion object {
        fun fromOrdinalSafe(ordinal: Int): SoundPattern =
            entries.getOrElse(ordinal) { SHORT_BEEP }
    }
}

enum class VibrationPattern {
    SHORT,
    MEDIUM,
    LONG;

    companion object {
        fun fromOrdinalSafe(ordinal: Int): VibrationPattern =
            entries.getOrElse(ordinal) { SHORT }
    }
}

/** State machine for the timer. Transitions are validated in TimerEngine. */
enum class TimerRunState {
    IDLE,
    RUNNING,
    PAUSED,
    STOPPED
}
