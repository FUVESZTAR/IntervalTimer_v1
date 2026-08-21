package com.example.intervaltimer.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.example.intervaltimer.shared.model.SoundPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Plays short, predefined tones using ToneGenerator (DTMF-style synthesized beeps).
 * Chosen over shipping .mp3/.wav assets + MediaPlayer because:
 *  - zero app-size cost, zero decode overhead,
 *  - ToneGenerator is designed exactly for brief UI/alert tones,
 *  - it self-releases immediately after playing (no lingering AudioTrack/MediaPlayer
 *    instance held in memory between signals).
 */
object SoundPlayer {

    fun play(context: android.content.Context, pattern: SoundPattern) {
        // Runs off the main thread; ToneGenerator calls block briefly (tens of ms).
        CoroutineScope(Dispatchers.Default).launch {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, /* volume 0-100 */ 90)
            try {
                when (pattern) {
                    SoundPattern.SHORT_BEEP -> playAndWait(toneGenerator, ToneGenerator.TONE_PROP_BEEP, 150)
                    SoundPattern.DOUBLE_BEEP -> {
                        playAndWait(toneGenerator, ToneGenerator.TONE_PROP_BEEP, 120)
                        Thread.sleep(100)
                        playAndWait(toneGenerator, ToneGenerator.TONE_PROP_BEEP, 120)
                    }
                    SoundPattern.SHORT_CHIME -> playAndWait(toneGenerator, ToneGenerator.TONE_PROP_ACK, 200)
                    SoundPattern.LONG_TONE -> playAndWait(toneGenerator, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                }
            } finally {
                toneGenerator.release()
            }
        }
    }

    /** Test-signal helper: identical to play(), exposed separately for clarity at call sites. */
    fun playTest(context: android.content.Context, pattern: SoundPattern) = play(context, pattern)

    private fun playAndWait(toneGenerator: ToneGenerator, tone: Int, durationMs: Int) {
        toneGenerator.startTone(tone, durationMs)
        Thread.sleep(durationMs.toLong())
    }
}
