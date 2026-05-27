package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.exception.PositionException
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
    private lateinit var exchange: ExchangePort

    @BeforeEach
    fun setUp() {
        exchange = mockk()
        trader = FuturesTrader(exchange)
    }

    private fun orderResult(
        id: Long,
        size: Long,
    ): OrderResult =
        OrderResult(
            id = id,
            contract = "BTC_USDT",
            size = size,
            price = "0",
            status = "finished",
            fillPrice = "50000",
            createTime = 1700000000.0,
        )

    @Test
    fun `should open long position successfully`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        every { exchange.createOrder("BTC_USDT", 1L, 5) } returns orderResult(1L, 1L)

        val result = trader.openLong("BTC_USDT", 1, 5)

        assertEquals(1L, result.id)
        verify { exchange.createOrder("BTC_USDT", 1L, 5) }
    }

    @Test
    fun `should throw InsufficientBalanceException on long with insufficient balance`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        every {
            exchange.createOrder("BTC_USDT", 1L, 5)
        } throws InsufficientBalanceException("Insufficient balance")

        assertThrows<InsufficientBalanceException> {
            trader.openLong("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should open short position successfully`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        every { exchange.createOrder("BTC_USDT", -1L, 5) } returns orderResult(2L, -1L)

        val result = trader.openShort("BTC_USDT", 1, 5)

        assertEquals(2L, result.id)
        verify { exchange.createOrder("BTC_USDT", -1L, 5) }
    }

    @Test
    fun `should throw InsufficientBalanceException on short with insufficient balance`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        every {
            exchange.createOrder("BTC_USDT", -1L, 5)
        } throws InsufficientBalanceException("Insufficient balance")

        assertThrows<InsufficientBalanceException> {
            trader.openShort("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should throw PositionException when position already exists on long`() {
        val existing = Position("BTC_USDT", 1L, "50000", 5, "100.5", "50.3")
        every { exchange.getPosition("BTC_USDT") } returns existing

        assertThrows<PositionException> {
            trader.openLong("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should return null when no position exists`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        assertNull(trader.getCurrentPosition("BTC_USDT"))
    }

    @Test
    fun `should return position when position exists`() {
        val existing = Position("BTC_USDT", 1L, "50000", 5, "100.5", "50.3")
        every { exchange.getPosition("BTC_USDT") } returns existing
        val position = trader.getCurrentPosition("BTC_USDT")
        assertNotNull(position)
        assertEquals(1L, position.size)
    }

    @Test
    fun `should close long position successfully`() {
        val longPosition = Position("BTC_USDT", 2L, "50000", 5, "100", "0")
        every { exchange.getPosition("BTC_USDT") } returns longPosition
        every { exchange.closePosition("BTC_USDT") } returns orderResult(10L, 0L)

        val result = trader.closeLong("BTC_USDT")

        assertEquals(10L, result.id)
        verify { exchange.closePosition("BTC_USDT") }
    }

    @Test
    fun `should close short position successfully`() {
        val shortPosition = Position("BTC_USDT", -2L, "50000", 5, "100", "0")
        every { exchange.getPosition("BTC_USDT") } returns shortPosition
        every { exchange.closePosition("BTC_USDT") } returns orderResult(11L, 0L)

        val result = trader.closeShort("BTC_USDT")

        assertEquals(11L, result.id)
        verify { exchange.closePosition("BTC_USDT") }
    }

    @Test
    fun `should throw PositionException when closing long without position`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        assertThrows<PositionException> { trader.closeLong("BTC_USDT") }
    }

    @Test
    fun `should throw PositionException when closeLong on short position`() {
        val short = Position("BTC_USDT", -1L, "50000", 5, "0", "0")
        every { exchange.getPosition("BTC_USDT") } returns short
        assertThrows<PositionException> { trader.closeLong("BTC_USDT") }
    }

    @Test
    fun `should throw PositionException when closeShort on long position`() {
        val long = Position("BTC_USDT", 1L, "50000", 5, "0", "0")
        every { exchange.getPosition("BTC_USDT") } returns long
        assertThrows<PositionException> { trader.closeShort("BTC_USDT") }
    }

    @Test
    fun `closePosition should auto-detect long direction`() {
        val long = Position("BTC_USDT", 3L, "50000", 5, "0", "0")
        every { exchange.getPosition("BTC_USDT") } returns long
        every { exchange.closePosition("BTC_USDT") } returns orderResult(20L, 0L)

        val result = trader.closePosition("BTC_USDT")

        assertEquals(20L, result.id)
    }

    @Test
    fun `closePosition should throw PositionException when no position exists`() {
        every { exchange.getPosition("BTC_USDT") } returns null
        assertThrows<PositionException> { trader.closePosition("BTC_USDT") }
    }
}
