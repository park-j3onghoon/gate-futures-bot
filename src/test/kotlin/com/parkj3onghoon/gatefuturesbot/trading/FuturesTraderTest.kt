package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.client.GateClient
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
    fun `should throw PositionException when position already exists on long`() {
        val existingPosition = Position(
            contract = "BTC_USDT",
            size = 1L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "100.5",
            realisedPnl = "50.3"
        )

        every { client.getPosition("BTC_USDT") } returns existingPosition

        assertThrows<PositionException> {
            trader.openLong("BTC_USDT", 1, 5)
        }
    }

    @Test
    fun `should throw PositionException when position already exists on short`() {
        val existingPosition = Position(
            contract = "BTC_USDT",
            size = -1L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "-50.0",
            realisedPnl = "0"
        )

        every { client.getPosition("BTC_USDT") } returns existingPosition

        assertThrows<PositionException> {
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

    @Test
    fun `should close long position successfully`() {
        val longPosition = Position(
            contract = "BTC_USDT",
            size = 2L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "100",
            realisedPnl = "0"
        )
        val closeResult = OrderResult(
            id = 10L, contract = "BTC_USDT", size = 0L, price = "0",
            status = "finished", fillPrice = "51000", createTime = 1700000000.0
        )

        every { client.getPosition("BTC_USDT") } returns longPosition
        every { client.closePosition("BTC_USDT") } returns closeResult

        val result = trader.closeLong("BTC_USDT")

        assertEquals(10L, result.id)
        verify { client.closePosition("BTC_USDT") }
    }

    @Test
    fun `should close short position successfully`() {
        val shortPosition = Position(
            contract = "BTC_USDT",
            size = -2L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "100",
            realisedPnl = "0"
        )
        val closeResult = OrderResult(
            id = 11L, contract = "BTC_USDT", size = 0L, price = "0",
            status = "finished", fillPrice = "49000", createTime = 1700000000.0
        )

        every { client.getPosition("BTC_USDT") } returns shortPosition
        every { client.closePosition("BTC_USDT") } returns closeResult

        val result = trader.closeShort("BTC_USDT")

        assertEquals(11L, result.id)
        verify { client.closePosition("BTC_USDT") }
    }

    @Test
    fun `should throw PositionException when closing long without position`() {
        every { client.getPosition("BTC_USDT") } returns null

        assertThrows<PositionException> {
            trader.closeLong("BTC_USDT")
        }
    }

    @Test
    fun `should throw PositionException when closing short without position`() {
        every { client.getPosition("BTC_USDT") } returns null

        assertThrows<PositionException> {
            trader.closeShort("BTC_USDT")
        }
    }

    @Test
    fun `should throw PositionException when closeLong on short position`() {
        val shortPosition = Position(
            contract = "BTC_USDT",
            size = -1L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "0",
            realisedPnl = "0"
        )
        every { client.getPosition("BTC_USDT") } returns shortPosition

        assertThrows<PositionException> {
            trader.closeLong("BTC_USDT")
        }
    }

    @Test
    fun `should throw PositionException when closeShort on long position`() {
        val longPosition = Position(
            contract = "BTC_USDT",
            size = 1L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "0",
            realisedPnl = "0"
        )
        every { client.getPosition("BTC_USDT") } returns longPosition

        assertThrows<PositionException> {
            trader.closeShort("BTC_USDT")
        }
    }

    @Test
    fun `closePosition should auto-detect long direction`() {
        val longPosition = Position(
            contract = "BTC_USDT",
            size = 3L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "0",
            realisedPnl = "0"
        )
        val closeResult = OrderResult(
            id = 20L, contract = "BTC_USDT", size = 0L, price = "0",
            status = "finished", fillPrice = "51000", createTime = 1700000000.0
        )
        every { client.getPosition("BTC_USDT") } returns longPosition
        every { client.closePosition("BTC_USDT") } returns closeResult

        val result = trader.closePosition("BTC_USDT")

        assertEquals(20L, result.id)
        verify { client.closePosition("BTC_USDT") }
    }

    @Test
    fun `closePosition should auto-detect short direction`() {
        val shortPosition = Position(
            contract = "BTC_USDT",
            size = -3L,
            entryPrice = "50000",
            leverage = 5,
            unrealisedPnl = "0",
            realisedPnl = "0"
        )
        val closeResult = OrderResult(
            id = 21L, contract = "BTC_USDT", size = 0L, price = "0",
            status = "finished", fillPrice = "49000", createTime = 1700000000.0
        )
        every { client.getPosition("BTC_USDT") } returns shortPosition
        every { client.closePosition("BTC_USDT") } returns closeResult

        val result = trader.closePosition("BTC_USDT")

        assertEquals(21L, result.id)
        verify { client.closePosition("BTC_USDT") }
    }

    @Test
    fun `closePosition should throw PositionException when no position exists`() {
        every { client.getPosition("BTC_USDT") } returns null

        assertThrows<PositionException> {
            trader.closePosition("BTC_USDT")
        }
    }
}
