package com.parkj3onghoon.gatefuturesbot.client

import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.exception.AuthenticationException
import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.exception.OrderException
import com.parkj3onghoon.gatefuturesbot.exception.RateLimitException
import com.parkj3onghoon.gatefuturesbot.model.Interval
import io.gate.gateapi.GateApiException
import io.gate.gateapi.api.FuturesApi
import io.gate.gateapi.models.FuturesCandlestick
import io.gate.gateapi.models.FuturesOrder
import io.gate.gateapi.models.Position as GatePosition
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GateClientTest {

    private lateinit var client: GateClient
    private lateinit var futuresApi: FuturesApi

    @BeforeEach
    fun setUp() {
        val apiProperties = ApiProperties(
            key = "test-key",
            secret = "test-secret",
            host = "https://api.gateio.ws/api/v4",
            settle = "usdt"
        )
        futuresApi = mockk()
        client = GateClient(apiProperties, futuresApi)
    }

    @Test
    fun `should create order successfully`() {
        val responseOrder = FuturesOrder().apply {
            contract = "BTC_USDT"
            size = 1L
            price = "0"
            tif = FuturesOrder.TifEnum.IOC
        }
        setField(responseOrder, "id", 12345L)
        setField(responseOrder, "status", FuturesOrder.StatusEnum.FINISHED)
        setField(responseOrder, "fillPrice", "50000.5")
        setField(responseOrder, "createTime", 1700000000.0)

        every { futuresApi.createFuturesOrder("usdt", any(), null) } returns responseOrder

        val result = client.createOrder("BTC_USDT", 1L)

        assertEquals(12345L, result.id)
        assertEquals("BTC_USDT", result.contract)
        assertEquals(1L, result.size)
        assertEquals("finished", result.status)
        assertEquals("50000.5", result.fillPrice)
    }

    @Test
    fun `should throw AuthenticationException on INVALID_KEY`() {
        val exception = GateApiException("INVALID_KEY", "Invalid API key", "")
        every { futuresApi.createFuturesOrder("usdt", any(), null) } throws exception

        assertThrows<AuthenticationException> {
            client.createOrder("BTC_USDT", 1L)
        }
    }

    @Test
    fun `should throw InsufficientBalanceException on BALANCE_NOT_ENOUGH`() {
        val exception = GateApiException("BALANCE_NOT_ENOUGH", "Insufficient balance", "")
        every { futuresApi.createFuturesOrder("usdt", any(), null) } throws exception

        assertThrows<InsufficientBalanceException> {
            client.createOrder("BTC_USDT", 1L)
        }
    }

    @Test
    fun `should throw RateLimitException on RATE_LIMIT`() {
        val exception = GateApiException("RATE_LIMIT", "Rate limit exceeded", "")
        every { futuresApi.createFuturesOrder("usdt", any(), null) } throws exception

        assertThrows<RateLimitException> {
            client.createOrder("BTC_USDT", 1L)
        }
    }

    @Test
    fun `should retry once on RateLimitException and succeed`() {
        val exception = GateApiException("RATE_LIMIT", "Rate limit exceeded", "")
        val responseOrder = FuturesOrder().apply {
            contract = "BTC_USDT"
            size = 1L
            price = "0"
            tif = FuturesOrder.TifEnum.IOC
        }
        setField(responseOrder, "id", 99L)
        setField(responseOrder, "status", FuturesOrder.StatusEnum.FINISHED)
        setField(responseOrder, "fillPrice", "50000")
        setField(responseOrder, "createTime", 1700000000.0)

        every { futuresApi.createFuturesOrder("usdt", any(), null) } throws exception andThen responseOrder

        val result = client.createOrder("BTC_USDT", 1L)

        assertEquals(99L, result.id)
        verify(exactly = 2) { futuresApi.createFuturesOrder("usdt", any(), null) }
    }

    @Test
    fun `should throw OrderException on unknown error label`() {
        val exception = GateApiException("UNKNOWN_ERROR", "Something went wrong", "")
        every { futuresApi.createFuturesOrder("usdt", any(), null) } throws exception

        assertThrows<OrderException> {
            client.createOrder("BTC_USDT", 1L)
        }
    }

    @Test
    fun `should return position when size is non-zero`() {
        val gatePosition = GatePosition().apply {
            leverage = "5"
        }
        setField(gatePosition, "contract", "BTC_USDT")
        setField(gatePosition, "size", 1L)
        setField(gatePosition, "entryPrice", "50000")
        setField(gatePosition, "unrealisedPnl", "100.5")
        setField(gatePosition, "realisedPnl", "50.3")

        val requestBuilder = mockk<FuturesApi.APIgetPositionRequest>()
        every { futuresApi.getPosition("usdt", "BTC_USDT") } returns requestBuilder
        every { requestBuilder.execute() } returns gatePosition

        val position = client.getPosition("BTC_USDT")

        assertNotNull(position)
        assertEquals("BTC_USDT", position.contract)
        assertEquals(1L, position.size)
        assertEquals("50000", position.entryPrice)
        assertEquals(5, position.leverage)
    }

    @Test
    fun `should return null when position size is zero`() {
        val gatePosition = GatePosition().apply {
            leverage = "5"
        }
        setField(gatePosition, "contract", "BTC_USDT")
        setField(gatePosition, "size", 0L)

        val requestBuilder = mockk<FuturesApi.APIgetPositionRequest>()
        every { futuresApi.getPosition("usdt", "BTC_USDT") } returns requestBuilder
        every { requestBuilder.execute() } returns gatePosition

        val position = client.getPosition("BTC_USDT")

        assertNull(position)
    }

    @Test
    fun `should close position with close=true and size=0`() {
        val responseOrder = FuturesOrder().apply {
            contract = "BTC_USDT"
            size = 0L
            price = "0"
            tif = FuturesOrder.TifEnum.IOC
        }
        setField(responseOrder, "id", 777L)
        setField(responseOrder, "status", FuturesOrder.StatusEnum.FINISHED)
        setField(responseOrder, "fillPrice", "51000")
        setField(responseOrder, "createTime", 1700000000.0)

        val orderSlot = slot<FuturesOrder>()
        every { futuresApi.createFuturesOrder("usdt", capture(orderSlot), null) } returns responseOrder

        val result = client.closePosition("BTC_USDT")

        assertEquals(777L, result.id)
        assertEquals("BTC_USDT", result.contract)
        val captured = orderSlot.captured
        assertEquals("BTC_USDT", captured.contract)
        assertEquals(0L, captured.size)
        assertEquals(GateClient.MARKET_PRICE, captured.price)
        assertEquals(FuturesOrder.TifEnum.IOC, captured.tif)
        assertEquals(true, captured.close)
    }

    @Test
    fun `should fetch candlesticks and map to Candle list`() {
        val raw1 = FuturesCandlestick().apply {
            t = 1700000000.0
            o = "50000"
            h = "50500"
            l = "49500"
            c = "50200"
            v = 100L
        }
        val raw2 = FuturesCandlestick().apply {
            t = 1700000060.0
            o = "50200"
            h = "50800"
            l = "50100"
            c = "50600"
            v = 150L
        }
        val request = mockk<FuturesApi.APIlistFuturesCandlesticksRequest>()
        every { futuresApi.listFuturesCandlesticks("usdt", "BTC_USDT") } returns request
        every { request.interval("1m") } returns request
        every { request.limit(100) } returns request
        every { request.execute() } returns listOf(raw1, raw2)

        val candles = client.getCandlesticks("BTC_USDT", Interval.MIN_1, limit = 100)

        assertEquals(2, candles.size)
        assertEquals(1700000000L, candles[0].timestamp)
        assertEquals("50200", candles[0].close)
        assertEquals(50200.0, candles[0].closePrice)
        assertEquals(100L, candles[0].volume)
        verify { request.interval("1m") }
        verify { request.limit(100) }
    }

    @Test
    fun `should fetch candlesticks with time range when from and to provided`() {
        val request = mockk<FuturesApi.APIlistFuturesCandlesticksRequest>()
        every { futuresApi.listFuturesCandlesticks("usdt", "BTC_USDT") } returns request
        every { request.interval("5m") } returns request
        every { request.from(1000L) } returns request
        every { request.to(2000L) } returns request
        every { request.execute() } returns emptyList()

        val candles = client.getCandlesticks("BTC_USDT", Interval.MIN_5, fromSec = 1000L, toSec = 2000L)

        assertEquals(0, candles.size)
        verify { request.from(1000L) }
        verify { request.to(2000L) }
    }

    @Test
    fun `should throw AuthenticationException when candlesticks returns INVALID_KEY`() {
        val exception = GateApiException("INVALID_KEY", "Invalid API key", "")
        val request = mockk<FuturesApi.APIlistFuturesCandlesticksRequest>()
        every { futuresApi.listFuturesCandlesticks("usdt", "BTC_USDT") } returns request
        every { request.interval("1h") } returns request
        every { request.execute() } throws exception

        assertThrows<AuthenticationException> {
            client.getCandlesticks("BTC_USDT", Interval.HOUR_1)
        }
    }

    private fun setField(obj: Any, fieldName: String, value: Any) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}
