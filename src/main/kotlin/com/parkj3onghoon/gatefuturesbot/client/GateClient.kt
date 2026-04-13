package com.parkj3onghoon.gatefuturesbot.client

import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.exception.AuthenticationException
import com.parkj3onghoon.gatefuturesbot.exception.InsufficientBalanceException
import com.parkj3onghoon.gatefuturesbot.exception.OrderException
import com.parkj3onghoon.gatefuturesbot.exception.RateLimitException
import com.parkj3onghoon.gatefuturesbot.model.OrderResult
import com.parkj3onghoon.gatefuturesbot.model.Position
import io.gate.gateapi.ApiClient
import io.gate.gateapi.ApiException
import io.gate.gateapi.GateApiException
import io.gate.gateapi.api.FuturesApi
import io.gate.gateapi.models.FuturesAccount
import io.gate.gateapi.models.FuturesOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GateClient(private val apiProperties: ApiProperties) {

    private val logger = LoggerFactory.getLogger(GateClient::class.java)
    internal lateinit var futuresApi: FuturesApi

    init {
        val client = ApiClient()
        client.setBasePath(apiProperties.host)
        client.setApiKeySecret(apiProperties.key, apiProperties.secret)
        futuresApi = FuturesApi(client)
    }

    fun createOrder(contract: String, size: Long, price: String = "0", tif: String = "ioc"): OrderResult {
        return withRetry {
            try {
                val order = FuturesOrder()
                order.contract = contract
                order.size = size
                order.price = price
                order.tif = FuturesOrder.TifEnum.fromValue(tif)

                logger.debug("주문 생성: contract={}, size={}, price={}, tif={}", contract, size, price, tif)
                val result = futuresApi.createFuturesOrder(apiProperties.settle, order, null)
                OrderResult.from(result)
            } catch (e: GateApiException) {
                throw mapGateException(e)
            } catch (e: ApiException) {
                throw wrapApiException(e)
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
            throw mapGateException(e)
        } catch (e: ApiException) {
            throw wrapApiException(e)
        }
    }

    fun updateLeverage(contract: String, leverage: Int) {
        try {
            futuresApi.updatePositionLeverage(
                apiProperties.settle,
                contract,
                leverage.toString(),
                "0",
                null
            )
            logger.debug("레버리지 설정: contract={}, leverage={}", contract, leverage)
        } catch (e: GateApiException) {
            throw mapGateException(e)
        } catch (e: ApiException) {
            throw wrapApiException(e)
        }
    }

    fun getAccount(): FuturesAccount {
        return try {
            futuresApi.listFuturesAccounts(apiProperties.settle)
        } catch (e: GateApiException) {
            throw mapGateException(e)
        } catch (e: ApiException) {
            throw wrapApiException(e)
        }
    }

    private fun mapGateException(e: GateApiException): RuntimeException {
        val label = e.errorLabel ?: ""
        val message = e.errorMessage ?: e.message ?: "Unknown Gate.io API error"
        logger.error("Gate.io API 에러: label={}, message={}", label, message)

        return when (label) {
            "INVALID_KEY" -> AuthenticationException(message)
            "BALANCE_NOT_ENOUGH" -> InsufficientBalanceException(message)
            "RATE_LIMIT" -> RateLimitException(message)
            else -> OrderException(message, e)
        }
    }

    private fun wrapApiException(e: ApiException): OrderException {
        return OrderException("Gate.io API 호출 실패: ${e.message}", e)
    }

    private fun <T> withRetry(action: () -> T): T {
        return try {
            action()
        } catch (e: RateLimitException) {
            logger.warn("Rate limit 도달, 1.5초 후 재시도")
            Thread.sleep(1500)
            action()
        }
    }
}
