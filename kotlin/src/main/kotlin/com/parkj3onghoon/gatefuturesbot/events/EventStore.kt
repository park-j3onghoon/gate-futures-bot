package com.parkj3onghoon.gatefuturesbot.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 도메인 이벤트를 영속화하는 facade.
 * CoinWorker/Trader/RiskManager가 호출해 append-only로 기록.
 *
 * 장애 격리: 이벤트 저장 실패가 비즈니스 로직을 중단시키지 않도록 catch + warn.
 */
@Service
class EventStore(
    private val repository: DomainEventRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val logger = LoggerFactory.getLogger(EventStore::class.java)

    fun record(
        type: String,
        contract: String = "",
        payload: Map<String, Any?> = emptyMap(),
    ): DomainEvent? =
        try {
            val event =
                DomainEvent(
                    type = type,
                    contract = contract,
                    payload = objectMapper.writeValueAsString(payload),
                )
            repository.save(event)
        } catch (e: Exception) {
            // 비즈니스 로직이 이벤트 저장 실패로 죽어서는 안 됨
            logger.warn("이벤트 저장 실패: type={}, contract={}, 원인={}", type, contract, e.message)
            null
        }

    fun findByContract(contract: String): List<DomainEvent> = repository.findAllByContractOrderByOccurredAtDesc(contract)

    fun findByType(type: String): List<DomainEvent> = repository.findAllByTypeOrderByOccurredAtDesc(type)

    fun countByType(type: String): Long = repository.countByType(type)
}

/**
 * 이벤트 타입 상수. 오타 방지 + IDE 자동완성.
 */
object EventTypes {
    const val POSITION_OPEN_ATTEMPT = "PositionOpenAttempt"
    const val POSITION_OPENED = "PositionOpened"
    const val POSITION_OPEN_FAILED = "PositionOpenFailed"
    const val POSITION_CLOSE_ATTEMPT = "PositionCloseAttempt"
    const val POSITION_CLOSED = "PositionClosed"
    const val POSITION_CLOSE_FAILED = "PositionCloseFailed"
    const val RISK_DENIED = "RiskDenied"
    const val STATE_TRANSITION = "StateTransition"
    const val EXTERNAL_CLOSE_DETECTED = "ExternalCloseDetected"
}
