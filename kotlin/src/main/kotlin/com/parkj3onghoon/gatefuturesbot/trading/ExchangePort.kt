package com.parkj3onghoon.gatefuturesbot.trading

import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position

/**
 * 거래소에 대한 도메인 Port (Hexagonal Architecture의 outgoing port).
 *
 * trading/worker 레이어는 이 interface에만 의존한다. Gate.io, Binance 등
 * 실제 거래소 SDK는 client 패키지의 Adapter 구현체로 격리된다.
 *
 * 장점:
 * - 테스트 시 mockk로 Adapter 전체를 한번에 대체
 * - 거래소 교체/추가가 Adapter 새로 쓰기로 귀결 (OCP)
 * - 도메인이 SDK 예외/타입을 몰라도 됨
 */
interface ExchangePort {
    fun createOrder(
        contract: String,
        size: Long,
        leverage: Int,
    ): OrderResult

    fun closePosition(contract: String): OrderResult

    fun getPosition(contract: String): Position?

    fun getCandles(
        contract: String,
        interval: Interval,
        limit: Int? = null,
        fromSec: Long? = null,
        toSec: Long? = null,
    ): List<Candle>

    fun getLatestPrice(
        contract: String,
        interval: Interval = Interval.MIN_1,
    ): Double
}
