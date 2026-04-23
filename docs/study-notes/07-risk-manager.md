# 공부 노트 07 — Risk Manager (게이트키퍼)

## 왜 필요한가
자동매매 봇의 가장 큰 위험: **전략 버그 + API 실패가 연쇄**되어 대량 손실.
실제 사고 사례:
- 무한 루프로 같은 포지션 수십 번 진입 시도 (일부 성공 → 의도치 않은 대량 포지션)
- 시장 급락 후에도 "RSI 과매도" 때문에 계속 long 진입 (손실 누적)
- 거래소 API 일시 장애를 주문 실패로 오인해 재시도 폭주

→ **중앙 게이트키퍼**로 진입 전 최종 점검. 비즈니스 로직이 아무리 뚫려도 여기서 차단.

## 3가지 체크
1. **동시 오픈 포지션 수**: 리스크 분산 실패 방지
2. **일일 손실 한도**: "오늘은 여기까지" — 자산 보호 핵심
3. **연속 실패 감지**: API 인증 만료 등으로 계속 실패시 cool-down

## 패턴: sealed class Decision
```kotlin
sealed class RiskDecision {
    data object Allow : RiskDecision()
    data class Deny(val reason: String) : RiskDecision()
}

when (val decision = riskManager.canOpenPosition(contract)) {
    RiskDecision.Allow -> trader.openLong(...)
    is RiskDecision.Deny -> logger.warn("진입 차단: ${decision.reason}")
}
```
→ `Boolean` 반환보다 **이유를 전달**할 수 있어 로그/알림에 바로 활용.

## Concurrency
여러 CoinWorker 코루틴이 동시에 `canOpenPosition`과 `onPositionOpened`를 호출. 그래서:
- `AtomicInteger`: openPositions 카운터
- `ConcurrentHashMap`: contract별 실패 카운터
- `AtomicReference<DailyPnl>`: 날짜 변경 시 원자적 리셋

**synchronized 블록을 쓰지 않는** 이유: `AtomicInteger.incrementAndGet()`처럼 간단한 연산은 Atomic으로 충분. 복잡한 "read-compute-write"만 `synchronized` 고려.

## Clock 주입 (테스트 가능성)
`LocalDate.now()`를 직접 쓰면 "날짜 변경시 리셋" 테스트가 불가.
→ `Clock` 주입:
```kotlin
class RiskManager(
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
)

private fun today(): LocalDate = LocalDate.now(clock)
```
테스트에서 `Clock.fixed(Instant.parse("2026-04-24T00:00:00Z"), UTC)`로 고정.

## CoinWorker 통합 (예정)
```kotlin
// worker/CoinWorker.kt의 evaluateAndEnter 시작에 추가 예정
private fun evaluateAndEnter() {
    when (val decision = riskManager.canOpenPosition(contract)) {
        is RiskDecision.Deny -> {
            logger.warn("진입 차단: ${decision.reason}")
            return
        }
        RiskDecision.Allow -> {}
    }
    // ... 기존 로직
}
```
+ 주문 성공시 `onPositionOpened`, 실패시 `onOrderFailure` 호출.

## Circuit Breaker 패턴과의 차이
- **Circuit Breaker**(Resilience4j): 외부 서비스 장애시 호출 차단 → 자동 복구 시도
- **Risk Manager**: 비즈니스 한도 기반 차단. 자동 복구 없음 (관리자 개입 or 자정 리셋)
- 둘은 **보완 관계**: Circuit Breaker가 인프라 레이어, Risk Manager가 비즈니스 레이어

## 대안 검토
| 접근 | 평가 |
|---|---|
| **Risk Manager** (채택) | 중앙 집중, 테스트 쉬움 |
| Aspect / @Around | 중앙 집중이지만 AOP 디버깅 어려움 |
| 각 Service에 분산 | 정책 변경시 여러 파일 수정 |
| DB/Redis 기반 | 분산 환경에선 필수. 단일 프로세스엔 과함 |

## 설정 예시
```yaml
risk:
  max-open-positions: 3
  daily-loss-limit-pct: 5.0
  consecutive-failure-threshold: 3
```
→ 보수적으로 시작해 운영 관찰 후 튜닝.

## 면접 포인트
- **"자동 매매에서 가장 중요한 건?"**: 수익 아니라 "장기 생존". Risk Manager가 생존 보장.
- **"어느 레벨에서 차단?"**: 가능한 한 **가장 바깥**. CoinWorker 진입 직전 1회 체크 → 모든 경로 커버.
- **"일일 손실 리셋 시점"**: 거래소 시간 기준? 한국 시간? Asia/Seoul 00:00 기준이 일반적. Clock으로 명시적 설정.
- **"해제 조건"**: 연속 실패 카운터는 성공 1회면 리셋. 일일 손실은 자정에 자연 리셋. 수동 reset API는 Kill Switch 기능에서 제공.
