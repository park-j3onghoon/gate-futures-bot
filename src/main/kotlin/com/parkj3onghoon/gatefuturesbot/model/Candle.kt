package com.parkj3onghoon.gatefuturesbot.model

import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import io.gate.gateapi.models.FuturesCandlestick

data class Candle(
    val timestamp: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: Long
) {
    val closePrice: Double get() = close.toDouble()

    companion object {
        fun from(c: FuturesCandlestick): Candle {
            val t = c.t ?: throw MarketDataException("캔들 응답에 timestamp(t)가 없습니다")
            val close = c.c ?: throw MarketDataException("캔들 응답에 close(c)가 없습니다")
            val open = c.o ?: throw MarketDataException("캔들 응답에 open(o)가 없습니다")
            val high = c.h ?: throw MarketDataException("캔들 응답에 high(h)가 없습니다")
            val low = c.l ?: throw MarketDataException("캔들 응답에 low(l)가 없습니다")
            return Candle(
                timestamp = t.toLong(),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = c.v ?: 0L
            )
        }
    }
}
