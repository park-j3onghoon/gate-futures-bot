package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.market.MarketDataService
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.model.Position
import com.parkj3onghoon.gatefuturesbot.ratelimit.RateLimiter
import com.parkj3onghoon.gatefuturesbot.strategy.EntrySignal
import com.parkj3onghoon.gatefuturesbot.strategy.ExitSignal
import com.parkj3onghoon.gatefuturesbot.strategy.TradingStrategy
import com.parkj3onghoon.gatefuturesbot.trading.FuturesTrader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class CoinWorker(
    val contract: String,
    private val interval: Interval,
    private val strategy: TradingStrategy,
    private val marketData: MarketDataService,
    private val trader: FuturesTrader,
    private val rateLimiter: RateLimiter,
    private val config: WorkerConfig,
) {
    private val logger = LoggerFactory.getLogger(CoinWorker::class.java)

    // candleCache는 단일 워커 코루틴에서만 접근된다 (외부 공유 금지)
    private val candleCache: MutableList<Candle> = mutableListOf()

    suspend fun run() {
        MDC.put(MDC_KEY_CONTRACT, contract)
        logger.info("워커 시작: interval={}", interval.code)
        try {
            while (currentCoroutineContext().isActive) {
                runOnce()
                delay(config.checkIntervalMillis)
            }
        } finally {
            logger.info("워커 종료")
            MDC.remove(MDC_KEY_CONTRACT)
        }
    }

    internal suspend fun runOnce() {
        // runOnce는 외부(test)에서 직접 호출될 수 있어 MDC 재주입
        MDC.put(MDC_KEY_CONTRACT, contract)
        try {
            rateLimiter.acquire()
            val position = trader.getCurrentPosition(contract)
            updateCandles()
            if (position != null) evaluateAndClose(position) else evaluateAndEnter()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("워커 iteration 에러", e)
        }
    }

    private fun evaluateAndEnter() {
        when (val signal = strategy.evaluateEntry(candleCache)) {
            is EntrySignal.Long -> {
                logger.info("롱 진입 시그널: conditions={}", signal.matched.size)
                trader.openLong(contract, config.orderSize, config.leverage)
            }
            is EntrySignal.Short -> {
                logger.info("숏 진입 시그널: conditions={}", signal.matched.size)
                trader.openShort(contract, config.orderSize, config.leverage)
            }
            EntrySignal.None -> logger.debug("진입 시그널 없음")
        }
    }

    private fun evaluateAndClose(position: Position) {
        when (val signal = strategy.evaluateExit(candleCache, position)) {
            is ExitSignal.Close -> {
                val reasons = signal.triggered.joinToString { it::class.simpleName ?: "?" }
                logger.info("청산 시그널: size={}, reasons=[{}]", position.size, reasons)
                trader.closePosition(contract)
            }
            ExitSignal.None -> logger.debug("청산 시그널 없음: size={}", position.size)
        }
    }

    internal fun updateCandles() {
        val lastTs = candleCache.lastOrNull()?.timestamp
        val fetched =
            if (lastTs == null) {
                marketData.getCandles(contract, interval, limit = config.initialCandleLimit)
            } else {
                marketData.getCandles(contract, interval, fromSec = lastTs)
            }
        val existingTs = candleCache.mapTo(HashSet()) { it.timestamp }
        val fresh = fetched.filter { it.timestamp !in existingTs }
        candleCache.addAll(fresh)
        trimCache()
        if (fresh.isNotEmpty()) {
            logger.debug("캔들 업데이트: added={}, total={}", fresh.size, candleCache.size)
        }
    }

    private fun trimCache() {
        val overflow = candleCache.size - config.maxCacheSize
        if (overflow > 0) candleCache.subList(0, overflow).clear()
    }

    internal fun cacheSize(): Int = candleCache.size

    companion object {
        private const val MDC_KEY_CONTRACT = "contract"
    }
}
