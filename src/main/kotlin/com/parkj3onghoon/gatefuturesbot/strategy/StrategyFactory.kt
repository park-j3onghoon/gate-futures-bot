package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.config.ContractStrategySpec
import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * application.yml의 전략 설정을 TradingStrategy 도메인 객체로 변환한다.
 *
 * 설정에 없는 contract는 빈 전략(no-op)이 반환된다.
 */
@Component
class StrategyFactory(private val properties: StrategyProperties) {

    private val logger = LoggerFactory.getLogger(StrategyFactory::class.java)

    fun forContract(contract: String): TradingStrategy {
        val spec = properties.contracts[contract]
        if (spec == null) {
            logger.warn("전략 설정 없음, 빈 전략 반환: contract={}", contract)
            return TradingStrategy()
        }
        return spec.toTradingStrategy()
    }

    private fun ContractStrategySpec.toTradingStrategy(): TradingStrategy = TradingStrategy(
        longEntries = longEntries.map { it.toEntryCondition() },
        shortEntries = shortEntries.map { it.toEntryCondition() },
        exitConditions = exitConditions.map { it.toExitCondition() }
    )
}
