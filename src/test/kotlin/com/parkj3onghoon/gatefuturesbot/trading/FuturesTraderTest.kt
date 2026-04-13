package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.client.GateClient
import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuturesTraderTest {

    private lateinit var trader: FuturesTrader
    private lateinit var client: GateClient

    @BeforeEach
    fun setUp() {
        client = mockk()
        trader = FuturesTrader(client)
    }

    @Test
    fun `should open long position successfully`() {
        val expectedResult = OrderResult(
            id = 1L,
            contract = "BTC_USDT",
            size = 1L,
            price = "0",
            status = "finished",
            fillPrice = "50000",
            createTime = 1700000000.0
        )

        every { client.getPosition("BTC_USDT") } returns null
        every { client.updateLeverage("BTC_USDT", 5) } returns Unit
        every { client.createOrder("BTC_USDT", 1L) } returns expectedResult

        val result = trader.openLong("BTC_USDT", 1, 5)

        assertEquals(1L, result.id)
        assertEquals(1L, result.size)
        verify { client.createOrder("BTC_USDT", 1L) }
        verify { client.updateLeverage("BTC_USDT", 5) }
    }

    @Test
    fun `should throw InsufficientBalanceException on long with insufficient balance`() {
        every { client.getPosition("BTC_USDT") } returns null
        every { client.updateLeverage("BTC_USDT", 5) } returns Unit
        every { client.createOrder("BTC_USDT", 1L) } throws InsufficientBalanceException("Insufficient balance")

        assertThrows<InsufficientBalanceException> {
            trader.openLong("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should open short position successfully`() {
        val expectedResult = OrderResult(
            id = 2L,
            contract = "BTC_USDT",
            size = -1L,
            price = "0",
            status = "finished",
            fillPrice = "50000",
            createTime = 1700000000.0
        )

        every { client.getPosition("BTC_USDT") } returns null
        every { client.updateLeverage("BTC_USDT", 5) } returns Unit
        every { client.createOrder("BTC_USDT", -1L) } returns expectedResult

        val result = trader.openShort("BTC_USDT", 1, 5)

        assertEquals(2L, result.id)
        assertEquals(-1L, result.size)
        verify { client.createOrder("BTC_USDT", -1L) }
        verify { client.updateLeverage("BTC_USDT", 5) }
    }

    @Test
    fun `should throw InsufficientBalanceException on short with insufficient balance`() {
        every { client.getPosition("BTC_USDT") } returns null
        every { client.updateLeverage("BTC_USDT", 5) } returns Unit
        every { client.createOrder("BTC_USDT", -1L) } throws InsufficientBalanceException("Insufficient balance")

        assertThrows<InsufficientBalanceException> {
            trader.openShort("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should return null when no position exists`() {
        every { client.getPosition("BTC_USDT") } returns null

        val position = trader.getCurrentPosition("BTC_USDT")

        assertNull(position)
    }

    @Test
    fun `should return position when position exists`() {
        val expectedPosition = Position(
            contract = "BTC_USDT",
            size = 1L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "100.5",
            realisedPnl = "50.3"
        )

        every { client.getPosition("BTC_USDT") } returns expectedPosition

        val position = trader.getCurrentPosition("BTC_USDT")

        assertNotNull(position)
        assertEquals("BTC_USDT", position.contract)
        assertEquals(1L, position.size)
    }
}
