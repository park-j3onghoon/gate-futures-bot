package com.parkj3onghoon.gatefuturesbot.market

import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.trading.ExchangePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 마켓 데이터 조회 서비스. ExchangePort에 의존 (Hexagonal).
 * 추가 정책:
 * - 빈 캔들 응답은 warn 로그로 가시화
 */
@Service
class MarketDataService(
    private val exchange: ExchangePort,
) {
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)

    fun getCandles(
        contract: String,
        interval: Interval,
        limit: Int? = null,
        fromSec: Long? = null,
        toSec: Long? = null,
    ): List<Candle> {
        val candles = exchange.getCandles(contract, interval, limit, fromSec, toSec)
        if (candles.isEmpty()) {
            logger.warn(
                "빈 캔들 응답: contract={}, interval={}, limit={}, fromSec={}, toSec={}",
                contract,
                interval.code,
                limit,
                fromSec,
                toSec,
            )
        }
        return candles
    }

    fun getLatestPrice(
        contract: String,
        interval: Interval = Interval.MIN_1,
    ): Double {
        val candles = exchange.getCandles(contract, interval, limit = 1)
        val latest =
            candles.lastOrNull()
                ?: throw MarketDataException(
                    "캔들 데이터가 없습니다: contract=$contract, interval=${interval.code}",
                )
        return latest.closePrice
    }
}
