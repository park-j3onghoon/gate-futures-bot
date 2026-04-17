package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.client.GateClient
import com.parkj3onghoon.gatefuturesbot.exception.PositionException
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FuturesTrader(private val client: GateClient) {

    private val logger = LoggerFactory.getLogger(FuturesTrader::class.java)

    fun openLong(contract: String, size: Int, leverage: Int = 5): OrderResult {
        return openPosition(contract, size.toLong(), leverage, "롱")
    }

    fun openShort(contract: String, size: Int, leverage: Int = 5): OrderResult {
        return openPosition(contract, -size.toLong(), leverage, "숏")
    }

    fun getCurrentPosition(contract: String): Position? {
        return client.getPosition(contract)
    }

    private fun openPosition(contract: String, size: Long, leverage: Int, direction: String): OrderResult {
        val currentPosition = client.getPosition(contract)
        if (currentPosition != null) {
            throw PositionException(
                "이미 포지션이 존재합니다: contract=$contract, 기존size=${currentPosition.size}, " +
                    "entryPrice=${currentPosition.entryPrice}, leverage=${currentPosition.leverage}"
            )
        }

        client.updateLeverage(contract, leverage)
        logger.info("{} 포지션 진입: contract={}, size={}, leverage={}", direction, contract, size, leverage)
        return client.createOrder(contract, size)
    }
}
