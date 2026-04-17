package com.parkj3onghoon.gatefuturesbot.bootstrap

import com.parkj3onghoon.gatefuturesbot.config.ContractStrategySpec
import com.parkj3onghoon.gatefuturesbot.config.EntryConditionSpec
import com.parkj3onghoon.gatefuturesbot.config.ExitConditionSpec
import com.parkj3onghoon.gatefuturesbot.config.ExitType
import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Position
import com.parkj3onghoon.gatefuturesbot.strategy.EntrySignal
import com.parkj3onghoon.gatefuturesbot.strategy.ExitCondition
import com.parkj3onghoon.gatefuturesbot.strategy.ExitSignal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StrategyAssemblerTest {

    @Test
    fun `returns empty strategy for unknown contract`() {
        val assembler = StrategyAssembler(StrategyProperties(emptyMap()))
        val strategy = assembler.forContract("BTC_USDT")
        assertEquals(EntrySignal.None, strategy.evaluateEntry(flatCandles()))
    }

    @Test
    fun `maps long-entry RSI spec to EntryCondition`() {
        val assembler = StrategyAssembler(
            StrategyProperties(
                contracts = mapOf(
                    "BTC_USDT" to ContractStrategySpec(
                        longEntries = listOf(
                            EntryConditionSpec(Indicator.RSI, ComparisonOp.LT, 30.0, 14)
                        )
                    )
                )
            )
        )
        val strategy = assembler.forContract("BTC_USDT")
        val falling = (1..20).map { 25.0 - it * 0.5 }
        val candles = falling.mapIndexed { i, c -> candle(1700000000L + i * 60, c) }
        assertIs<EntrySignal.Long>(strategy.evaluateEntry(candles))
    }

    @Test
    fun `maps TAKE_PROFIT_PCT spec to ExitCondition TakeProfitPct`() {
        val assembler = StrategyAssembler(
            StrategyProperties(
                contracts = mapOf(
                    "BTC_USDT" to ContractStrategySpec(
                        exitConditions = listOf(ExitConditionSpec(ExitType.TAKE_PROFIT_PCT, pct = 5.0))
                    )
                )
            )
        )
        val strategy = assembler.forContract("BTC_USDT")
        val position = Position("BTC_USDT", 1L, "100", 5, "0", "0")
        val signal = strategy.evaluateExit(listOf(candle(1L, 105.0)), position)
        val close = assertIs<ExitSignal.Close>(signal)
        assertIs<ExitCondition.TakeProfitPct>(close.triggered.first())
    }

    @Test
    fun `maps INDICATOR exit spec to IndicatorExit`() {
        val assembler = StrategyAssembler(
            StrategyProperties(
                contracts = mapOf(
                    "BTC_USDT" to ContractStrategySpec(
                        exitConditions = listOf(
                            ExitConditionSpec(
                                type = ExitType.INDICATOR,
                                indicator = Indicator.RSI,
                                operator = ComparisonOp.GT,
                                value = 70.0,
                                period = 14
                            )
                        )
                    )
                )
            )
        )
        val strategy = assembler.forContract("BTC_USDT")
        val rising = (1..20).map { 100.0 + it.toDouble() }
        val candles = rising.mapIndexed { i, c -> candle(1700000000L + i * 60, c) }
        val position = Position("BTC_USDT", 1L, "100", 5, "0", "0")
        val signal = strategy.evaluateExit(candles, position)
        val close = assertIs<ExitSignal.Close>(signal)
        assertIs<ExitCondition.IndicatorExit>(close.triggered.first())
    }

    @Test
    fun `throws when TAKE_PROFIT_PCT spec missing pct`() {
        val assembler = StrategyAssembler(
            StrategyProperties(
                contracts = mapOf(
                    "BTC_USDT" to ContractStrategySpec(
                        exitConditions = listOf(ExitConditionSpec(ExitType.TAKE_PROFIT_PCT))
                    )
                )
            )
        )
        assertThrows<IllegalArgumentException> { assembler.forContract("BTC_USDT") }
    }

    @Test
    fun `throws when INDICATOR spec missing indicator`() {
        val assembler = StrategyAssembler(
            StrategyProperties(
                contracts = mapOf(
                    "BTC_USDT" to ContractStrategySpec(
                        exitConditions = listOf(
                            ExitConditionSpec(ExitType.INDICATOR, operator = ComparisonOp.GT, value = 70.0)
                        )
                    )
                )
            )
        )
        assertThrows<IllegalArgumentException> { assembler.forContract("BTC_USDT") }
    }

    private fun flatCandles(): List<Candle> =
        (0 until 20).map { candle(1700000000L + it * 60, 100.0) }

    private fun candle(ts: Long, close: Double): Candle = Candle(
        timestamp = ts,
        open = close.toString(),
        high = close.toString(),
        low = close.toString(),
        close = close.toString(),
        volume = 0L
    )
}
