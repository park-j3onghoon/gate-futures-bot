package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Position
import kotlin.math.abs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertFalse(condition.evaluate(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun `EntryCondition LT returns false at exact boundary`() {
        val prices = List(20) { 100.0 }
        // RSI of flat prices == 50.0
        val ltAt50 = EntryCondition(Indicator.RSI, ComparisonOp.LT, 50.0, period = 14)
        val lteAt50 = EntryCondition(Indicator.RSI, ComparisonOp.LTE, 50.0, period = 14)
        assertFalse(ltAt50.evaluate(prices))
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

    @Test
    fun `TakeProfitPct triggers when long PnL reaches threshold exactly`() {
        val position = longPosition(entry = 100.0, size = 1L)
        val condition = ExitCondition.TakeProfitPct(5.0)
        assertTrue(condition.evaluate(prices = listOf(105.0), position = position))
        assertFalse(condition.evaluate(prices = listOf(104.99), position = position))
    }

    @Test
    fun `TakeProfitPct uses inverse sign for short position`() {
        val shortPos = shortPosition(entry = 100.0, size = -1L)
        val condition = ExitCondition.TakeProfitPct(5.0)
        // 숏: 가격 하락 시 이익
        assertTrue(condition.evaluate(prices = listOf(95.0), position = shortPos))
        assertFalse(condition.evaluate(prices = listOf(96.0), position = shortPos))
        // 숏에서 가격 상승은 손실 → TakeProfit false
        assertFalse(condition.evaluate(prices = listOf(105.0), position = shortPos))
    }

    @Test
    fun `StopLossPct triggers on long loss`() {
        val position = longPosition(entry = 100.0, size = 1L)
        val condition = ExitCondition.StopLossPct(3.0)
        assertTrue(condition.evaluate(prices = listOf(97.0), position = position))
        assertFalse(condition.evaluate(prices = listOf(98.0), position = position))
    }

    @Test
    fun `StopLossPct triggers on short loss (price rising)`() {
        val shortPos = shortPosition(entry = 100.0, size = -1L)
        val condition = ExitCondition.StopLossPct(3.0)
        // 숏: 가격 상승 시 손실
        assertTrue(condition.evaluate(prices = listOf(103.0), position = shortPos))
        assertFalse(condition.evaluate(prices = listOf(102.0), position = shortPos))
    }

    @Test
    fun `IndicatorExit rejects non-positive period`() {
        assertThrows<IllegalArgumentException> {
            ExitCondition.IndicatorExit(Indicator.RSI, ComparisonOp.GT, 70.0, period = 0)
        }
    }

    @Test
    fun `IndicatorExit triggers on RSI overbought`() {
        val prices = (1..20).map { 100.0 + it.toDouble() }
        val position = longPosition(entry = 100.0, size = 1L)
        val condition = ExitCondition.IndicatorExit(Indicator.RSI, ComparisonOp.GT, 70.0, 14)
        assertTrue(condition.evaluate(prices, position))
    }

    @Test
    fun `evaluateExit returns None when no conditions configured`() {
        val strategy = TradingStrategy()
        val candles = candles(listOf(100.0, 105.0))
        val position = longPosition(entry = 100.0, size = 1L)
        assertEquals(ExitSignal.None, strategy.evaluateExit(candles, position))
    }

    @Test
    fun `evaluateExit returns None on empty candles`() {
        val strategy = TradingStrategy(exitConditions = listOf(ExitCondition.TakeProfitPct(5.0)))
        val position = longPosition(entry = 100.0, size = 1L)
        assertEquals(ExitSignal.None, strategy.evaluateExit(emptyList(), position))
    }

    @Test
    fun `evaluateExit returns Close when any condition triggers (OR)`() {
        val strategy = TradingStrategy(
            exitConditions = listOf(
                ExitCondition.TakeProfitPct(5.0),
                ExitCondition.StopLossPct(3.0)
            )
        )
        val position = longPosition(entry = 100.0, size = 1L)
        val candles = candles(listOf(100.0, 106.0))
        val signal = strategy.evaluateExit(candles, position)
        val close = assertIs<ExitSignal.Close>(signal)
        assertEquals(1, close.triggered.size)
        assertIs<ExitCondition.TakeProfitPct>(close.triggered.first())
    }

    @Test
    fun `evaluateExit returns Close with multiple triggered reasons`() {
        val strategy = TradingStrategy(
            exitConditions = listOf(
                ExitCondition.TakeProfitPct(5.0),
                ExitCondition.IndicatorExit(Indicator.RSI, ComparisonOp.GT, 50.0, 14)
            )
        )
        val prices = (1..20).map { 100.0 + it.toDouble() }
        val candles = candles(prices)
        val position = longPosition(entry = 100.0, size = 1L)
        val signal = strategy.evaluateExit(candles, position)
        val close = assertIs<ExitSignal.Close>(signal)
        assertEquals(2, close.triggered.size)
    }

    private fun longPosition(entry: Double, size: Long): Position = Position(
        contract = "BTC_USDT",
        size = size,
        entryPrice = entry.toString(),
        leverage = 5,
        unrealisedPnl = "0",
        realisedPnl = "0"
    )

    private fun shortPosition(entry: Double, size: Long): Position =
        longPosition(entry, size).copy(size = -abs(size))

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
