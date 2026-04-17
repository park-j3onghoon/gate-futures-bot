package com.parkj3onghoon.gatefuturesbot.market

import com.parkj3onghoon.gatefuturesbot.client.GateClient
import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import org.springframework.stereotype.Service

@Service
class MarketDataService(private val client: GateClient) {

    fun getCandles(
        contract: String,
        interval: Interval,
        limit: Int? = null,
        fromSec: Long? = null,
        toSec: Long? = null
    ): List<Candle> {
        return client.getCandlesticks(contract, interval, limit, fromSec, toSec)
    }

    fun getLatestPrice(contract: String, interval: Interval = Interval.MIN_1): Double {
        val candles = client.getCandlesticks(contract, interval, limit = 1)
        val latest = candles.lastOrNull()
            ?: throw MarketDataException(
                "캔들 데이터가 없습니다: contract=$contract, interval=${interval.code}"
            )
        return latest.closePrice
    }
}
