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

class CoinWorker(
    val contract: String,
    private val interval: Interval,
    private val strategy: TradingStrategy,
    private val marketData: MarketDataService,
    private val trader: FuturesTrader,
    private val rateLimiter: RateLimiter,
    private val orderSize: Int,
    private val leverage: Int,
    private val checkIntervalMillis: Long,
    private val initialCandleLimit: Int = 100,
    private val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE
) {

    private val logger = LoggerFactory.getLogger(CoinWorker::class.java)
    // candleCache는 단일 워커 코루틴에서만 접근된다 (외부 공유 금지)
    private val candleCache: MutableList<Candle> = mutableListOf()

    companion object {
        const val DEFAULT_MAX_CACHE_SIZE: Int = 1000
    }

    suspend fun run() {
        logger.info("워커 시작: contract={}, interval={}", contract, interval.code)
        try {
            while (currentCoroutineContext().isActive) {
                runOnce()
                delay(checkIntervalMillis)
            }
        } finally {
            logger.info("워커 종료: contract={}", contract)
        }
    }

    internal suspend fun runOnce() {
        try {
            rateLimiter.acquire()
            val position = trader.getCurrentPosition(contract)
            updateCandles()
            if (position != null) {
                evaluateAndClose(position)
            } else {
                evaluateAndEnter()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("워커 iteration 에러: contract=$contract", e)
        }
    }

    private fun evaluateAndEnter() {
        when (val signal = strategy.evaluateEntry(candleCache)) {
            is EntrySignal.Long -> {
                logger.info("롱 진입 시그널: contract={}, conditions={}", contract, signal.matched.size)
                trader.openLong(contract, orderSize, leverage)
            }
            is EntrySignal.Short -> {
                logger.info("숏 진입 시그널: contract={}, conditions={}", contract, signal.matched.size)
                trader.openShort(contract, orderSize, leverage)
            }
            EntrySignal.None -> logger.debug("진입 시그널 없음: contract={}", contract)
        }
    }

    private fun evaluateAndClose(position: Position) {
        when (val signal = strategy.evaluateExit(candleCache, position)) {
            is ExitSignal.Close -> {
                val reasons = signal.triggered.joinToString { it::class.simpleName ?: "?" }
                logger.info(
                    "청산 시그널: contract={}, size={}, reasons=[{}]",
                    contract, position.size, reasons
                )
                trader.closePosition(contract)
            }
            ExitSignal.None -> logger.debug(
                "청산 시그널 없음: contract={}, size={}", contract, position.size
            )
        }
    }

    internal fun updateCandles() {
        val lastTs = candleCache.lastOrNull()?.timestamp
        val fetched = if (lastTs == null) {
            marketData.getCandles(contract, interval, limit = initialCandleLimit)
        } else {
            marketData.getCandles(contract, interval, fromSec = lastTs)
        }
        val existingTs = candleCache.mapTo(HashSet()) { it.timestamp }
        val fresh = fetched.filter { it.timestamp !in existingTs }
        candleCache.addAll(fresh)
        trimCache()
        if (fresh.isNotEmpty()) {
            logger.debug("캔들 업데이트: contract={}, added={}, total={}", contract, fresh.size, candleCache.size)
        }
    }

    private fun trimCache() {
        val overflow = candleCache.size - maxCacheSize
        if (overflow > 0) candleCache.subList(0, overflow).clear()
    }

    internal fun cacheSize(): Int = candleCache.size
}
