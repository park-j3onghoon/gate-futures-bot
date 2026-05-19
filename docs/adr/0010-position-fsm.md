# ADR-0010 — Position State Machine (FSM)

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/08-position-fsm.md

## Context

포지션 라이프사이클은 단순한 boolean(`isOpen`)으로 다룰 수 없다.
실제로 발생한 사건들:

- 주문 요청 보냈는데 응답 안 옴 → 봇은 "오픈 안 됨"으로 알고 재시도 → 실제론 두 번 오픈
- 청산 요청 후 응답 오기 전 봇 재시작 → 청산됐는지 알 수 없음
- 거래소엔 포지션이 있는데 봇은 IDLE로 알고 새 진입 시도 ("유령 포지션")

상태 전환 규칙이 명시적이지 않으면 위 같은 race가 결과적으로 자금 손실로 이어진다.

## Decision

**유한 상태 기계(FSM)** 를 명시적으로 도입한다.

```
IDLE → OPENING → OPEN → CLOSING → CLOSED
                ↓        ↓
              FAILED   FAILED
```

- `PositionState` enum: `IDLE`, `OPENING`, `OPEN`, `CLOSING`, `CLOSED`, `FAILED`
- 전환 규칙은 enum에 메서드로: `canOpen()`, `canClose()`, `isTerminal()`
- `PositionTracker`는 `ConcurrentHashMap.compute`로 **원자적 전이** — race 차단
- `reconcile()` 메서드: 거래소 실제 포지션과 대조해 유령 감지
- `Clock` 주입으로 "OPENING 상태로 N분 이상 머물면 stale" 검출

## Alternatives Considered

- **boolean isOpen**: 단순. → ❌ 중간 상태(OPENING/CLOSING) 표현 불가.
- **Spring StateMachine 라이브러리**: 풍부한 기능. → ❌ 도메인 상태 수가 작아 과함, learning curve.
- **DB 트랜잭션 격리에 의존**: → ❌ 거래소 API 호출은 DB와 분리됨, 트랜잭션 경계 외부.

## Consequences

### Positive
- 잘못된 전환은 `IllegalStateException` 또는 noop으로 막힘 — 데이터 깨짐 방지
- 유령 포지션 검출 가능 (거래소 vs 트래커 dual-source-of-truth 대조)
- 운영자가 수동 리셋 가능 (`POST /admin/positions/{contract}/reset` — ADR-0012)
- 상태 전이를 이벤트로 기록 → 사후 분석 가능 (ADR-0011)

### Negative
- 상태가 늘면(FAILED, RECONCILING 등) 코드 분기 증가
- ConcurrentHashMap.compute 안에서 무거운 로직 금지 — 락 보유 시간 주의

### Neutral
- in-memory 추적, 프로세스 재시작 시 상태는 거래소 조회로 복원 (`reconcile()`)

## Follow-up

- 재시작 후 자동 reconcile (현재는 수동/주기적) — booting 시 모든 contract reconcile
- FAILED 상태에서 자동 재시도 정책 (백오프) 도입 검토
