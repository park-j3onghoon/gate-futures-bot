package com.parkj3onghoon.gatefuturesbot.model

import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import io.gate.gateapi.models.FuturesCandlestick
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CandleTest {

    @Test
    fun `from maps FuturesCandlestick fields correctly`() {
        val raw = FuturesCandlestick().apply {
            t = 1700000000.0
            o = "50000"
            h = "50500"
            l = "49500"
            c = "50200"
            v = 100L
        }

        val candle = Candle.from(raw)

        assertEquals(1700000000L, candle.timestamp)
        assertEquals("50200", candle.close)
        assertEquals(50200.0, candle.closePrice)
        assertEquals(100L, candle.volume)
    }

    @Test
    fun `from throws MarketDataException when timestamp is null`() {
        val raw = FuturesCandlestick().apply {
            o = "1"; h = "1"; l = "1"; c = "1"
        }
        assertThrows<MarketDataException> { Candle.from(raw) }
    }

    @Test
    fun `from throws MarketDataException when close is null`() {
        val raw = FuturesCandlestick().apply {
            t = 1.0; o = "1"; h = "1"; l = "1"
        }
        assertThrows<MarketDataException> { Candle.from(raw) }
    }

    @Test
    fun `from defaults volume to zero when null`() {
        val raw = FuturesCandlestick().apply {
            t = 1.0; o = "1"; h = "1"; l = "1"; c = "1"
        }
        assertEquals(0L, Candle.from(raw).volume)
    }
}
