package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
import com.parkj3onghoon.gatefuturesbot.market.evaluateIndicator

data class EntryCondition(
    val indicator: Indicator,
    val operator: ComparisonOp,
    val value: Double,
    val period: Int = 14,
) {
    init {
        require(period > 0) { "period must be positive: $period" }
    }

    fun evaluate(prices: List<Double>): Boolean = evaluateIndicator(indicator, operator, value, period, prices)
}
