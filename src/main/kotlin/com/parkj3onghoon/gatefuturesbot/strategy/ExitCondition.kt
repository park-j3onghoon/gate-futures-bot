package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
import com.parkj3onghoon.gatefuturesbot.market.evaluateIndicator
import com.parkj3onghoon.gatefuturesbot.model.Position
import org.slf4j.LoggerFactory

/**
 * 포지션 청산 조건.
 *
 * - EntryCondition과 의미적으로 분리: 진입용/청산용 조건은 명시적으로 다른 타입.
 * - 여러 조건은 OR로 결합된다 (어느 하나라도 충족 시 청산).
 */
sealed class ExitCondition {
    abstract fun evaluate(prices: List<Double>, position: Position): Boolean

    /**
     * 롱: (current - entry) / entry * 100 >= pct
     * 숏: (entry - current) / entry * 100 >= pct (부호 반대)
     */
    data class TakeProfitPct(val pct: Double) : ExitCondition() {
        override fun evaluate(prices: List<Double>, position: Position): Boolean {
            val current = prices.lastOrNull() ?: return false
            return pnlPercent(position, current) >= pct
        }
    }

    /**
     * PnL이 -pct 이하일 때 true (pct는 양수로 전달).
     */
    data class StopLossPct(val pct: Double) : ExitCondition() {
        override fun evaluate(prices: List<Double>, position: Position): Boolean {
            val current = prices.lastOrNull() ?: return false
            return pnlPercent(position, current) <= -pct
        }
    }

    data class IndicatorExit(
        val indicator: Indicator,
        val operator: ComparisonOp,
        val value: Double,
        val period: Int = 14
    ) : ExitCondition() {
        init {
            require(period > 0) { "period must be positive: $period" }
        }

        override fun evaluate(prices: List<Double>, position: Position): Boolean =
            evaluateIndicator(indicator, operator, value, period, prices)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExitCondition::class.java)

        /**
         * 명목가 기준 손익률(%). 레버리지는 반영되지 않는다.
         * Linear USDT-margined 선물(BTC_USDT 등)을 전제로 하며, inverse 계약은 지원하지 않는다.
         *
         * - 롱: (current - entry) / entry * 100
         * - 숏: (entry - current) / entry * 100 (부호 반대)
         * - entryPrice 파싱 실패/0이면 0% 반환하고 경고 로그를 남긴다.
         */
        internal fun pnlPercent(position: Position, currentPrice: Double): Double {
            val entry = position.entryPrice.toDoubleOrNull()
            if (entry == null || entry == 0.0) {
                logger.warn(
                    "비정상 entryPrice로 PnL 계산 불가: contract={}, entryPrice={}",
                    position.contract, position.entryPrice
                )
                return 0.0
            }
            return if (position.size > 0) {
                (currentPrice - entry) / entry * 100.0
            } else {
                (entry - currentPrice) / entry * 100.0
            }
        }
    }
}

sealed class ExitSignal {
    data object None : ExitSignal()
    data class Close(val triggered: List<ExitCondition>) : ExitSignal()
}
