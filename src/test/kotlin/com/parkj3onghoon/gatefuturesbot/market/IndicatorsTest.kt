package com.parkj3onghoon.gatefuturesbot.market

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IndicatorsTest {
    @Test
    fun `SMA returns average of last period values`() {
        val prices = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(4.0, calculateSma(prices, 3))
    }

    @Test
    fun `SMA returns null when prices shorter than period`() {
        assertNull(calculateSma(listOf(1.0, 2.0), 3))
    }

    @Test
    fun `SMA returns null on empty list`() {
        assertNull(calculateSma(emptyList(), 3))
    }

    @Test
    fun `SMA throws on non-positive period`() {
        assertThrows<IllegalArgumentException> { calculateSma(listOf(1.0), 0) }
        assertThrows<IllegalArgumentException> { calculateSma(listOf(1.0), -1) }
    }

    @Test
    fun `EMA returns value when enough data`() {
        val prices = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ema = calculateEma(prices, 3)
        assertNotNull(ema)
        // seed avg = (1+2+3)/3 = 2.0; mult = 2/4 = 0.5
        // step 4: (4-2)*0.5+2 = 3.0; step 5: (5-3)*0.5+3 = 4.0
        assertEquals(4.0, ema)
    }

    @Test
    fun `EMA returns null when prices shorter than period`() {
        assertNull(calculateEma(listOf(1.0, 2.0), 3))
    }

    @Test
    fun `EMA returns null on empty list`() {
        assertNull(calculateEma(emptyList(), 5))
    }

    @Test
    fun `RSI returns null when prices not longer than period`() {
        assertNull(calculateRsi(listOf(1.0, 2.0, 3.0), period = 3))
    }

    @Test
    fun `RSI returns null on empty list`() {
        assertNull(calculateRsi(emptyList(), period = 14))
    }

    @Test
    fun `RSI equals 100 when all prices strictly increase`() {
        val prices = (1..20).map { it.toDouble() }
        val rsi = calculateRsi(prices, period = 14)
        assertNotNull(rsi)
        assertEquals(100.0, rsi)
    }

    @Test
    fun `RSI equals 0 when all prices strictly decrease`() {
        val prices = (1..20).map { (21 - it).toDouble() }
        val rsi = calculateRsi(prices, period = 14)
        assertNotNull(rsi)
        assertEquals(0.0, rsi)
    }

    @Test
    fun `RSI returns 50 when no price changes`() {
        val prices = List(20) { 100.0 }
        val rsi = calculateRsi(prices, period = 14)
        assertNotNull(rsi)
        assertEquals(50.0, rsi)
    }

    @Test
    fun `RSI computes expected value for known sequence`() {
        val prices =
            listOf(
                44.34,
                44.09,
                44.15,
                43.61,
                44.33,
                44.83,
                45.10,
                45.42,
                45.84,
                46.08,
                45.89,
                46.03,
                45.61,
                46.28,
                46.28,
            )
        val rsi = calculateRsi(prices, period = 14)
        assertNotNull(rsi)
        assertEquals(70.46, Math.round(rsi * 100) / 100.0)
    }

    @Test
    fun `RSI throws on non-positive period`() {
        assertThrows<IllegalArgumentException> { calculateRsi(listOf(1.0, 2.0), 0) }
    }
}
