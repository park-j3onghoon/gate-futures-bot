package com.parkj3onghoon.gatefuturesbot.client

import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import com.parkj3onghoon.gatefuturesbot.trading.ExchangePort
import org.springframework.stereotype.Component

/**
 * ExchangePort의 Gate.io 구현체.
 *
 * - GateClient(SDK 래퍼)를 호출하여 도메인 API로 번역
 * - 이 Adapter가 있어 trading/worker 레이어는 GateClient를 직접 참조하지 않음
 * - SDK가 바뀌거나 거래소가 바뀌면 이 파일만 교체
 */
@Component
class GateExchangeAdapter(
    private val client: GateClient,
) : ExchangePort {
    override fun createOrder(
        contract: String,
        size: Long,
        leverage: Int,
    ): OrderResult {
        client.updateLeverage(contract, leverage)
        return client.createOrder(contract, size)
    }

    override fun closePosition(contract: String): OrderResult = client.closePosition(contract)

    override fun getPosition(contract: String): Position? = client.getPosition(contract)

    override fun getCandles(
        contract: String,
        interval: Interval,
        limit: Int?,
        fromSec: Long?,
        toSec: Long?,
    ): List<Candle> = client.getCandlesticks(contract, interval, limit, fromSec, toSec)

    override fun getLatestPrice(
        contract: String,
        interval: Interval,
    ): Double {
        val candles = client.getCandlesticks(contract, interval, limit = 1)
        return candles.lastOrNull()?.closePrice
            ?: throw MarketDataException("캔들 데이터 없음: $contract")
    }
}
