package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.config.BotProperties
import com.parkj3onghoon.gatefuturesbot.market.MarketDataService
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.ratelimit.RateLimiter
import com.parkj3onghoon.gatefuturesbot.strategy.TradingStrategy
import com.parkj3onghoon.gatefuturesbot.trading.FuturesTrader
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WorkerOrchestrator(
    private val botProperties: BotProperties,
    private val marketData: MarketDataService,
    private val trader: FuturesTrader,
    private val rateLimiter: RateLimiter,
    private val interval: Interval = Interval.MIN_1
) {

    private val logger = LoggerFactory.getLogger(WorkerOrchestrator::class.java)

    fun createWorker(contract: String, strategy: TradingStrategy = TradingStrategy()): CoinWorker =
        CoinWorker(
            contract = contract,
            interval = interval,
            strategy = strategy,
            marketData = marketData,
            trader = trader,
            rateLimiter = rateLimiter,
            config = WorkerConfig(
                orderSize = botProperties.orderSize,
                leverage = botProperties.leverage,
                checkIntervalMillis = botProperties.checkIntervalSec * 1_000L
            )
        )

    /**
     * 모든 contract 워커를 supervisorScope에서 병렬 실행.
     *
     * - 한 워커의 에러는 다른 워커에 전파되지 않는다(supervisorScope).
     * - CoinWorker.runOnce는 내부에서 Exception을 catch하므로 run() 루프는 CancellationException 외에는 잘 죽지 않는다.
     *   따라서 죽은 워커 재시작 로직은 두지 않는다. (4단계 범위. 향후 필요 시 추가)
     * - strategyProvider의 기본값은 빈 전략으로 아무 시그널도 내지 않는다. 실전에서는 명시적으로 주입해야 한다.
     */
    suspend fun runAll(strategyProvider: (String) -> TradingStrategy = { TradingStrategy() }) =
        supervisorScope {
            logger.info("오케스트레이터 시작: contracts={}", botProperties.contracts)
            botProperties.contracts.forEach { contract ->
                launch { createWorker(contract, strategyProvider(contract)).run() }
            }
        }
}
