package com.example.intervaltimer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.shared.model.SoundPattern
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.VibrationPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore Preferences chosen over legacy SharedPreferences: it's async/coroutine-based
// (no main-thread disk I/O), transactionally safe, and is the officially recommended
// replacement per current Android guidance (see section 20 of the spec).
private val Context.dataStore by preferencesDataStore(name = "interval_timer_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val INTERVAL_MILLIS = longPreferencesKey("interval_millis")
        val SIGNAL_TYPE = intPreferencesKey("signal_type")
        val SOUND_PATTERN = intPreferencesKey("sound_pattern")
        val VIBRATION_PATTERN = intPreferencesKey("vibration_pattern")
        val WATCH_ENABLED = booleanPreferencesKey("watch_enabled")
        val PHONE_ENABLED = booleanPreferencesKey("phone_enabled")
        val AUTO_RESTORE_ON_BOOT = booleanPreferencesKey("auto_restore_on_boot")
        val DARK_THEME = stringPreferencesKey("dark_theme") // "system" | "dark" | "light"
        // Persisted so a running timer can be restored across process death / reboot.
        val WAS_RUNNING = booleanPreferencesKey("was_running")
        val NEXT_TRIGGER_WALL_CLOCK = longPreferencesKey("next_trigger_wall_clock")
    }

    val configFlow: Flow<TimerConfig> = context.dataStore.data.map { prefs ->
        TimerConfig(
            intervalMillis = prefs[Keys.INTERVAL_MILLIS] ?: TimerConfig.DEFAULT.intervalMillis,
            signalType = SignalType.fromOrdinalSafe(prefs[Keys.SIGNAL_TYPE] ?: TimerConfig.DEFAULT.signalType.ordinal),
            soundPattern = SoundPattern.fromOrdinalSafe(prefs[Keys.SOUND_PATTERN] ?: TimerConfig.DEFAULT.soundPattern.ordinal),
            vibrationPattern = VibrationPattern.fromOrdinalSafe(prefs[Keys.VIBRATION_PATTERN] ?: TimerConfig.DEFAULT.vibrationPattern.ordinal),
            watchEnabled = prefs[Keys.WATCH_ENABLED] ?: true,
            phoneEnabled = prefs[Keys.PHONE_ENABLED] ?: true
        )
    }

    val autoRestoreOnBootFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_RESTORE_ON_BOOT] ?: false }

    val darkThemeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.DARK_THEME] ?: "system" }

    suspend fun saveConfig(config: TimerConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INTERVAL_MILLIS] = config.intervalMillis
            prefs[Keys.SIGNAL_TYPE] = config.signalType.ordinal
            prefs[Keys.SOUND_PATTERN] = config.soundPattern.ordinal
            prefs[Keys.VIBRATION_PATTERN] = config.vibrationPattern.ordinal
            prefs[Keys.WATCH_ENABLED] = config.watchEnabled
            prefs[Keys.PHONE_ENABLED] = config.phoneEnabled
        }
    }

    suspend fun setAutoRestoreOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RESTORE_ON_BOOT] = enabled }
    }

    suspend fun setDarkTheme(mode: String) {
        context.dataStore.edit { it[Keys.DARK_THEME] = mode }
    }

    /** Called by TimerEngine whenever it (re)schedules, so reboot/process-death can recover. */
    suspend fun persistRunningState(isRunning: Boolean, nextTriggerWallClockMillis: Long?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAS_RUNNING] = isRunning
            if (nextTriggerWallClockMillis != null) {
                prefs[Keys.NEXT_TRIGGER_WALL_CLOCK] = nextTriggerWallClockMillis
            }
        }
    }

    suspend fun wasRunningBeforeShutdown(): Boolean =
        kotlinx.coroutines.flow.first(context.dataStore.data.map { it[Keys.WAS_RUNNING] ?: false })
}
