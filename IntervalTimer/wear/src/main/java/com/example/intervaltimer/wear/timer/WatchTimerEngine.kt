package com.example.intervaltimer.wear.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.shared.model.TimerSnapshot
import com.example.intervaltimer.shared.model.TimerStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standalone watch-side timer engine (spec §14/C: "Óra önálló működése").
 *
 * We DID implement this in v1, rather than deferring it, because the mechanism is
 * identical to the phone's (AlarmManager.setExactAndAllowWhileIdle, chained one-at-a-time,
 * no loop) — there was no technical reason to withhold it. It only activates when the
 * user explicitly starts a timer from the watch UI itself (as opposed to a phone-driven
 * session mirrored via WearMessagePaths.STATE_SYNC).
 *
 * Watch-initiated and phone-initiated timers are mutually exclusive in this v1: starting
 * one on the watch does not also start one on the phone, and vice versa — they are two
 * independent timer instances, matching modes B and C from the spec rather than merging them.
 */
object WatchTimerEngine {

    private const val REQUEST_CODE_ALARM = 2001

    private val _snapshot = MutableStateFlow(
        TimerSnapshot(runState = TimerRunState.IDLE, config = TimerConfig.DEFAULT, config2 = TimerConfig.DEFAULT2, nextTriggerElapsedRealtime = null)
    )
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    fun currentState(): TimerRunState = _snapshot.value.runState

    fun start(context: Context, config: TimerConfig, config2: TimerConfig = _snapshot.value.config2) {
        val current = _snapshot.value
        check(TimerStateMachine.isValidTransition(current.runState, TimerRunState.RUNNING)) {
            "Invalid transition ${current.runState} -> RUNNING"
        }
        require(config.isValid())
        require(config2.isValid())

        val intervalToUse = current.remainingMillisAtPause?.takeIf { current.runState == TimerRunState.PAUSED }
            ?: current.activeConfig.intervalMillis
        scheduleNext(context, config, config2, current.nextTimerIndex, intervalToUse)
    }

    fun pause(context: Context) {
        val current = _snapshot.value
        if (!TimerStateMachine.isValidTransition(current.runState, TimerRunState.PAUSED)) return
        val remaining = (current.nextTriggerElapsedRealtime ?: SystemClock.elapsedRealtime())
            .minus(SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        cancelAlarm(context)
        _snapshot.value = current.copy(
            runState = TimerRunState.PAUSED,
            nextTriggerElapsedRealtime = null,
            remainingMillisAtPause = remaining
        )
    }

    fun stop(context: Context) {
        cancelAlarm(context)
        _snapshot.value = _snapshot.value.copy(
            runState = TimerRunState.STOPPED,
            nextTriggerElapsedRealtime = null,
            remainingMillisAtPause = null
        )
    }

    fun onSignalFired(context: Context) {
        val current = _snapshot.value
        if (current.runState != TimerRunState.RUNNING) return
        val nextIndex = 1 - current.nextTimerIndex
        val nextConfig = if (nextIndex == 0) current.config else current.config2
        scheduleNext(context, current.config, current.config2, nextIndex, nextConfig.intervalMillis)
    }

    fun remainingMillisNow(): Long? {
        val next = _snapshot.value.nextTriggerElapsedRealtime ?: return null
        return (next - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun scheduleNext(context: Context, config: TimerConfig, config2: TimerConfig, nextTimerIndex: Int, intervalMillis: Long) {
        val triggerAt = SystemClock.elapsedRealtime() + intervalMillis
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmPendingIntent(context))

        _snapshot.value = _snapshot.value.copy(
            runState = TimerRunState.RUNNING,
            config = config,
            config2 = config2,
            nextTimerIndex = nextTimerIndex,
            nextTriggerElapsedRealtime = triggerAt,
            remainingMillisAtPause = null
        )
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_ALARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
