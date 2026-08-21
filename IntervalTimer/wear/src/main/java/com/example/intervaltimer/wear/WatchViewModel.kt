package com.example.intervaltimer.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.wear.settings.WatchSettingsRepository
import com.example.intervaltimer.wear.timer.WatchTimerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchUiState(
    val config: TimerConfig = TimerConfig.DEFAULT,
    val runState: TimerRunState = TimerRunState.IDLE,
    val remainingMillis: Long? = null
)

/**
 * Drives the STANDALONE watch timer (WatchTimerEngine). This is intentionally the only
 * timer control surface on the watch screen — it works with zero phone connectivity.
 * A separately-received phone-driven state (mirrored via PhoneListenerService) is shown
 * only as a passive "Telefon: aktív" indicator elsewhere; it never overrides this engine.
 */
class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WatchSettingsRepository(application)
    private val _tick = MutableStateFlow(0L)

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.configFlow.collect { config ->
                if (WatchTimerEngine.currentState() != TimerRunState.RUNNING) {
                    _uiState.value = _uiState.value.copy(config = config)
                }
            }
        }
        viewModelScope.launch {
            WatchTimerEngine.snapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(runState = snapshot.runState, config = snapshot.config)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.value = _uiState.value.copy(remainingMillis = WatchTimerEngine.remainingMillisNow())
            }
        }
    }

    fun start() {
        val app = getApplication<Application>()
        WatchTimerEngine.start(app, _uiState.value.config)
        viewModelScope.launch { repository.saveConfig(_uiState.value.config) }
    }

    fun pause() = WatchTimerEngine.pause(getApplication())

    fun stop() = WatchTimerEngine.stop(getApplication())

    fun updateInterval(millis: Long) {
        if (_uiState.value.runState == TimerRunState.RUNNING) return
        val newConfig = _uiState.value.config.copy(intervalMillis = TimerConfig.coerceInterval(millis))
        _uiState.value = _uiState.value.copy(config = newConfig)
        viewModelScope.launch { repository.saveConfig(newConfig) }
    }

    fun testSignal() {
        val config = _uiState.value.config
        com.example.intervaltimer.wear.vibration.WatchVibrationPlayer.play(getApplication(), config.vibrationPattern)
    }
}
