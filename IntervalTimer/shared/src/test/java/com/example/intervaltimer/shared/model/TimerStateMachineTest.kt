package com.example.intervaltimer.shared.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStateMachineTest {

    @Test
    fun `idle to running is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.IDLE, TimerRunState.RUNNING))

    @Test
    fun `running to paused is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.RUNNING, TimerRunState.PAUSED))

    @Test
    fun `paused to running (resume) is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.PAUSED, TimerRunState.RUNNING))

    @Test
    fun `running to stopped is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.RUNNING, TimerRunState.STOPPED))

    @Test
    fun `paused to stopped is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.PAUSED, TimerRunState.STOPPED))

    @Test
    fun `stopped to running (restart) is allowed`() =
        assertTrue(TimerStateMachine.isValidTransition(TimerRunState.STOPPED, TimerRunState.RUNNING))

    @Test
    fun `idle to paused is not allowed`() =
        assertFalse(TimerStateMachine.isValidTransition(TimerRunState.IDLE, TimerRunState.PAUSED))

    @Test
    fun `idle to stopped is not allowed`() =
        assertFalse(TimerStateMachine.isValidTransition(TimerRunState.IDLE, TimerRunState.STOPPED))

    @Test
    fun `stopped to paused is not allowed`() =
        assertFalse(TimerStateMachine.isValidTransition(TimerRunState.STOPPED, TimerRunState.PAUSED))

    @Test
    fun `self transition is not allowed`() =
        assertFalse(TimerStateMachine.isValidTransition(TimerRunState.RUNNING, TimerRunState.RUNNING))
}
