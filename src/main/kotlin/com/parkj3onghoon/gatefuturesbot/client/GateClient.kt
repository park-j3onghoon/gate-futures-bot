package com.parkj3onghoon.gatefuturesbot.client

import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.exception.AuthenticationException
import com.parkj3onghoon.gatefuturesbot.exception.GateFuturesException
import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.exception.OrderException
import com.parkj3onghoon.gatefuturesbot.exception.RateLimitException
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import io.gate.gateapi.ApiException
import io.gate.gateapi.GateApiException
import io.gate.gateapi.api.FuturesApi
import io.gate.gateapi.models.FuturesAccount
import io.gate.gateapi.models.FuturesOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GateClient(
    private val apiProperties: ApiProperties,
    internal val futuresApi: FuturesApi
) {

    private val logger = LoggerFactory.getLogger(GateClient::class.java)

    companion object {
        const val MARKET_PRICE = "0"
        private const val DEFAULT_CROSS_LEVERAGE_LIMIT = "0"
    }

    fun createOrder(
        contract: String,
        size: Long,
        price: String = MARKET_PRICE,
        tif: FuturesOrder.TifEnum = FuturesOrder.TifEnum.IOC
    ): OrderResult {
        return withRetry("createOrder(contract=$contract, size=$size)") {
            try {
                val order = FuturesOrder().apply {
                    this.contract = contract
                    this.size = size
                    this.price = price
                    this.tif = tif
                }

                logger.debug("주문 생성: contract={}, size={}, price={}, tif={}", contract, size, price, tif)
                val result = futuresApi.createFuturesOrder(apiProperties.settle, order, null)
                OrderResult.from(result)
            } catch (e: GateApiException) {
                throw mapGateException(e, "createOrder(contract=$contract, size=$size)")
            } catch (e: ApiException) {
                throw wrapApiException(e, "createOrder(contract=$contract, size=$size)")
            }
        }
    }

    fun getPosition(contract: String): Position? {
        return try {
            val pos = futuresApi.getPosition(apiProperties.settle, contract).execute()
            if (pos.size == null || pos.size == 0L) {
                null
            } else {
                Position.from(pos)
            }
        } catch (e: GateApiException) {
            throw mapGateException(e, "getPosition(contract=$contract)")
        } catch (e: ApiException) {
            throw wrapApiException(e, "getPosition(contract=$contract)")
        }
    }

    fun updateLeverage(contract: String, leverage: Int) {
        try {
            futuresApi.updatePositionLeverage(
                apiProperties.settle,
                contract,
                leverage.toString(),
                DEFAULT_CROSS_LEVERAGE_LIMIT,
                null
            )
            logger.debug("레버리지 설정: contract={}, leverage={}", contract, leverage)
        } catch (e: GateApiException) {
            throw mapGateException(e, "updateLeverage(contract=$contract, leverage=$leverage)")
        } catch (e: ApiException) {
            throw wrapApiException(e, "updateLeverage(contract=$contract, leverage=$leverage)")
        }
    }

    fun getAccount(): FuturesAccount? {
        return try {
            futuresApi.listFuturesAccounts(apiProperties.settle)
        } catch (e: GateApiException) {
            throw mapGateException(e, "getAccount()")
        } catch (e: ApiException) {
            throw wrapApiException(e, "getAccount()")
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

    private fun <T> withRetry(context: String, action: () -> T): T {
        return try {
            action()
        } catch (e: RateLimitException) {
            logger.warn("Rate limit 도달, 1.5초 후 재시도: {}", context)
            Thread.sleep(1500)
            action()
        }
    }
}
