package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.market.calculateEma
import com.parkj3onghoon.gatefuturesbot.market.calculateRsi
import com.parkj3onghoon.gatefuturesbot.market.calculateSma

enum class Indicator { RSI, SMA, EMA, PRICE }

enum class ComparisonOp { LT, LTE, GT, GTE }

data class EntryCondition(
    val indicator: Indicator,
    val operator: ComparisonOp,
    val value: Double,
    val period: Int = 14
) {
    init {
        require(period > 0) { "period must be positive: $period" }
    }

    fun evaluate(prices: List<Double>): Boolean {
        val current: Double = when (indicator) {
            Indicator.RSI -> calculateRsi(prices, period) ?: return false
            Indicator.SMA -> calculateSma(prices, period) ?: return false
            Indicator.EMA -> calculateEma(prices, period) ?: return false
            Indicator.PRICE -> prices.lastOrNull() ?: return false
        }
        return when (operator) {
            ComparisonOp.LT -> current < value
            ComparisonOp.LTE -> current <= value
            ComparisonOp.GT -> current > value
            ComparisonOp.GTE -> current >= value
        }
    }
}
