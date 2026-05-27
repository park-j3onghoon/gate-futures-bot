package com.parkj3onghoon.gatefuturesbot.model

/**
 * OHLCV 캔들 도메인 모델.
 * SDK 타입(FuturesCandlestick)과의 매핑은 GateClient가 담당한다.
 */
data class Candle(
    val timestamp: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: Long,
) {
    val closePrice: Double get() = close.toDouble()
}
