package com.parkj3onghoon.gatefuturesbot.client

import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.exception.AuthenticationException
import com.parkj3onghoon.gatefuturesbot.exception.GateFuturesException
import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.exception.MarketDataException
import com.parkj3onghoon.gatefuturesbot.exception.OrderException
import com.parkj3onghoon.gatefuturesbot.exception.RateLimitException
import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Interval
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import io.gate.gateapi.ApiException
import io.gate.gateapi.GateApiException
import io.gate.gateapi.api.FuturesApi
import io.gate.gateapi.models.FuturesAccount
import io.gate.gateapi.models.FuturesCandlestick
import io.gate.gateapi.models.FuturesOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import io.gate.gateapi.models.Position as SdkPosition

@Component
class GateClient(
    private val apiProperties: ApiProperties,
    internal val futuresApi: FuturesApi
) {

    private val logger = LoggerFactory.getLogger(GateClient::class.java)

    companion object {
        const val MARKET_PRICE = "0"
        private const val DEFAULT_CROSS_LEVERAGE_LIMIT = "0"
        private const val RATE_LIMIT_RETRY_SLEEP_MS = 1500L
    }

    fun createOrder(
        contract: String,
        size: Long,
        price: String = MARKET_PRICE,
        tif: FuturesOrder.TifEnum = FuturesOrder.TifEnum.IOC
    ): OrderResult = callApi("createOrder(contract=$contract, size=$size)") {
        val order = FuturesOrder().apply {
            this.contract = contract
            this.size = size
            this.price = price
            this.tif = tif
        }
        logger.debug("주문 생성: contract={}, size={}, price={}, tif={}", contract, size, price, tif)
        toOrderResult(futuresApi.createFuturesOrder(apiProperties.settle, order, null))
    }

    fun closePosition(contract: String): OrderResult =
        callApi("closePosition(contract=$contract)") {
            val order = FuturesOrder().apply {
                this.contract = contract
                this.size = 0L
                this.price = MARKET_PRICE
                this.tif = FuturesOrder.TifEnum.IOC
                this.close = true
            }
            logger.debug("포지션 청산: contract={}", contract)
            toOrderResult(futuresApi.createFuturesOrder(apiProperties.settle, order, null))
        }

    fun getPosition(contract: String): Position? =
        callApi("getPosition(contract=$contract)") {
            val pos = futuresApi.getPosition(apiProperties.settle, contract).execute()
            if (pos.size == null || pos.size == 0L) null else toPosition(pos)
        }

    fun updateLeverage(contract: String, leverage: Int) {
        callApi("updateLeverage(contract=$contract, leverage=$leverage)") {
            futuresApi.updatePositionLeverage(
                apiProperties.settle,
                contract,
                leverage.toString(),
                DEFAULT_CROSS_LEVERAGE_LIMIT,
                null
            )
            logger.debug("레버리지 설정: contract={}, leverage={}", contract, leverage)
        }
    }

    fun getCandlesticks(
        contract: String,
        interval: Interval,
        limit: Int? = null,
        fromSec: Long? = null,
        toSec: Long? = null
    ): List<Candle> = callApi("getCandlesticks(contract=$contract, interval=${interval.code})") {
        val request = futuresApi.listFuturesCandlesticks(apiProperties.settle, contract)
            .interval(interval.code)
        limit?.let { request.limit(it) }
        fromSec?.let { request.from(it) }
        toSec?.let { request.to(it) }

        val candles = request.execute()
        logger.debug(
            "캔들 조회: contract={}, interval={}, limit={}, count={}",
            contract, interval.code, limit, candles.size
        )
        candles.map { toCandle(it) }
    }

    private fun toOrderResult(order: FuturesOrder): OrderResult = OrderResult(
        id = order.id ?: throw OrderException("주문 응답에 id가 없습니다"),
        contract = order.contract ?: throw OrderException("주문 응답에 contract가 없습니다"),
        size = order.size ?: throw OrderException("주문 응답에 size가 없습니다"),
        price = order.price ?: "0",
        status = order.status?.value ?: "unknown",
        fillPrice = order.fillPrice ?: "0",
        createTime = order.createTime ?: 0.0
    )

    private fun toPosition(pos: SdkPosition): Position = Position(
        contract = pos.contract ?: "",
        size = pos.size ?: 0L,
        entryPrice = pos.entryPrice ?: "0",
        leverage = pos.leverage?.toIntOrNull() ?: 0,
        unrealisedPnl = pos.unrealisedPnl ?: "0",
        realisedPnl = pos.realisedPnl ?: "0"
    )

    private fun toCandle(c: FuturesCandlestick): Candle {
        val t = c.t ?: throw MarketDataException("캔들 응답에 timestamp(t)가 없습니다")
        val close = c.c ?: throw MarketDataException("캔들 응답에 close(c)가 없습니다")
        val open = c.o ?: throw MarketDataException("캔들 응답에 open(o)가 없습니다")
        val high = c.h ?: throw MarketDataException("캔들 응답에 high(h)가 없습니다")
        val low = c.l ?: throw MarketDataException("캔들 응답에 low(l)가 없습니다")
        return Candle(
            timestamp = t.toLong(),
            open = open, high = high, low = low, close = close,
            volume = c.v ?: 0L
        )
    }

    fun getAccount(): FuturesAccount? = callApi("getAccount()") {
        futuresApi.listFuturesAccounts(apiProperties.settle)
    }

    /**
     * Gate.io API 호출 공통 래퍼.
     * - GateApiException → 도메인 예외로 매핑
     * - ApiException → OrderException으로 래핑
     * - RateLimitException은 한 번 재시도 (write/read 모두 대칭)
     */
    private inline fun <T> callApi(context: String, action: () -> T): T = withRetry(context) {
        try {
            action()
        } catch (e: GateApiException) {
            throw mapGateException(e, context)
        } catch (e: ApiException) {
            throw wrapApiException(e, context)
        }
    }

    private inline fun <T> withRetry(context: String, action: () -> T): T {
        return try {
            action()
        } catch (e: RateLimitException) {
            logger.warn("Rate limit 도달, {}ms 후 재시도: {}", RATE_LIMIT_RETRY_SLEEP_MS, context)
            Thread.sleep(RATE_LIMIT_RETRY_SLEEP_MS)
            action()
        }
    }

    private fun mapGateException(e: GateApiException, context: String): GateFuturesException {
        val label = e.errorLabel ?: ""
        val message = e.errorMessage ?: e.message ?: "Unknown Gate.io API error"
        logger.warn("Gate.io API 에러: label={}, message={}, context={}", label, message, context)

        return when (label) {
            "INVALID_KEY" -> AuthenticationException("[$context] $message", e)
            "BALANCE_NOT_ENOUGH" -> InsufficientBalanceException("[$context] $message", e)
            "RATE_LIMIT" -> RateLimitException("[$context] $message", e)
            else -> OrderException("[$context] $message", e)
        }
    }

    private fun wrapApiException(e: ApiException, context: String): OrderException {
        return OrderException("[$context] Gate.io API 호출 실패: ${e.message}", e)
    }
}
