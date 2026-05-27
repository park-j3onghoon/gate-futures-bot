package com.parkj3onghoon.gatefuturesbot.events

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * 도메인에서 발생한 의미 있는 사건의 append-only 기록.
 *
 * 용도:
 * - 디버깅: "어제 15:30에 왜 포지션 오픈했지?" 를 재구성
 * - 감사: 모든 주문/상태 변경 이력
 * - 재현: 과거 이벤트로 상태 rebuild (event sourcing 기본 원리)
 *
 * Spring Data JDBC 사용 — JPA 대비 경량, Entity 라이프사이클 단순.
 */
@Table("domain_events")
data class DomainEvent(
    @Id val id: Long? = null,
    /** 이벤트 타입. 예: "PositionOpened", "OrderFailed", "RiskDenied" */
    val type: String,
    /** 관련 contract (없으면 "" — 전역 이벤트) */
    val contract: String,
    /** JSON 페이로드 (세부 필드). 스키마는 type별로 다름. */
    val payload: String,
    val occurredAt: Instant = Instant.now(),
)
