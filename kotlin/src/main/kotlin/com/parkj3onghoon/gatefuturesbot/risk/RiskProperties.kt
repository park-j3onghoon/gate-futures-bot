package com.parkj3onghoon.gatefuturesbot.risk

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 리스크 한도. application.yml의 risk.* 에서 바인딩.
 *
 * ```yaml
 * risk:
 *   max-open-positions: 3
 *   daily-loss-limit-pct: 5.0
 *   consecutive-failure-threshold: 3
 * ```
 */
@Validated
@ConfigurationProperties(prefix = "risk")
data class RiskProperties(
    /** 동시에 열릴 수 있는 포지션 최대 수. */
    @field:Positive val maxOpenPositions: Int = 3,
    /** 일일 누적 손실률 한도(% 단위, 양수). 이를 초과하면 신규 진입 차단. */
    @field:Positive val dailyLossLimitPct: Double = 5.0,
    /** 연속 주문 실패 횟수 한도. 초과하면 해당 contract 진입 일시 차단. */
    @field:Positive val consecutiveFailureThreshold: Int = 3,
)
