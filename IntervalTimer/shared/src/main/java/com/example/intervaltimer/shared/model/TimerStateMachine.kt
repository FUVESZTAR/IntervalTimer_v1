package com.example.intervaltimer.shared.model

/**
 * Formalizes the state graph described in spec §23, independent of Android/AlarmManager,
 * so it can be unit tested on the plain JVM without Robolectric/instrumentation.
 * TimerEngine (app module) consults this before mutating its state.
 */
object TimerStateMachine {

    private val allowedTransitions: Map<TimerRunState, Set<TimerRunState>> = mapOf(
        TimerRunState.IDLE to setOf(TimerRunState.RUNNING),
        TimerRunState.RUNNING to setOf(TimerRunState.PAUSED, TimerRunState.STOPPED),
        TimerRunState.PAUSED to setOf(TimerRunState.RUNNING, TimerRunState.STOPPED),
        TimerRunState.STOPPED to setOf(TimerRunState.RUNNING),
    )

    fun isValidTransition(from: TimerRunState, to: TimerRunState): Boolean {
        if (from == to) return false
        return allowedTransitions[from]?.contains(to) == true
    }
}
