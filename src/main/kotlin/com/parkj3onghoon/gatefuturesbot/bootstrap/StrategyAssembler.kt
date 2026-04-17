package com.parkj3onghoon.gatefuturesbot.bootstrap

import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import com.parkj3onghoon.gatefuturesbot.strategy.TradingStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Application-layer adapter: config 계층의 StrategyProperties를 domain 계층의 TradingStrategy로 조립한다.
 *
 * - config → domain 방향으로만 변환한다 (역방향 참조 없음)
 * - 설정에 없는 contract는 빈 전략(no-op)이 반환된다
 */
@Component
class StrategyAssembler(private val properties: StrategyProperties) {

    private val logger = LoggerFactory.getLogger(StrategyAssembler::class.java)

    fun forContract(contract: String): TradingStrategy {
        val spec = properties.contracts[contract]
        if (spec == null) {
            logger.warn("전략 설정 없음, 빈 전략 반환: contract={}", contract)
            return TradingStrategy()
        }
        return TradingStrategy(
            longEntries = spec.longEntries.map { it.toEntryCondition() },
            shortEntries = spec.shortEntries.map { it.toEntryCondition() },
            exitConditions = spec.exitConditions.map { it.toExitCondition() }
        )
    }
}
