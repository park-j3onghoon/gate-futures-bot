# ADR-0009 — Risk Manager 게이트키퍼 패턴

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/07-risk-manager.md

## Context

자동매매 봇은 **잘못된 상황에서 멈출 줄 알아야** 한다. 시나리오:
- 너무 많은 포지션을 동시에 (n개 코인 봇이 일시에 다 진입)
- 일일 누적 손실이 한도 초과 → 더 잃기 전에 중단
- 주문 실패가 연속 발생 → API 장애 가능성, 추가 시도 위험

이 검증을 전략(Strategy)이나 워커(CoinWorker) 안에 박으면 **분산되고 누락**된다.
새 전략을 추가할 때마다 같은 가드를 다시 작성해야 한다.

## Decision

**`RiskManager`를 진입 시점의 게이트키퍼**로 도입한다.

```kotlin
fun canOpenPosition(contract: String): RiskDecision  // Allow | Deny(reason)
```

3가지 체크:
1. **Max open positions**: 동시 오픈 포지션 수 한도
2. **Daily loss limit**: 오늘 누적 손실액 한도
3. **Consecutive failures**: 연속 주문 실패 카운트 한도

상태: `AtomicInteger` 카운터, `AtomicReference` 누적 손실, `Clock` 주입(테스트 가능).

워커는 진입 직전에 `riskManager.canOpenPosition()`을 호출하고 Deny면 진입 skip + 이벤트 기록.

## Alternatives Considered

- **전략 내부에서 가드**: 전략별로 작성. → ❌ 중복, 누락, 일관성 결여.
- **AOP(Aspect)로 진입 메서드 인터셉트**: 깔끔해 보임. → ❌ 디버깅 어렵고 정책이 코드와 분리됨.
- **별도 Sidecar 프로세스**: 거시적 제어. → ❌ 단일 프로세스 봇엔 과함.

## Consequences

### Positive
- 모든 신규 진입이 같은 게이트를 통과 — 정책 일관성
- 정책 변경이 한 곳(RiskManager + RiskProperties)에서 가능
- `RiskDecision`이 사유를 포함 → 이벤트/알림에 그대로 노출 가능
- 테스트: Clock 주입으로 "내일이 되면 손실 카운터 리셋" 같은 시간 의존 로직 검증 용이

### Negative
- 게이트가 너무 보수적이면 정상 진입까지 막힘 — RiskProperties 튜닝 필요
- 카운터/누적 손실은 in-memory → 프로세스 재시작 시 손실 카운터 초기화 (의도된 동작이지만 명시 필요)

### Neutral
- Risk 통계는 `/admin/status`로 노출 — 운영자가 가시화

## Follow-up

- 프로세스 재시작 후에도 일일 손실을 유지하려면 EventStore에서 복원 로직 추가 (ADR 신규 작성)
- 코인별 손실 한도 (전역이 아닌) 도입 검토
