package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.config.BotProperties
import com.parkj3onghoon.gatefuturesbot.market.MarketDataService
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.ratelimit.RateLimiter
import com.parkj3onghoon.gatefuturesbot.trading.FuturesTrader
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerOrchestratorTest {
    @Test
    fun `createWorker applies BotProperties`() {
        val orchestrator =
            WorkerOrchestrator(
                botProperties =
                    BotProperties(
                        contracts = listOf("BTC_USDT"),
                        leverage = 10,
                        orderSize = 3,
                        checkIntervalSec = 30,
                    ),
                marketData = mockk(),
                trader = mockk(),
                rateLimiter = mockk(),
                interval = Interval.MIN_5,
            )

        val worker = orchestrator.createWorker("ETH_USDT")

        assertEquals("ETH_USDT", worker.contract)
    }

    @Test
    fun `runAll launches a worker for each contract`() =
        runBlocking {
            val trader = mockk<FuturesTrader>()
            val marketData = mockk<MarketDataService>()
            val rateLimiter = mockk<RateLimiter>()

            coEvery { rateLimiter.acquire() } just Runs
            every { trader.getCurrentPosition(any()) } returns null
            every { marketData.getCandles(any(), any(), any(), any(), any()) } returns emptyList()

            val orchestrator =
                WorkerOrchestrator(
                    botProperties =
                        BotProperties(
                            contracts = listOf("BTC_USDT", "ETH_USDT"),
                            checkIntervalSec = 1,
                        ),
                    marketData = marketData,
                    trader = trader,
                    rateLimiter = rateLimiter,
                )

            val job: Job = launch { orchestrator.runAll() }
            repeat(3) { yield() }
            delay(30)
            job.cancel()
            job.join()

            verify(atLeast = 1) { trader.getCurrentPosition("BTC_USDT") }
            verify(atLeast = 1) { trader.getCurrentPosition("ETH_USDT") }
        }

    @Test
    fun `runAll isolates worker failure via supervisorScope`() =
        runBlocking {
            val trader = mockk<FuturesTrader>()
            val marketData = mockk<MarketDataService>()
            val rateLimiter = mockk<RateLimiter>()

            coEvery { rateLimiter.acquire() } just Runs
            // BTC 워커는 예외 던짐, ETH 워커는 정상
            every { trader.getCurrentPosition("BTC_USDT") } throws RuntimeException("BTC boom")
            every { trader.getCurrentPosition("ETH_USDT") } returns null
            every {
                marketData.getCandles("ETH_USDT", any(), any(), any(), any())
            } returns emptyList()

            val orchestrator =
                WorkerOrchestrator(
                    botProperties =
                        BotProperties(
                            contracts = listOf("BTC_USDT", "ETH_USDT"),
                            checkIntervalSec = 1,
                        ),
                    marketData = marketData,
                    trader = trader,
                    rateLimiter = rateLimiter,
                )

            val job = launch { orchestrator.runAll() }
            repeat(3) { yield() }
            delay(30)
            job.cancel()
            job.join()

            // ETH 워커가 살아남아 실행되었는지 검증 (BTC 예외로 중단 안 됨)
            verify(atLeast = 1) { trader.getCurrentPosition("ETH_USDT") }
            assertTrue(job.isCancelled)
        }
}
