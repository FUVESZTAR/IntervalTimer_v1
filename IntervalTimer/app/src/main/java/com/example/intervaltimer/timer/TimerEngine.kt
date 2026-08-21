package com.example.intervaltimer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.intervaltimer.BuildConfig
import com.example.intervaltimer.settings.SettingsRepository
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRestoreCalculator
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.shared.model.TimerSnapshot
import com.example.intervaltimer.shared.model.TimerStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Core interval-timer engine.
 *
 * Design (see README "Timing mechanism" for the full rationale):
 * - Every "next signal" is a single AlarmManager.setExactAndAllowWhileIdle() alarm.
 * - When it fires, TimerAlarmReceiver plays the signal AND immediately asks this
 *   engine to schedule the *next* one. There is no loop, no polling, no held wake lock
 *   beyond the brief moment needed to play the signal.
 * - elapsedRealtime() (monotonic, survives wall-clock/timezone/DST changes) is used
 *   for all "how long until next" math; only the final AlarmManager call needs a
 *   trigger time, which we express in elapsedRealtime terms via ELAPSED_REALTIME_WAKEUP.
 *
 * This class is a plain Kotlin singleton-per-process; TimerForegroundService keeps the
 * process alive while RUNNING/PAUSED so this object's in-memory state persists, and
 * SettingsRepository persists just enough (state + next trigger) to recover after
 * process death or reboot.
 */
object TimerEngine {

    private const val TAG = "TimerEngine"
    private const val REQUEST_CODE_ALARM = 1001
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val hydrationMutex = Mutex()
    @Volatile private var settingsRepository: SettingsRepository? = null

    private val _snapshot = MutableStateFlow(
        TimerSnapshot(runState = TimerRunState.IDLE, config = TimerConfig.DEFAULT, nextTriggerElapsedRealtime = null)
    )
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    fun currentState(): TimerRunState = _snapshot.value.runState

    /** IDLE|STOPPED -> RUNNING, or resume from PAUSED. */
    fun start(context: Context, config: TimerConfig) {
        val current = _snapshot.value
        check(TimerStateMachine.isValidTransition(current.runState, TimerRunState.RUNNING)) {
            "Invalid transition ${current.runState} -> RUNNING"
        }
        require(config.isValid()) { "Invalid interval: ${config.intervalMillis}" }

        val intervalToUse = current.remainingMillisAtPause?.takeIf { current.runState == TimerRunState.PAUSED }
            ?: config.intervalMillis

        scheduleNext(context, config, intervalToUse)
        log("Timer started. interval=${config.intervalMillis}ms")
    }

    /** Rebuilds the in-memory state after process death, without scheduling a duplicate alarm. */
    suspend fun hydrateFromPersistence(context: Context) {
        hydrationMutex.withLock {
            if (_snapshot.value.runState != TimerRunState.IDLE) return@withLock

            val repository = repository(context)
            val config = repository.configFlow.first()
            val persisted = repository.readPersistedTimerRuntime()
            when (persisted.runState) {
                TimerRunState.RUNNING -> {
                    val nextTriggerWallClockMillis = persisted.nextTriggerWallClockMillis
                    if (nextTriggerWallClockMillis == null) {
                        _snapshot.value = TimerSnapshot(
                            runState = TimerRunState.STOPPED,
                            config = config,
                            nextTriggerElapsedRealtime = null
                        )
                    } else {
                        _snapshot.value = TimerSnapshot(
                            runState = TimerRunState.RUNNING,
                            config = config,
                            nextTriggerElapsedRealtime = SystemClock.elapsedRealtime() +
                                TimerRestoreCalculator.remainingForHydration(
                                    nowWallClockMillis = System.currentTimeMillis(),
                                    nextTriggerWallClockMillis = nextTriggerWallClockMillis
                                )
                        )
                    }
                }
                TimerRunState.PAUSED -> {
                    _snapshot.value = TimerSnapshot(
                        runState = TimerRunState.PAUSED,
                        config = config,
                        nextTriggerElapsedRealtime = null,
                        remainingMillisAtPause = persisted.pausedRemainingMillis ?: config.intervalMillis
                    )
                }
                TimerRunState.STOPPED -> {
                    _snapshot.value = TimerSnapshot(
                        runState = TimerRunState.STOPPED,
                        config = config,
                        nextTriggerElapsedRealtime = null
                    )
                }
                TimerRunState.IDLE -> {
                    _snapshot.value = TimerSnapshot(
                        runState = TimerRunState.IDLE,
                        config = config,
                        nextTriggerElapsedRealtime = null
                    )
                }
            }
        }
    }

