package com.parkj3onghoon.gatefuturesbot.config

import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
import com.parkj3onghoon.gatefuturesbot.strategy.EntryCondition
import com.parkj3onghoon.gatefuturesbot.strategy.ExitCondition
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "strategy")
data class StrategyProperties(
    val contracts: Map<String, ContractStrategySpec> = emptyMap()
)

data class ContractStrategySpec(
    val longEntries: List<EntryConditionSpec> = emptyList(),
    val shortEntries: List<EntryConditionSpec> = emptyList(),
    val exitConditions: List<ExitConditionSpec> = emptyList()
)

data class EntryConditionSpec(
    val indicator: Indicator,
    val operator: ComparisonOp,
    val value: Double,
    val period: Int = 14
) {
    fun toEntryCondition(): EntryCondition = EntryCondition(indicator, operator, value, period)
}

/**
 * ExitConditionSpec은 type 필드로 하위 종류를 구분한다.
 * Kotlin sealed class는 @ConfigurationProperties YAML 바인딩이 번거로워 단일 data class + type 필드를 사용.
 * 변환 시 toExitCondition()이 type별 필수 필드를 검증한다.
 */
data class ExitConditionSpec(
    val type: ExitType,
    val pct: Double? = null,
    val indicator: Indicator? = null,
    val operator: ComparisonOp? = null,
    val value: Double? = null,
    val period: Int = 14
) {
    fun toExitCondition(): ExitCondition = when (type) {
        ExitType.TAKE_PROFIT_PCT -> ExitCondition.TakeProfitPct(requirePct())
        ExitType.STOP_LOSS_PCT -> ExitCondition.StopLossPct(requirePct())
        ExitType.INDICATOR -> ExitCondition.IndicatorExit(
            indicator = require(indicator) { "INDICATOR 청산 조건에 indicator 필드가 필요합니다" },
            operator = require(operator) { "INDICATOR 청산 조건에 operator 필드가 필요합니다" },
            value = require(value) { "INDICATOR 청산 조건에 value 필드가 필요합니다" },
            period = period
        )
    }

    private fun requirePct(): Double =
        require(pct) { "${type} 청산 조건에 pct 필드가 필요합니다" }

    private fun <T : Any> require(value: T?, lazyMessage: () -> String): T =
        value ?: throw IllegalArgumentException(lazyMessage())
}

enum class ExitType { TAKE_PROFIT_PCT, STOP_LOSS_PCT, INDICATOR }
