package com.example.intervaltimer.shared.model

/**
 * Pure restore/recovery math kept in `shared` so the behavior can be JVM-tested without
 * Android dependencies.
 */
object TimerRestoreCalculator {

    /** Used when rebuilding in-memory state for an alarm that is already scheduled. */
    fun remainingForHydration(nowWallClockMillis: Long, nextTriggerWallClockMillis: Long): Long =
        (nextTriggerWallClockMillis - nowWallClockMillis).coerceAtLeast(0L)

    /**
     * Used after device reboot, when the old AlarmManager entry is gone and we must schedule a
     * fresh one. If the alarm should already have fired during boot, signal again ASAP.
     */
    fun remainingForReschedule(
        nowWallClockMillis: Long,
        nextTriggerWallClockMillis: Long?,
        fallbackIntervalMillis: Long
    ): Long {
        if (nextTriggerWallClockMillis == null) return fallbackIntervalMillis
        return (nextTriggerWallClockMillis - nowWallClockMillis).coerceAtLeast(TimerConfig.MIN_INTERVAL_MILLIS)
    }
}
