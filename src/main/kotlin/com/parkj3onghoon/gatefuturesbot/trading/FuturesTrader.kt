package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.client.GateClient
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FuturesTrader(private val client: GateClient) {

    private val logger = LoggerFactory.getLogger(FuturesTrader::class.java)

    fun openLong(contract: String, size: Int, leverage: Int = 5): OrderResult {
        val currentPosition = client.getPosition(contract)
        if (currentPosition != null) {
            logger.warn("이미 포지션이 존재합니다: contract={}, size={}", contract, currentPosition.size)
        }

        client.updateLeverage(contract, leverage)
        logger.info("롱 포지션 진입: contract={}, size={}, leverage={}", contract, size, leverage)
        return client.createOrder(contract, size.toLong())
    }

    fun openShort(contract: String, size: Int, leverage: Int = 5): OrderResult {
        val currentPosition = client.getPosition(contract)
        if (currentPosition != null) {
            logger.warn("이미 포지션이 존재합니다: contract={}, size={}", contract, currentPosition.size)
        }

        client.updateLeverage(contract, leverage)
        logger.info("숏 포지션 진입: contract={}, size={}, leverage={}", contract, size, leverage)
        return client.createOrder(contract, -size.toLong())
    }

    fun getCurrentPosition(contract: String): Position? {
        return client.getPosition(contract)
    }
}
