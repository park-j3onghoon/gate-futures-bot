package com.parkj3onghoon.gatefuturesbot.position

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionTrackerTest {
    @Test
    fun `initial state is IDLE`() {
        val t = PositionTracker()
        assertEquals(PositionState.IDLE, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `happy path IDLE to OPEN to CLOSED`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        t.transition("BTC_USDT", PositionState.OPEN)
        t.transition("BTC_USDT", PositionState.CLOSING)
        t.transition("BTC_USDT", PositionState.CLOSED)
        assertEquals(PositionState.CLOSED, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `invalid transition throws`() {
        val t = PositionTracker()
        assertThrows<IllegalArgumentException> {
            t.transition("BTC_USDT", PositionState.OPEN) // IDLE -> OPEN 직접 금지
        }
    }

    @Test
    fun `same state transition returns false`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        assertFalse(t.transition("BTC_USDT", PositionState.OPENING))
    }

    @Test
    fun `OPENING to FAILED allowed`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        t.transition("BTC_USDT", PositionState.FAILED, reason = "order rejected")
        assertEquals(PositionState.FAILED, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `FAILED can reset to IDLE`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        t.transition("BTC_USDT", PositionState.FAILED)
        t.resetToIdle("BTC_USDT")
        assertEquals(PositionState.IDLE, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `reconcile detects external close when OPEN but exchange empty`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        t.transition("BTC_USDT", PositionState.OPEN)

        t.reconcile("BTC_USDT", exchangeHasPosition = false)
        assertEquals(PositionState.CLOSED, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `reconcile transitions OPENING to FAILED after timeout`() {
        val initialInstant = Instant.parse("2026-04-24T00:00:00Z")
        val clock =
            object : Clock() {
                var now = initialInstant

                override fun instant() = now

                override fun getZone() = ZoneId.of("UTC")

                override fun withZone(zone: ZoneId) = this
            }

        val t = PositionTracker(clock)
        t.transition("BTC_USDT", PositionState.OPENING)

        // Clock advances by 40 seconds (> 30s threshold)
        clock.now = initialInstant.plusSeconds(40)
        t.reconcile("BTC_USDT", exchangeHasPosition = false, failOpeningAfter = Duration.ofSeconds(30))
        assertEquals(PositionState.FAILED, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `reconcile does not fail OPENING within timeout window`() {
        val t = PositionTracker()
        t.transition("BTC_USDT", PositionState.OPENING)
        // 방금 OPENING 상태가 되었으므로 timeout 아직 안 됨
        t.reconcile("BTC_USDT", exchangeHasPosition = false, failOpeningAfter = Duration.ofSeconds(30))
        assertEquals(PositionState.OPENING, t.stateOf("BTC_USDT"))
    }

    @Test
    fun `canOpen is true for IDLE and CLOSED`() {
        assertTrue(PositionState.IDLE.canOpen())
        assertTrue(PositionState.CLOSED.canOpen())
        assertFalse(PositionState.OPENING.canOpen())
        assertFalse(PositionState.OPEN.canOpen())
    }

    @Test
    fun `snapshot returns all tracked contracts`() {
        val t = PositionTracker()
        t.transition("BTC", PositionState.OPENING)
        t.transition("ETH", PositionState.OPENING)
        t.transition("ETH", PositionState.OPEN)

        val snap = t.snapshot()
        assertEquals(2, snap.size)
        assertEquals(PositionState.OPENING, snap["BTC"]?.state)
        assertEquals(PositionState.OPEN, snap["ETH"]?.state)
    }
}
