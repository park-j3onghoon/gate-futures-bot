package com.parkj3onghoon.gatefuturesbot.market

import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.trading.ExchangePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MarketDataServiceTest {
    private lateinit var service: MarketDataService
    private lateinit var exchange: ExchangePort

    @BeforeEach
    fun setUp() {
        exchange = mockk()
        service = MarketDataService(exchange)
    }

    @Test
    fun `getCandles delegates to ExchangePort`() {
        val expected = listOf(sampleCandle(1700000000L, "50000"))
        every {
            exchange.getCandles("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)
        } returns expected

        val result = service.getCandles("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)

        assertEquals(expected, result)
        verify {
            exchange.getCandles("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)
        }
    }

    @Test
    fun `getLatestPrice returns last candle's closePrice`() {
        every {
            exchange.getCandles("BTC_USDT", Interval.MIN_1, 1, null, null)
        } returns listOf(sampleCandle(1700000000L, "51234.5"))

        val price = service.getLatestPrice("BTC_USDT")

        assertEquals(51234.5, price)
    }

    @Test
    fun `getLatestPrice throws MarketDataException when no candle returned`() {
        every {
            exchange.getCandles("BTC_USDT", Interval.MIN_1, 1, null, null)
        } returns emptyList()

        assertThrows<MarketDataException> {
            service.getLatestPrice("BTC_USDT")
        }
    }

    @Test
    fun `getCandles returns empty list without throwing`() {
        every {
            exchange.getCandles(any(), any(), any(), any(), any())
        } returns emptyList()

        val result = service.getCandles("BTC_USDT", Interval.MIN_1)

        assertEquals(emptyList(), result)
    }

    private fun sampleCandle(
        ts: Long,
        close: String,
    ): Candle =
        Candle(
            timestamp = ts,
            open = close,
            high = close,
            low = close,
            close = close,
            volume = 0L,
        )
}
