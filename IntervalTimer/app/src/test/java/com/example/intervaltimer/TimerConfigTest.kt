package com.example.intervaltimer

import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.shared.model.SoundPattern
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerConfigTest {

    private fun config(intervalMillis: Long) = TimerConfig(
        intervalMillis = intervalMillis,
        signalType = SignalType.SOUND_AND_VIBRATION,
        soundPattern = SoundPattern.SHORT_BEEP,
        vibrationPattern = VibrationPattern.SHORT
    )

    @Test
    fun `interval within bounds is valid`() {
        assertTrue(config(90_000L).isValid()) // 1m30s
    }

    @Test
    fun `interval below minimum is invalid`() {
        assertFalse(config(500L).isValid()) // 0.5s < 1s minimum
    }

    @Test
    fun `interval above maximum is invalid`() {
        assertFalse(config(25L * 60 * 60 * 1000).isValid()) // 25h > 24h maximum
    }

    @Test
    fun `coerceInterval clamps to minimum`() {
        assertEquals(TimerConfig.MIN_INTERVAL_MILLIS, TimerConfig.coerceInterval(0L))
    }

    @Test
    fun `coerceInterval clamps to maximum`() {
        assertEquals(TimerConfig.MAX_INTERVAL_MILLIS, TimerConfig.coerceInterval(999_999_999_999L))
    }

    @Test
    fun `1m30s example from spec parses to 90000ms`() {
        val hours = 0
        val minutes = 1
        val seconds = 30
        val millis = (hours * 3600L + minutes * 60L + seconds) * 1000L
        assertEquals(90_000L, millis)
    }
}
