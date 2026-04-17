package com.parkj3onghoon.gatefuturesbot.config

import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
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
)

/**
 * ExitConditionSpec은 type 필드로 하위 종류를 구분한다.
 * - TAKE_PROFIT_PCT: pct 필수
 * - STOP_LOSS_PCT: pct 필수
 * - INDICATOR: indicator/operator/value/period 필수
 */
data class ExitConditionSpec(
    val type: ExitType,
    val pct: Double? = null,
    val indicator: Indicator? = null,
    val operator: ComparisonOp? = null,
    val value: Double? = null,
    val period: Int = 14
)

enum class ExitType { TAKE_PROFIT_PCT, STOP_LOSS_PCT, INDICATOR }
