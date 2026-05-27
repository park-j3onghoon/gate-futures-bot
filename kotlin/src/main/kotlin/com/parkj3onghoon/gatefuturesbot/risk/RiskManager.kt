package com.parkj3onghoon.gatefuturesbot.risk

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 진입 전 게이트키퍼. 모든 신규 포지션 진입이 이 매니저를 통과해야 한다.
 *
 * 3가지 체크:
 * 1. 동시 오픈 포지션 수 ≤ maxOpenPositions
 * 2. 오늘 누적 손실률 ≤ dailyLossLimitPct
 * 3. 해당 contract의 연속 실패 ≤ consecutiveFailureThreshold
 *
 * 쓰레드 안전: ConcurrentHashMap + Atomic*.
 * 날짜는 Clock 주입으로 테스트에서 제어 가능.
 */
@Component
class RiskManager(
    private val properties: RiskProperties,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) {
    private val logger = LoggerFactory.getLogger(RiskManager::class.java)

    private val openPositions = AtomicInteger(0)

    // contract -> 연속 실패 카운터
    private val failureCounters = ConcurrentHashMap<String, AtomicInteger>()

    // 일일 PnL 추적 (자정 지나면 리셋)
    private val dailyPnl = AtomicReference(DailyPnl(today(), 0.0))

    data class DailyPnl(
        val date: LocalDate,
        val realizedLossPct: Double,
    )

    fun canOpenPosition(contract: String): RiskDecision {
        if (openPositions.get() >= properties.maxOpenPositions) {
            return RiskDecision.Deny(
                "최대 포지션 수 초과: ${openPositions.get()}/${properties.maxOpenPositions}",
            )
        }
        val pnl = currentDailyPnl()
        if (pnl.realizedLossPct >= properties.dailyLossLimitPct) {
            return RiskDecision.Deny(
                "일일 손실 한도 도달: ${pnl.realizedLossPct}% >= ${properties.dailyLossLimitPct}%",
            )
        }
        val failures = failureCounters[contract]?.get() ?: 0
        if (failures >= properties.consecutiveFailureThreshold) {
            return RiskDecision.Deny(
                "연속 실패 한도 도달: contract=$contract, failures=$failures",
            )
        }
        return RiskDecision.Allow
    }

    /** 포지션 오픈 성공 시 호출. */
    fun onPositionOpened(contract: String) {
        openPositions.incrementAndGet()
        failureCounters[contract]?.set(0)
        logger.info("포지션 오픈 기록: contract={}, total={}", contract, openPositions.get())
    }

    /** 포지션 청산 완료 시 호출. 실현 손실률 전달(이익이면 음수). */
    fun onPositionClosed(
        contract: String,
        realizedPnlPct: Double,
    ) {
        val remaining = openPositions.decrementAndGet()
        if (realizedPnlPct < 0) {
            accumulateLoss(-realizedPnlPct)
        }
        logger.info(
            "포지션 청산 기록: contract={}, pnl%={}, remainingOpen={}",
            contract,
            realizedPnlPct,
            remaining,
        )
    }

    /** 주문 실패 시 호출. 연속 실패 카운터 증가. */
    fun onOrderFailure(contract: String) {
        val counter = failureCounters.computeIfAbsent(contract) { AtomicInteger(0) }
        val count = counter.incrementAndGet()
        logger.warn("주문 실패 기록: contract={}, consecutiveFailures={}", contract, count)
    }

    /** 현재 상태 스냅샷 (관측용). */
    fun snapshot(): Snapshot {
        val pnl = currentDailyPnl()
        return Snapshot(
            openPositions = openPositions.get(),
            dailyLossPct = pnl.realizedLossPct,
            failureCounts = failureCounters.mapValues { it.value.get() },
        )
    }

    private fun accumulateLoss(lossPct: Double) {
        dailyPnl.updateAndGet { current ->
            val todayDate = today()
            if (current.date != todayDate) {
                DailyPnl(todayDate, lossPct)
            } else {
                current.copy(realizedLossPct = current.realizedLossPct + lossPct)
            }
        }
    }

    private fun currentDailyPnl(): DailyPnl {
        val todayDate = today()
        return dailyPnl.updateAndGet { current ->
            if (current.date != todayDate) DailyPnl(todayDate, 0.0) else current
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    data class Snapshot(
        val openPositions: Int,
        val dailyLossPct: Double,
        val failureCounts: Map<String, Int>,
    )
}
