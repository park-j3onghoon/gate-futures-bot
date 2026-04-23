package com.parkj3onghoon.gatefuturesbot.admin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KillSwitchTest {
    @Test
    fun `not tripped by default`() {
        val ks = KillSwitch()
        assertFalse(ks.isTripped())
        assertNull(ks.tripReason())
    }

    @Test
    fun `trip activates and records reason`() {
        val ks = KillSwitch()
        val first = ks.trip("manual test")
        assertTrue(first, "첫 trip은 true 반환")
        assertTrue(ks.isTripped())
        assertEquals("manual test", ks.tripReason())
    }

    @Test
    fun `trip returns false when already tripped`() {
        val ks = KillSwitch()
        ks.trip("first")
        val second = ks.trip("second")
        assertFalse(second, "이미 trip 상태면 false")
    }

    @Test
    fun `reset deactivates`() {
        val ks = KillSwitch()
        ks.trip("x")
        val ok = ks.reset()
        assertTrue(ok)
        assertFalse(ks.isTripped())
        assertNull(ks.tripReason())
    }

    @Test
    fun `reset returns false when already not tripped`() {
        val ks = KillSwitch()
        assertFalse(ks.reset())
    }
}
