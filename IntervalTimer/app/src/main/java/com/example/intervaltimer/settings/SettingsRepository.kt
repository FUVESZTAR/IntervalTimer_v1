package com.example.intervaltimer.settings

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.remove
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.shared.model.SoundPattern
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.shared.model.TimerSnapshot
import com.example.intervaltimer.shared.model.VibrationPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore Preferences chosen over legacy SharedPreferences: it's async/coroutine-based
// (no main-thread disk I/O), transactionally safe, and is the officially recommended
// replacement per current Android guidance (see section 20 of the spec).
private val Context.dataStore by preferencesDataStore(name = "interval_timer_settings")

class SettingsRepository(private val context: Context) {

    data class PersistedTimerRuntime(
        val runState: TimerRunState,
        val nextTriggerWallClockMillis: Long?,
        val pausedRemainingMillis: Long?
    )

    private object Keys {
        val INTERVAL_MILLIS = longPreferencesKey("interval_millis")
        val SIGNAL_TYPE = intPreferencesKey("signal_type")
        val SOUND_PATTERN = intPreferencesKey("sound_pattern")
        val VIBRATION_PATTERN = intPreferencesKey("vibration_pattern")
        val WATCH_ENABLED = booleanPreferencesKey("watch_enabled")
        val PHONE_ENABLED = booleanPreferencesKey("phone_enabled")
        val AUTO_RESTORE_ON_BOOT = booleanPreferencesKey("auto_restore_on_boot")
        val DARK_THEME = stringPreferencesKey("dark_theme") // "system" | "dark" | "light"
        val TIMER_RUN_STATE = stringPreferencesKey("timer_run_state")
        val NEXT_TRIGGER_WALL_CLOCK = longPreferencesKey("next_trigger_wall_clock")
        val PAUSED_REMAINING_MILLIS = longPreferencesKey("paused_remaining_millis")
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

    suspend fun persistSnapshot(snapshot: TimerSnapshot) {
        val nowWallClockMillis = System.currentTimeMillis()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()

        context.dataStore.edit { prefs ->
            prefs[Keys.TIMER_RUN_STATE] = snapshot.runState.name
            when (snapshot.runState) {
                TimerRunState.RUNNING -> {
                    val nextTriggerWallClockMillis = snapshot.nextTriggerElapsedRealtime?.let { nextElapsed ->
                        nowWallClockMillis + (nextElapsed - nowElapsedRealtime).coerceAtLeast(0L)
                    }
                    if (nextTriggerWallClockMillis != null) {
                        prefs[Keys.NEXT_TRIGGER_WALL_CLOCK] = nextTriggerWallClockMillis
                    } else {
                        prefs.remove(Keys.NEXT_TRIGGER_WALL_CLOCK)
                    }
                    prefs.remove(Keys.PAUSED_REMAINING_MILLIS)
                }
                TimerRunState.PAUSED -> {
                    prefs.remove(Keys.NEXT_TRIGGER_WALL_CLOCK)
                    prefs[Keys.PAUSED_REMAINING_MILLIS] =
                        snapshot.remainingMillisAtPause ?: snapshot.config.intervalMillis
                }
                else -> {
                    prefs.remove(Keys.NEXT_TRIGGER_WALL_CLOCK)
                    prefs.remove(Keys.PAUSED_REMAINING_MILLIS)
                }
            }
        }
    }

    suspend fun readPersistedTimerRuntime(): PersistedTimerRuntime =
        context.dataStore.data.map { prefs ->
            PersistedTimerRuntime(
                runState = runCatching {
                    TimerRunState.valueOf(prefs[Keys.TIMER_RUN_STATE] ?: TimerRunState.IDLE.name)
                }.getOrDefault(TimerRunState.IDLE),
                nextTriggerWallClockMillis = prefs[Keys.NEXT_TRIGGER_WALL_CLOCK],
                pausedRemainingMillis = prefs[Keys.PAUSED_REMAINING_MILLIS]
            )
        }.first()
}
