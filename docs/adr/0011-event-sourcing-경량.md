# ADR-0011 — Event Sourcing 경량 (H2 append-only)

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/09-event-sourcing.md

## Context

봇이 새벽에 이상한 동작을 했다 — 로그를 뒤져도 시간 순서·인과 관계 재구성이 어렵다.
로그는 자유 텍스트라 쿼리 불가, 파일에 흩어져 있어 contract별 추적 힘들다.

필요한 것:
- 시간 순서 정확한 도메인 이벤트 기록
- contract / type별 쿼리 가능
- "이 포지션은 어떻게 이 상태가 됐는가" 재구성

## Decision

**Full Event Sourcing이 아닌 경량 append-only 이벤트 로그**를 채택한다.

- 상태는 기존 객체(`PositionTracker`, `RiskManager`)가 in-memory로 유지 — 그대로 둠
- 이벤트는 **부가적으로** DB에 기록 (관측·분석용)
- 저장소: **H2 embedded** (`jdbc:h2:file:./data/events;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`)
- ORM: Spring Data JDBC (JPA보다 단순, 영속성 컨텍스트 없음)
- 스키마:
  ```sql
  CREATE TABLE domain_events (
    id BIGINT IDENTITY PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    contract VARCHAR(32),
    payload CLOB,           -- JSON
    occurred_at TIMESTAMP NOT NULL
  );
  -- (type), (contract), (occurred_at) 인덱스
  ```
- `EventStore.record()` 호출 실패는 비즈니스 로직에 영향 주지 않음 (try/catch + 로그)
- `@EnableJdbcRepositories`는 별도 `EventStoreConfig` 클래스에 둠 (`@WebMvcTest` 호환)

## Alternatives Considered

- **Full Event Sourcing(Axon/EventStore)**: 상태를 이벤트로만 저장. → ❌ 운영 비용 큼, 학습 곡선, 단일 프로세스 봇엔 과함.
- **PostgreSQL**: 견고함. → ❌ 외부 프로세스 필요, 단일 프로세스 봇엔 과함.
- **파일(JSON Lines)**: 단순. → ❌ 쿼리 불가, 인덱스 없음, 회전·삭제 직접 관리.
- **JPA/Hibernate**: → ❌ append-only에 영속성 컨텍스트는 오버킬, dirty checking 불필요.

## Consequences

### Positive
- 이벤트 기록을 DB 쿼리로 분석 가능 (`SELECT * FROM domain_events WHERE contract = ? ORDER BY occurred_at`)
- 외부 의존성 없음 — H2가 jar에 포함, 파일 한 개로 영속
- 비즈니스 로직과 결합 안 됨 — 이벤트 기록 실패해도 거래는 계속
- 추후 분산/대용량으로 가면 그때 PostgreSQL 전환 (이벤트는 append-only라 마이그레이션 단순)

### Negative
- H2는 단일 프로세스 락 — 다중 인스턴스 불가 (현재 단일이라 OK)
- 디스크 사용량 누적 — 회전/아카이브 정책 필요 (TODO)
- payload가 JSON CLOB이라 스키마 변경에 유연하지만 타입 안전성 약함

### Neutral
- 이벤트 타입은 `EventTypes` 상수 객체로 중앙화 (오타 방지)
- `@EnableJdbcRepositories`를 main class에서 분리한 부수 효과: `@WebMvcTest`에서 JdbcMappingContext 충돌 회피

## Follow-up

- 이벤트 회전/아카이브 정책 (예: 90일 이상은 파일로 dump)
- Risk 카운터 등 in-memory 상태를 이벤트에서 부트 시 복원
- 분산 환경 전환 시 PostgreSQL + outbox 패턴 검토 (ADR 신규 작성)
