package com.parkj3onghoon.gatefuturesbot.position

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * contract별 포지션 상태를 추적한다.
 * thread-safe + 상태 전이 검증 포함.
 */
@Component
class PositionTracker(
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) {
    private val logger = LoggerFactory.getLogger(PositionTracker::class.java)
    private val states = ConcurrentHashMap<String, PositionRecord>()

    data class PositionRecord(
        val state: PositionState,
        val updatedAt: Instant,
        val reason: String = "",
    )

    fun stateOf(contract: String): PositionState = states[contract]?.state ?: PositionState.IDLE

    fun recordOf(contract: String): PositionRecord = states[contract] ?: PositionRecord(PositionState.IDLE, Instant.now(clock))

    /**
     * 상태 전이 시도. 유효하지 않은 전이는 예외 발생.
     * 성공시 true, 이미 해당 상태면 false.
     */
    fun transition(
        contract: String,
        to: PositionState,
        reason: String = "",
    ): Boolean {
        var changed = false
        states.compute(contract) { _, existing ->
            val fromState = existing?.state ?: PositionState.IDLE
            if (fromState == to) {
                changed = false
                return@compute existing
            }
            require(isValid(fromState, to)) {
                "invalid transition: $fromState -> $to (contract=$contract)"
            }
            changed = true
            logger.info(
                "상태 전이: contract={}, {} -> {}, reason={}",
                contract,
                fromState,
                to,
                reason,
            )
            PositionRecord(to, Instant.now(clock), reason)
        }
        return changed
    }

    /**
     * 거래소 실제 상태와 우리 상태를 비교해 불일치 감지.
     * - 우리: OPEN, 거래소: null → 외부 청산으로 간주 (CLOSED로 전환)
     * - 우리: OPENING, 거래소: null (n초 이상) → FAILED
     */
    fun reconcile(
        contract: String,
        exchangeHasPosition: Boolean,
        failOpeningAfter: java.time.Duration = java.time.Duration.ofSeconds(30),
    ) {
        val record = states[contract] ?: return
        val ageMs = Instant.now(clock).toEpochMilli() - record.updatedAt.toEpochMilli()
        when {
            record.state == PositionState.OPEN && !exchangeHasPosition -> {
                logger.warn("외부 청산 감지: contract={}. OPEN→CLOSED", contract)
                transition(contract, PositionState.CLOSED, reason = "external close detected")
            }
            record.state == PositionState.OPENING &&
                !exchangeHasPosition &&
                ageMs > failOpeningAfter.toMillis() -> {
                logger.warn("OPENING 상태 타임아웃: contract={}. OPENING→FAILED", contract)
                transition(contract, PositionState.FAILED, reason = "opening timeout")
            }
        }
    }

    /**
     * FAILED 상태에서 다시 IDLE로 복구 (관리자 개입 후).
     */
    fun resetToIdle(contract: String) {
        states[contract] = PositionRecord(PositionState.IDLE, Instant.now(clock), "manual reset")
        logger.info("수동 리셋: contract={} -> IDLE", contract)
    }

    fun snapshot(): Map<String, PositionRecord> = states.toMap()

    private fun isValid(
        from: PositionState,
        to: PositionState,
    ): Boolean =
        when (from) {
            PositionState.IDLE -> to == PositionState.OPENING
            PositionState.OPENING -> to == PositionState.OPEN || to == PositionState.FAILED
            PositionState.OPEN -> to == PositionState.CLOSING || to == PositionState.CLOSED
            PositionState.CLOSING -> to == PositionState.CLOSED || to == PositionState.FAILED
            PositionState.CLOSED -> to == PositionState.OPENING // 재진입
            PositionState.FAILED -> to == PositionState.IDLE // 수동 리셋
        }
}
