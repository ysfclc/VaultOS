package com.vaultos.app.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRequestGateTest {
    @Test
    fun newestRequestInvalidatesOlderRequest() {
        val gate = SuggestionRequestGate()
        val older = gate.issue()
        val newer = gate.issue()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
    }

    @Test
    fun editorLifecycleInvalidationRejectsPendingResult() {
        val gate = SuggestionRequestGate()
        val pending = gate.issue()

        gate.invalidate()

        assertFalse(gate.isCurrent(pending))
    }

    @Test
    fun resultRemainsCurrentUntilNextRenderOrInvalidation() {
        val gate = SuggestionRequestGate()
        val request = gate.issue()

        assertTrue(gate.isCurrent(request))
        assertTrue(gate.isCurrent(request))
        gate.invalidate()
        assertFalse(gate.isCurrent(request))
    }
}
