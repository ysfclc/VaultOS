package com.vaultos.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardTouchTargetResolverTest {
    @Test
    fun choosesTheNearestLetterKeyCentre() {
        assertEquals(1, KeyboardTouchTargetResolver.nearestIndex(47f, floatArrayOf(20f, 50f, 80f), 26f))
    }

    @Test
    fun acceptsAKeyNearItsOuterTouchEdge() {
        assertEquals(0, KeyboardTouchTargetResolver.nearestIndex(43f, floatArrayOf(20f, 70f), 26f))
    }

    @Test
    fun rejectsTouchesOutsideTheLetterRowTolerance() {
        assertNull(KeyboardTouchTargetResolver.nearestIndex(100f, floatArrayOf(20f, 50f), 26f))
    }

    @Test
    fun handlesAnEmptyRow() {
        assertNull(KeyboardTouchTargetResolver.nearestIndex(20f, floatArrayOf(), 26f))
    }
}
