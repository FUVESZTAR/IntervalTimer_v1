package com.example.intervaltimer.wear.settings

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.shared.model.SoundPattern
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.VibrationPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchDataStore by preferencesDataStore(name = "interval_timer_watch_settings")

/**
 * Deliberately separate from the phone's SettingsRepository / DataStore file: the watch
 * keeps its own last-used interval and signal preferences so a standalone session on the
 * watch works identically whether or not a phone has ever been paired.
 */
class WatchSettingsRepository(private val context: Context) {

    private object Keys {
        val INTERVAL_MILLIS = longPreferencesKey("interval_millis")
        val SIGNAL_TYPE = intPreferencesKey("signal_type")
        val VIBRATION_PATTERN = intPreferencesKey("vibration_pattern")
    }

    val configFlow: Flow<TimerConfig> = context.watchDataStore.data.map { prefs ->
        TimerConfig(
            intervalMillis = prefs[Keys.INTERVAL_MILLIS] ?: TimerConfig.DEFAULT.intervalMillis,
            signalType = SignalType.fromOrdinalSafe(prefs[Keys.SIGNAL_TYPE] ?: SignalType.VIBRATION_ONLY.ordinal),
            soundPattern = SoundPattern.SHORT_BEEP, // watch signals via vibration only, see WatchAlarmReceiver
            vibrationPattern = VibrationPattern.fromOrdinalSafe(prefs[Keys.VIBRATION_PATTERN] ?: TimerConfig.DEFAULT.vibrationPattern.ordinal)
        )
    }

    suspend fun saveConfig(config: TimerConfig) {
        context.watchDataStore.edit { prefs ->
            prefs[Keys.INTERVAL_MILLIS] = config.intervalMillis
            prefs[Keys.SIGNAL_TYPE] = config.signalType.ordinal
            prefs[Keys.VIBRATION_PATTERN] = config.vibrationPattern.ordinal
        }
    }
}
