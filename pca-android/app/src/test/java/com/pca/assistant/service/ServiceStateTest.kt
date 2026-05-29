package com.pca.assistant.service

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStateTest {

    @Test fun `defaults to STOPPED`() {
        assertEquals(ListeningState.STOPPED, ServiceState().state.value)
    }

    @Test fun `update emits the new state`() = runTest {
        val s = ServiceState()
        s.state.test {
            assertEquals(ListeningState.STOPPED, awaitItem())
            s.update(ListeningState.LISTENING)
            assertEquals(ListeningState.LISTENING, awaitItem())
            s.update(ListeningState.PAUSED)
            assertEquals(ListeningState.PAUSED, awaitItem())
        }
    }

    @Test fun `setting the same state does not double-emit`() = runTest {
        val s = ServiceState()
        s.update(ListeningState.LISTENING)
        s.state.test {
            assertEquals(ListeningState.LISTENING, awaitItem())
            s.update(ListeningState.LISTENING)
            // MutableStateFlow deduplicates equal values — Turbine will time
            // out on awaitItem() if there's no second emit, which is what
            // we want.
            expectNoEvents()
        }
    }
}
