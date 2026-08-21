package com.example.intervaltimer.shared.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerRestoreCalculatorTest {

    @Test
    fun `hydration keeps future remaining delay`() {
        assertEquals(
            15_000L,
            TimerRestoreCalculator.remainingForHydration(
                nowWallClockMillis = 1_000L,
                nextTriggerWallClockMillis = 16_000L
            )
        )
    }

    @Test
    fun `hydration clamps overdue alarms to zero`() {
        assertEquals(
            0L,
            TimerRestoreCalculator.remainingForHydration(
                nowWallClockMillis = 25_000L,
                nextTriggerWallClockMillis = 16_000L
            )
        )
    }

    @Test
    fun `reboot reschedule keeps future remaining delay`() {
        assertEquals(
            15_000L,
            TimerRestoreCalculator.remainingForReschedule(
                nowWallClockMillis = 1_000L,
                nextTriggerWallClockMillis = 16_000L,
                fallbackIntervalMillis = 30_000L
            )
        )
    }

    @Test
    fun `reboot reschedule clamps overdue alarms to minimum interval`() {
        assertEquals(
            TimerConfig.MIN_INTERVAL_MILLIS,
            TimerRestoreCalculator.remainingForReschedule(
                nowWallClockMillis = 25_000L,
                nextTriggerWallClockMillis = 16_000L,
                fallbackIntervalMillis = 30_000L
            )
        )
    }

    @Test
    fun `reboot reschedule falls back to configured interval when trigger is unknown`() {
        assertEquals(
            30_000L,
            TimerRestoreCalculator.remainingForReschedule(
                nowWallClockMillis = 25_000L,
                nextTriggerWallClockMillis = null,
                fallbackIntervalMillis = 30_000L
            )
        )
    }
}
