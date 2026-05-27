package com.parkj3onghoon.gatefuturesbot.risk

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RiskManagerTest {
    private val props =
        RiskProperties(
            maxOpenPositions = 2,
            dailyLossLimitPct = 5.0,
            consecutiveFailureThreshold = 3,
        )

    @Test
    fun `allows entry when under all thresholds`() {
        val rm = RiskManager(props)
        assertIs<RiskDecision.Allow>(rm.canOpenPosition("BTC_USDT"))
    }

    @Test
    fun `denies when max open positions reached`() {
        val rm = RiskManager(props)
        rm.onPositionOpened("BTC_USDT")
        rm.onPositionOpened("ETH_USDT")

        val decision = rm.canOpenPosition("SOL_USDT")
        val deny = assertIs<RiskDecision.Deny>(decision)
        assertTrue(deny.reason.contains("최대 포지션 수"))
    }

    @Test
    fun `allows again after position closed`() {
        val rm = RiskManager(props)
        rm.onPositionOpened("BTC_USDT")
        rm.onPositionOpened("ETH_USDT")
        rm.onPositionClosed("BTC_USDT", realizedPnlPct = 2.0)

        assertIs<RiskDecision.Allow>(rm.canOpenPosition("SOL_USDT"))
    }

    @Test
    fun `denies when daily loss limit exceeded`() {
        val rm = RiskManager(props)
        rm.onPositionOpened("BTC_USDT")
        rm.onPositionClosed("BTC_USDT", realizedPnlPct = -3.0)
        rm.onPositionOpened("ETH_USDT")
        rm.onPositionClosed("ETH_USDT", realizedPnlPct = -3.0)
        // 누적 손실률 6% > 5% 한도

        val deny = assertIs<RiskDecision.Deny>(rm.canOpenPosition("SOL_USDT"))
        assertTrue(deny.reason.contains("일일 손실"))
    }

    @Test
    fun `profitable trades do not add to loss`() {
        val rm = RiskManager(props)
        rm.onPositionOpened("BTC_USDT")
        rm.onPositionClosed("BTC_USDT", realizedPnlPct = 10.0) // 이익

        assertIs<RiskDecision.Allow>(rm.canOpenPosition("BTC_USDT"))
        assertEquals(0.0, rm.snapshot().dailyLossPct)
    }

    @Test
    fun `consecutive failures block specific contract only`() {
        val rm = RiskManager(props)
        repeat(3) { rm.onOrderFailure("BTC_USDT") }

        val btcDecision = assertIs<RiskDecision.Deny>(rm.canOpenPosition("BTC_USDT"))
        assertTrue(btcDecision.reason.contains("연속 실패"))

        // 다른 contract는 영향 없음
        assertIs<RiskDecision.Allow>(rm.canOpenPosition("ETH_USDT"))
    }

    @Test
    fun `successful open resets consecutive failure counter`() {
        val rm = RiskManager(props)
        rm.onOrderFailure("BTC_USDT")
        rm.onOrderFailure("BTC_USDT")
        rm.onPositionOpened("BTC_USDT") // 성공

        assertEquals(0, rm.snapshot().failureCounts["BTC_USDT"])
    }

    @Test
    fun `daily loss resets when date changes`() {
        // day1 → day2 시뮬레이션 (Clock을 하루 앞으로)
        val day1 = Instant.parse("2026-04-24T00:00:00Z")
        val clock1 = Clock.fixed(day1, ZoneId.of("UTC"))
        val rm = RiskManager(props, clock1)
        rm.onPositionOpened("BTC_USDT")
        rm.onPositionClosed("BTC_USDT", realizedPnlPct = -4.0)
        assertEquals(4.0, rm.snapshot().dailyLossPct)

        // 다음날 Clock으로 교체 후 조회 → 리셋되어야 함
        val day2 = Instant.parse("2026-04-25T00:00:00Z")
        val clock2 = Clock.fixed(day2, ZoneId.of("UTC"))
        val rm2 = RiskManager(props, clock2)
        assertEquals(0.0, rm2.snapshot().dailyLossPct)
    }

    @Test
    fun `snapshot exposes all counters`() {
        val rm = RiskManager(props)
        rm.onPositionOpened("BTC_USDT")
        rm.onOrderFailure("ETH_USDT")

        val snap = rm.snapshot()
        assertEquals(1, snap.openPositions)
        assertEquals(1, snap.failureCounts["ETH_USDT"])
    }
}
