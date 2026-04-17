package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.model.Candle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TradingStrategyTest {

    @Test
    fun `EntryCondition throws on non-positive period`() {
        assertThrows<IllegalArgumentException> {
            EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, period = 0)
        }
    }

    @Test
    fun `EntryCondition evaluates RSI below threshold as true for oversold`() {
        // 20개 가격이 꾸준히 하락 → RSI 낮음
        val prices = (1..20).map { (25 - it * 0.5) }
        val condition = EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, period = 14)
        assertTrue(condition.evaluate(prices))
    }

    @Test
    fun `EntryCondition evaluates false when prices shorter than period`() {
        val condition = EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, period = 14)
        assertTrue(!condition.evaluate(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun `EntryCondition LT returns false at exact boundary`() {
        val prices = List(20) { 100.0 }
        // RSI of flat prices == 50.0
        val ltAt50 = EntryCondition(Indicator.RSI, ComparisonOp.LT, 50.0, period = 14)
        val lteAt50 = EntryCondition(Indicator.RSI, ComparisonOp.LTE, 50.0, period = 14)
        assertTrue(!ltAt50.evaluate(prices))
        assertTrue(lteAt50.evaluate(prices))
    }

    @Test
    fun `EntryCondition PRICE uses last close`() {
        val prices = listOf(100.0, 110.0, 120.0)
        val cond = EntryCondition(Indicator.PRICE, ComparisonOp.GT, 115.0)
        assertTrue(cond.evaluate(prices))
    }

    @Test
    fun `evaluateEntry returns Long when all long conditions meet`() {
        val candles = candles((1..20).map { (25 - it * 0.5) })
        val strategy = TradingStrategy(
            longEntries = listOf(
                EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, period = 14)
            )
        )
        val signal = strategy.evaluateEntry(candles)
        val longSignal = assertIs<EntrySignal.Long>(signal)
        assertEquals(1, longSignal.matched.size)
    }

    @Test
    fun `evaluateEntry returns None when not all long conditions meet`() {
        val candles = candles((1..20).map { 100.0 })
        val strategy = TradingStrategy(
            longEntries = listOf(
                EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, period = 14),
                EntryCondition(Indicator.PRICE, ComparisonOp.LT, 50.0)
            )
        )
        assertEquals(EntrySignal.None, strategy.evaluateEntry(candles))
    }

    @Test
    fun `evaluateEntry returns Short when all short conditions meet`() {
        // 꾸준한 상승 → RSI 높음 + PRICE 120 > 115
        val candles = candles((1..20).map { 100.0 + it.toDouble() })
        val strategy = TradingStrategy(
            shortEntries = listOf(
                EntryCondition(Indicator.RSI, ComparisonOp.GT, 70.0, period = 14),
                EntryCondition(Indicator.PRICE, ComparisonOp.GT, 115.0)
            )
        )
        val signal = strategy.evaluateEntry(candles)
        assertIs<EntrySignal.Short>(signal)
    }

    @Test
    fun `evaluateEntry prefers Long when both long and short conditions trigger`() {
        val candles = candles((1..20).map { 100.0 })
        // long: RSI<=50 (true flat), short: RSI>=50 (true flat)
        val strategy = TradingStrategy(
            longEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.LTE, 50.0, 14)),
            shortEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.GTE, 50.0, 14))
        )
        assertIs<EntrySignal.Long>(strategy.evaluateEntry(candles))
    }

    @Test
    fun `evaluateEntry returns None on empty candles`() {
        val strategy = TradingStrategy(
            longEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0))
        )
        assertEquals(EntrySignal.None, strategy.evaluateEntry(emptyList()))
    }

    @Test
    fun `evaluateEntry returns None when both entries empty`() {
        val candles = candles((1..20).map { 100.0 })
        val strategy = TradingStrategy()
        assertEquals(EntrySignal.None, strategy.evaluateEntry(candles))
    }

    private fun candles(closes: List<Double>): List<Candle> = closes.mapIndexed { i, c ->
        Candle(
            timestamp = 1700000000L + i * 60,
            open = c.toString(),
            high = c.toString(),
            low = c.toString(),
            close = c.toString(),
            volume = 0L
        )
    }
}
