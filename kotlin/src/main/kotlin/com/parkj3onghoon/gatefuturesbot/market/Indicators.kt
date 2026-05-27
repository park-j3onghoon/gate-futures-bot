package com.parkj3onghoon.gatefuturesbot.market

import kotlin.math.abs
import kotlin.math.max

private const val FLOAT_EPSILON = 1e-12

fun calculateSma(
    prices: List<Double>,
    period: Int,
): Double? {
    require(period > 0) { "period must be positive: $period" }
    if (prices.size < period) return null
    return prices.takeLast(period).average()
}

fun calculateEma(
    prices: List<Double>,
    period: Int,
): Double? {
    require(period > 0) { "period must be positive: $period" }
    if (prices.size < period) return null

    val multiplier = 2.0 / (period + 1)
    var ema = prices.take(period).average()
    for (i in period until prices.size) {
        ema = (prices[i] - ema) * multiplier + ema
    }
    return ema
}

fun calculateRsi(
    prices: List<Double>,
    period: Int = 14,
): Double? {
    require(period > 0) { "period must be positive: $period" }
    if (prices.size <= period) return null

    var avgGain = 0.0
    var avgLoss = 0.0
    for (i in 1..period) {
        val change = prices[i] - prices[i - 1]
        if (change >= 0) avgGain += change else avgLoss -= change
    }
    avgGain /= period
    avgLoss /= period

    for (i in period + 1 until prices.size) {
        val change = prices[i] - prices[i - 1]
        val gain = max(change, 0.0)
        val loss = max(-change, 0.0)
        avgGain = (avgGain * (period - 1) + gain) / period
        avgLoss = (avgLoss * (period - 1) + loss) / period
    }

    val lossIsZero = abs(avgLoss) < FLOAT_EPSILON
    val gainIsZero = abs(avgGain) < FLOAT_EPSILON
    if (lossIsZero) return if (gainIsZero) 50.0 else 100.0
    val rs = avgGain / avgLoss
    return 100.0 - (100.0 / (1.0 + rs))
}