    fun restoreAfterBoot(context: Context, config: TimerConfig, nextTriggerWallClockMillis: Long?) {
        val remainingMillis = TimerRestoreCalculator.remainingForReschedule(
            nowWallClockMillis = System.currentTimeMillis(),
            nextTriggerWallClockMillis = nextTriggerWallClockMillis,
            fallbackIntervalMillis = config.intervalMillis
        )
        scheduleNext(context, config, remainingMillis)
        log("Timer restored after boot. remaining=${remainingMillis}ms")
    }

    /** RUNNING -> PAUSED. Cancels the pending alarm and remembers remaining time. */
    fun pause(context: Context) {
        val current = _snapshot.value
        if (!TimerStateMachine.isValidTransition(current.runState, TimerRunState.PAUSED)) return

        val remaining = (current.nextTriggerElapsedRealtime ?: SystemClock.elapsedRealtime())
            .minus(SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)

        cancelAlarm(context)
        _snapshot.value = current.copy(
            runState = TimerRunState.PAUSED,
            nextTriggerElapsedRealtime = null,
            remainingMillisAtPause = remaining
        )
        persistSnapshot(context)
        log("Timer paused. remaining=${remaining}ms")
    }

    /** RUNNING|PAUSED -> STOPPED. No further signals will fire. */
    fun stop(context: Context) {
        cancelAlarm(context)
        _snapshot.value = _snapshot.value.copy(
            runState = TimerRunState.STOPPED,
            nextTriggerElapsedRealtime = null,
            remainingMillisAtPause = null
        )
        persistSnapshot(context)
        log("Timer stopped")
    }

    /** Called by TimerAlarmReceiver when an alarm actually fires. Reschedules the next one. */
    fun onSignalFired(context: Context) {
        val current = _snapshot.value
        if (current.runState != TimerRunState.RUNNING) {
            // Defensive: a stray alarm arrived after STOP/PAUSE raced with delivery. Ignore.
            log("Signal fired but state=${current.runState}; ignoring stray alarm")
            return
        }
        scheduleNext(context, current.config, current.config.intervalMillis)
    }

    fun updateConfigWhileIdle(config: TimerConfig) {
        val current = _snapshot.value
        if (current.runState == TimerRunState.RUNNING) return // don't mutate mid-flight; UI should block this
        _snapshot.value = current.copy(config = config)
    }

    private fun scheduleNext(context: Context, config: TimerConfig, intervalMillis: Long) {
        val triggerAt = SystemClock.elapsedRealtime() + intervalMillis
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent(context)

        // setExactAndAllowWhileIdle: fires at (close to) the exact requested time even
        // during Doze, at the cost of being rate-limited by the system for very frequent
        // repeats — acceptable trade-off since we only ever have ONE alarm pending at a time.
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)

        _snapshot.value = _snapshot.value.copy(
            runState = TimerRunState.RUNNING,
            config = config,
            nextTriggerElapsedRealtime = triggerAt,
            remainingMillisAtPause = null
        )
        persistSnapshot(context)
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Human-readable "next signal in mm:ss" for the UI/notification; recomputed on demand, not polled. */
    fun remainingMillisNow(): Long? {
        val next = _snapshot.value.nextTriggerElapsedRealtime ?: return null
        return (next - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun log(message: String) {
        if (BuildConfig.VERBOSE_LOGGING) Log.d(TAG, message)
    }

    private fun persistSnapshot(context: Context) {
        persistenceScope.launch {
            repository(context).persistSnapshot(_snapshot.value)
        }
    }

    private fun repository(context: Context): SettingsRepository {
        settingsRepository?.let { return it }
        return synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext).also {
                settingsRepository = it
            }
        }
    }
}
