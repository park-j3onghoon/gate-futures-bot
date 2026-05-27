package com.parkj3onghoon.gatefuturesbot.risk

/**
 * Risk Manager의 판정 결과.
 * 진입을 허용(Allow)하거나 구체적 이유와 함께 차단(Deny).
 */
sealed class RiskDecision {
    data object Allow : RiskDecision()

    data class Deny(
        val reason: String,
    ) : RiskDecision()
}
