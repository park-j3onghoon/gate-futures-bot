package com.parkj3onghoon.gatefuturesbot.market

/**
 * 이 프로젝트에서 지원하는 기술 지표 종류.
 * - RSI/SMA/EMA: Indicators.kt의 top-level 계산 함수와 1:1 대응
 * - PRICE: 지표가 아닌 현재 가격 자체 (period 무시)
 */
enum class Indicator { RSI, SMA, EMA, PRICE }

enum class ComparisonOp { LT, LTE, GT, GTE }

/**
 * 지표값 계산 + 비교 연산을 한 번에 수행.
 * EntryCondition / ExitCondition.IndicatorExit가 공유하는 평가 엔진.
 */
fun evaluateIndicator(
    indicator: Indicator,
    operator: ComparisonOp,
    value: Double,
    period: Int,
    prices: List<Double>,
): Boolean {
    val current: Double =
        when (indicator) {
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
