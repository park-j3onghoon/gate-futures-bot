package com.parkj3onghoon.gatefuturesbot.market

import com.parkj3onghoon.gatefuturesbot.client.GateClient
import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MarketDataServiceTest {
    private lateinit var service: MarketDataService
    private lateinit var client: GateClient

    @BeforeEach
    fun setUp() {
        client = mockk()
        service = MarketDataService(client)
    }

    @Test
    fun `getCandles delegates to GateClient with all parameters`() {
        val expected = listOf(sampleCandle(1700000000L, "50000"))
        every {
            client.getCandlesticks("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)
        } returns expected

        val result = service.getCandles("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)

        assertEquals(expected, result)
        verify {
            client.getCandlesticks("BTC_USDT", Interval.MIN_5, 50, 1000L, 2000L)
        }
    }

    @Test
    fun `getLatestPrice returns last candle's closePrice`() {
        every {
            client.getCandlesticks("BTC_USDT", Interval.MIN_1, 1, null, null)
        } returns listOf(sampleCandle(1700000000L, "51234.5"))

        val price = service.getLatestPrice("BTC_USDT")

        assertEquals(51234.5, price)
    }

    @Test
    fun `getLatestPrice throws MarketDataException when no candle returned`() {
        every {
            client.getCandlesticks("BTC_USDT", Interval.MIN_1, 1, null, null)
        } returns emptyList()

        assertThrows<MarketDataException> {
            service.getLatestPrice("BTC_USDT")
        }
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
