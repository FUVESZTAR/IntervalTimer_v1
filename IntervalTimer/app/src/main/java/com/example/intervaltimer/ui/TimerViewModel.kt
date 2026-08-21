package com.example.intervaltimer.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.intervaltimer.communication.WatchCommunicationManager
import com.example.intervaltimer.notification.NotificationHelper
import com.example.intervaltimer.notification.TimerForegroundService
import com.example.intervaltimer.settings.SettingsRepository
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.timer.TimerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TimerUiState(
    val config: TimerConfig = TimerConfig.DEFAULT,
    val runState: TimerRunState = TimerRunState.IDLE,
    val remainingMillis: Long? = null
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _uiTick = MutableStateFlow(0L)
    val uiState: StateFlow<TimerUiState> = combine(
        TimerEngine.snapshot, _uiTick
    ) { snapshot, _ ->
        TimerUiState(
            config = snapshot.config,
            runState = snapshot.runState,
            remainingMillis = TimerEngine.remainingMillisNow()
        )
    }.let { flow ->
        val state = MutableStateFlow(TimerUiState())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    init {
        viewModelScope.launch {
            TimerEngine.hydrateFromPersistence(application)
        }
        // Load persisted config once on start.
        viewModelScope.launch {
            val savedConfig = settingsRepository.configFlow
            savedConfig.collect { config ->
                TimerEngine.updateConfigWhileIdle(config)
            }
        }
        // UI-only countdown refresh (1x/sec) purely for the on-screen "mm:ss" label.
        // This is a cheap Compose-scoped tick, NOT the signal mechanism itself — it does
        // nothing while the app is backgrounded/closed, since AlarmManager handles that.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiTick.value = System.currentTimeMillis()
            }
        }
    }

    fun start() {
        val app = getApplication<Application>()
        TimerEngine.start(app, uiState.value.config)
        ContextCompat.startForegroundService(app, Intent(app, TimerForegroundService::class.java))
        NotificationHelper.updateOngoingNotification(app)
        WatchCommunicationManager.sendStateSync(app)
    }

    fun pause() {
        val app = getApplication<Application>()
        TimerEngine.pause(app)
        NotificationHelper.updateOngoingNotification(app)
        WatchCommunicationManager.sendStateSync(app)
    }

    fun stop() {
        val app = getApplication<Application>()
        TimerEngine.stop(app)
        NotificationHelper.cancel(app)
        app.stopService(Intent(app, TimerForegroundService::class.java))
        WatchCommunicationManager.sendStateSync(app)
    }

    fun updateConfig(newConfig: TimerConfig) {
        if (uiState.value.runState == TimerRunState.RUNNING) return // must pause/stop first
        TimerEngine.updateConfigWhileIdle(newConfig)
        viewModelScope.launch { settingsRepository.saveConfig(newConfig) }
    }

    fun testSignal() {
        val app = getApplication<Application>()
        val config = uiState.value.config
        com.example.intervaltimer.audio.SoundPlayer.let {
            if (config.signalType == com.example.intervaltimer.shared.model.SignalType.SOUND_ONLY ||
                config.signalType == com.example.intervaltimer.shared.model.SignalType.SOUND_AND_VIBRATION
            ) it.playTest(app, config.soundPattern)
        }
        if (config.signalType == com.example.intervaltimer.shared.model.SignalType.VIBRATION_ONLY ||
            config.signalType == com.example.intervaltimer.shared.model.SignalType.SOUND_AND_VIBRATION
        ) {
            com.example.intervaltimer.vibration.VibrationPlayer.play(app, config.vibrationPattern)
        }
        // Test signal never touches the timer state and is not sent to the watch,
        // per spec §4 ("ez ne indítsa el az időzítőt").
    }
}
