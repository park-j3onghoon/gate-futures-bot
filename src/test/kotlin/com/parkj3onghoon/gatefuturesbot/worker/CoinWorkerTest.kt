package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.market.MarketDataService
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import com.parkj3onghoon.gatefuturesbot.ratelimit.RateLimiter
import com.parkj3onghoon.gatefuturesbot.strategy.ComparisonOp
import com.parkj3onghoon.gatefuturesbot.strategy.EntryCondition
import com.parkj3onghoon.gatefuturesbot.strategy.ExitCondition
import com.parkj3onghoon.gatefuturesbot.strategy.Indicator
import com.parkj3onghoon.gatefuturesbot.strategy.TradingStrategy
import com.parkj3onghoon.gatefuturesbot.trading.FuturesTrader
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinWorkerTest {

    private val contract = "BTC_USDT"

    @Test
    fun `runOnce skips entry when position exists and no exit signal`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } returns Position(
            contract, 1L, "50000", 5, "0", "0"
        )
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns
            flatPriceCandles(20, 50000.0)

        val worker = newWorker(trader, marketData, rateLimiter, TradingStrategy())
        worker.runOnce()

        verify(exactly = 0) { trader.openLong(any(), any(), any()) }
        verify(exactly = 0) { trader.openShort(any(), any(), any()) }
        verify(exactly = 0) { trader.closePosition(any()) }
    }

    @Test
    fun `runOnce closes position when exit signal triggers`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        val position = Position(contract, 1L, "100", 5, "0", "0")
        every { trader.getCurrentPosition(contract) } returns position
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns
            listOf(candle(1700000000L, 106.0))
        every { trader.closePosition(contract) } returns OrderResult(
            100L, contract, 0L, "0", "finished", "106", 1700000000.0
        )

        val strategy = TradingStrategy(
            exitConditions = listOf(ExitCondition.TakeProfitPct(5.0))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)
        worker.runOnce()

        verify(exactly = 1) { trader.closePosition(contract) }
        verify(exactly = 0) { trader.openLong(any(), any(), any()) }
    }

    @Test
    fun `runOnce does not close when exit signal is None`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        val position = Position(contract, 1L, "100", 5, "0", "0")
        every { trader.getCurrentPosition(contract) } returns position
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns
            listOf(candle(1700000000L, 101.0))

        val strategy = TradingStrategy(
            exitConditions = listOf(ExitCondition.TakeProfitPct(5.0))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)
        worker.runOnce()

        verify(exactly = 0) { trader.closePosition(any()) }
    }

    @Test
    fun `runOnce opens long when long signal and no position`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } returns null

        val fallingCandles = fallingPriceCandles(20)
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns fallingCandles
        val orderResult = OrderResult(1L, contract, 1L, "0", "finished", "50000", 1700000000.0)
        every { trader.openLong(contract, 1, 5) } returns orderResult

        val strategy = TradingStrategy(
            longEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, 14))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)
        worker.runOnce()

        verify(exactly = 1) { trader.openLong(contract, 1, 5) }
        verify(exactly = 0) { trader.openShort(any(), any(), any()) }
    }

    @Test
    fun `runOnce opens short when short signal and no position`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } returns null

        val risingCandles = risingPriceCandles(20)
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns risingCandles
        every { trader.openShort(contract, 1, 5) } returns OrderResult(
            2L, contract, -1L, "0", "finished", "50000", 1700000000.0
        )

        val strategy = TradingStrategy(
            shortEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.GT, 70.0, 14))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)
        worker.runOnce()

        verify(exactly = 1) { trader.openShort(contract, 1, 5) }
    }

    @Test
    fun `runOnce does nothing on None signal`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } returns null
        every {
            marketData.getCandles(contract, Interval.MIN_1, 100, null, null)
        } returns flatPriceCandles(20, 100.0)

        val strategy = TradingStrategy(
            longEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.LT, 20.0, 14))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)
        worker.runOnce()

        verify(exactly = 0) { trader.openLong(any(), any(), any()) }
        verify(exactly = 0) { trader.openShort(any(), any(), any()) }
    }

    @Test
    fun `runOnce swallows exception from trader to keep worker alive`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } throws RuntimeException("boom")

        val worker = newWorker(trader, marketData, rateLimiter, TradingStrategy())
        worker.runOnce()
    }

    @Test
    fun `updateCandles uses delta fetch after first call`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()

        val first = flatPriceCandles(5, 100.0)
        val lastTs = first.last().timestamp
        val second = listOf(
            candle(lastTs, 100.0),
            candle(lastTs + 60, 101.0),
            candle(lastTs + 120, 102.0)
        )
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns first
        every { marketData.getCandles(contract, Interval.MIN_1, null, lastTs, null) } returns second

        val worker = newWorker(trader, marketData, rateLimiter, TradingStrategy())
        worker.updateCandles()
        assertEquals(5, worker.cacheSize())

        worker.updateCandles()
        assertEquals(7, worker.cacheSize(), "dedup by timestamp should add only 2 new candles")
    }

    @Test
    fun `full cycle - long entry then exit closes position`() = runTest {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs

        // 1) 진입 단계: 포지션 없음 + 하락장 → 롱 진입
        val fallingCandles = fallingPriceCandles(20)
        every { marketData.getCandles(contract, Interval.MIN_1, 100, null, null) } returns fallingCandles
        every { trader.openLong(contract, 1, 5) } returns OrderResult(
            1L, contract, 1L, "0", "finished", "15.5", 1700000000.0
        )
        // getCurrentPosition: 1st call → null, 2nd call → 새 포지션
        val enteredPosition = Position(contract, 1L, "15.5", 5, "0", "0")
        every { trader.getCurrentPosition(contract) } returnsMany listOf(null, enteredPosition)
        every { trader.closePosition(contract) } returns OrderResult(
            2L, contract, 0L, "0", "finished", "17.0", 1700000060.0
        )

        val strategy = TradingStrategy(
            longEntries = listOf(EntryCondition(Indicator.RSI, ComparisonOp.LT, 30.0, 14)),
            exitConditions = listOf(ExitCondition.TakeProfitPct(5.0))
        )
        val worker = newWorker(trader, marketData, rateLimiter, strategy)

        // Cycle 1: 진입
        worker.runOnce()
        verify(exactly = 1) { trader.openLong(contract, 1, 5) }
        verify(exactly = 0) { trader.closePosition(any()) }

        // Cycle 2: 포지션 상태에서 가격이 +5% 이상 (15.5 → 17.0 = +9.7%)로 청산 시그널
        // 델타 조회 (fromSec = 마지막 timestamp)로 상승 캔들 피드
        val lastTs = fallingCandles.last().timestamp
        every { marketData.getCandles(contract, Interval.MIN_1, null, lastTs, null) } returns
            listOf(candle(lastTs + 60, 17.0))

        worker.runOnce()
        verify(exactly = 1) { trader.closePosition(contract) }
        verify(exactly = 1) { trader.openLong(contract, 1, 5) } // 재호출 없음
    }

    @Test
    fun `run exits gracefully on cancellation`() = runBlocking {
        val trader = mockk<FuturesTrader>()
        val marketData = mockk<MarketDataService>()
        val rateLimiter = mockk<RateLimiter>()
        coEvery { rateLimiter.acquire() } just Runs
        every { trader.getCurrentPosition(contract) } returns null
        every {
            marketData.getCandles(contract, Interval.MIN_1, 100, null, null)
        } returns flatPriceCandles(20, 100.0)

        val worker = newWorker(trader, marketData, rateLimiter, TradingStrategy())
        val job = launch { worker.run() }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun newWorker(
        trader: FuturesTrader,
        marketData: MarketDataService,
        rateLimiter: RateLimiter,
        strategy: TradingStrategy
    ): CoinWorker = CoinWorker(
        contract = contract,
        interval = Interval.MIN_1,
        strategy = strategy,
        marketData = marketData,
        trader = trader,
        rateLimiter = rateLimiter,
        orderSize = 1,
        leverage = 5,
        checkIntervalMillis = 10,
        initialCandleLimit = 100
    )

    private fun candle(ts: Long, close: Double): Candle = Candle(
        timestamp = ts,
        open = close.toString(),
        high = close.toString(),
        low = close.toString(),
        close = close.toString(),
        volume = 0L
    )

    private fun flatPriceCandles(count: Int, price: Double): List<Candle> =
        (0 until count).map { candle(1700000000L + it * 60, price) }

    private fun fallingPriceCandles(count: Int): List<Candle> =
        (0 until count).map { candle(1700000000L + it * 60, 25.0 - it * 0.5) }

    private fun risingPriceCandles(count: Int): List<Candle> =
        (0 until count).map { candle(1700000000L + it * 60, 100.0 + it.toDouble()) }
}
