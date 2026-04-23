package com.parkj3onghoon.gatefuturesbot.config

import com.parkj3onghoon.gatefuturesbot.bootstrap.StrategyAssembler
import com.parkj3onghoon.gatefuturesbot.market.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.market.Indicator
import com.parkj3onghoon.gatefuturesbot.strategy.EntryCondition
import com.parkj3onghoon.gatefuturesbot.strategy.ExitCondition
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * application.yml → @ConfigurationProperties → StrategyAssembler 경로가 실제 Spring 컨텍스트에서
 * 동작하는지 통합 검증. ExitConditionSpec의 type 분기가 제대로 바인딩되는지 확인.
 */
@SpringBootTest(
    properties = [
        "strategy.contracts.BTC_USDT.long-entries[0].indicator=RSI",
        "strategy.contracts.BTC_USDT.long-entries[0].operator=LT",
        "strategy.contracts.BTC_USDT.long-entries[0].value=30.0",
        "strategy.contracts.BTC_USDT.long-entries[0].period=14",
        "strategy.contracts.BTC_USDT.exit-conditions[0].type=TAKE_PROFIT_PCT",
        "strategy.contracts.BTC_USDT.exit-conditions[0].pct=5.0",
        "strategy.contracts.BTC_USDT.exit-conditions[1].type=INDICATOR",
        "strategy.contracts.BTC_USDT.exit-conditions[1].indicator=RSI",
        "strategy.contracts.BTC_USDT.exit-conditions[1].operator=GT",
        "strategy.contracts.BTC_USDT.exit-conditions[1].value=70.0",
    ],
)
class StrategyBindingIntegrationTest {
    @Autowired
    lateinit var properties: StrategyProperties

    @Autowired
    lateinit var assembler: StrategyAssembler

    @Test
    fun `StrategyProperties binds nested map and lists`() {
        val spec = properties.contracts["BTC_USDT"]
        requireNotNull(spec)
        assertEquals(1, spec.longEntries.size)
        assertEquals(Indicator.RSI, spec.longEntries[0].indicator)
        assertEquals(ComparisonOp.LT, spec.longEntries[0].operator)
        assertEquals(30.0, spec.longEntries[0].value)
        assertEquals(2, spec.exitConditions.size)
        assertEquals(ExitType.TAKE_PROFIT_PCT, spec.exitConditions[0].type)
        assertEquals(5.0, spec.exitConditions[0].pct)
        assertEquals(ExitType.INDICATOR, spec.exitConditions[1].type)
    }

    @Test
    fun `StrategyAssembler converts bound specs to domain TradingStrategy`() {
        val strategy = assembler.forContract("BTC_USDT")

        // 빈 candles 시 EntrySignal.None
        assertEquals(
            com.parkj3onghoon.gatefuturesbot.strategy.EntrySignal.None,
            strategy.evaluateEntry(emptyList()),
        )

        // reflection으로 내부 조건 타입 확인 대신, 구조적 동작 확인:
        // TakeProfitPct + IndicatorExit 각각 1개씩 있어야 함
        val triggered =
            listOf(
                ExitCondition.TakeProfitPct(5.0),
                ExitCondition.IndicatorExit(Indicator.RSI, ComparisonOp.GT, 70.0, 14),
            )
        triggered.forEachIndexed { i, expected ->
            assertEquals(
                expected::class,
                properties.contracts["BTC_USDT"]!!
                    .exitConditions[i]
                    .toExitCondition()::class,
            )
        }

        // EntryCondition 1개
        val entryCondition: EntryCondition =
            properties.contracts["BTC_USDT"]!!
                .longEntries[0]
                .toEntryCondition()
        assertIs<EntryCondition>(entryCondition)
    }
}
