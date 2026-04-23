# 공부 노트 08 — Position State Machine

## 왜 FSM인가
이전에는 "포지션이 있냐/없냐" 이분법. 하지만 실전엔 **"주문은 넣었는데 응답이 오기 전"**, **"청산 요청 후 체결 대기"** 같은 중간 상태가 있다. 이걸 무시하면:
- 같은 포지션 2번 오픈 시도 (OPENING 중인데 또 open)
- 외부에서 수동 청산된 걸 감지 못함 (유령 상태)
- 주문 실패인지 성공 후 늦은 응답인지 구분 불가

→ **상태 머신(FSM)**이 자연스러운 해법.

## 상태 다이어그램
```
IDLE ──▶ OPENING ──▶ OPEN ──▶ CLOSING ──▶ CLOSED
            │                   │
            ▼                   ▼
         FAILED              FAILED
            │
            ▼
          IDLE (수동 리셋)
```

## 상태별 의미
| 상태 | 의미 | 전이 허용 |
|---|---|---|
| IDLE | 포지션 없음, 진입 대기 | → OPENING |
| OPENING | 주문 전송 완료, 체결 대기 | → OPEN, FAILED |
| OPEN | 포지션 보유 중 | → CLOSING, CLOSED (외부 청산) |
| CLOSING | 청산 주문 전송, 체결 대기 | → CLOSED, FAILED |
| CLOSED | 완전 청산 완료 | → OPENING (재진입) |
| FAILED | 치명적 실패 | → IDLE (수동 리셋) |

## 유령 포지션(Ghost) 감지
`reconcile(contract, exchangeHasPosition)` 메서드:
- **OPEN인데 거래소에 없음**: 외부(웹/모바일)에서 청산했거나 강제 청산. CLOSED로 전환.
- **OPENING 30초+ 후에도 거래소에 없음**: 주문 실패로 판단. FAILED로.

## transition() 구현 포인트
```kotlin
states.compute(contract) { _, existing ->
    val from = existing?.state ?: IDLE
    require(isValid(from, to)) { "invalid transition: $from -> $to" }
    PositionRecord(to, now, reason)
}
```
- `compute`: ConcurrentHashMap의 **원자적 read-modify-write**
- `require`: 유효하지 않은 전이는 예외 (조기 실패)
- `reason`: 전이 이유 기록 (디버깅)

## FSM 구현 방식 비교
| 방식 | 장점 | 단점 |
|---|---|---|
| **enum + transition 테이블** (채택) | 단순, 컴파일 타임 exhaustive | 전이 로직이 한 곳에 |
| sealed class + subtype별 state | 각 상태 고유 데이터 보유 | 복잡, Kotlin 관용 |
| 라이브러리(Spring Statemachine, Tinder) | 정교한 기능 | 러닝커브, 과함 |

이 규모엔 enum이 충분.

## 실전 활용 (CoinWorker 통합 예정)
```kotlin
// Before 진입
if (!positionTracker.stateOf(contract).canOpen()) {
    return  // 이미 OPENING/OPEN
}
positionTracker.transition(contract, OPENING)
try {
    trader.openLong(...)
    positionTracker.transition(contract, OPEN, "주문 체결")
} catch (e: Exception) {
    positionTracker.transition(contract, FAILED, e.message ?: "")
}

// 매 iteration마다 reconcile
val realPosition = exchange.getPosition(contract)
positionTracker.reconcile(contract, exchangeHasPosition = realPosition != null)
```

## 왜 Clock 주입인가
`reconcile`의 "30초 타임아웃"을 테스트하려면 시간을 조작해야 함.
`Clock.fixed(Instant)`로 정확한 제어.

## 면접 포인트
- **"왜 FSM?"**: 비동기 시스템에서 "요청 보냈지만 결과 모름" 상태를 명시. 이분법은 실전에서 버그 온상.
- **"reconcile 주기"**: 매 iteration마다 체크. 무거우면 N iteration당 1회로 throttle.
- **"외부 청산 감지"**: 사용자가 수동 청산 or 거래소 강제 청산(margin call) 시 상태 동기화. 안 하면 다음 iteration에서 "이미 포지션 있네" 판단 오류.
- **"Event Sourcing과 관계"**: 상태 전이 로그가 그대로 이벤트 스트림 — 다음 기능(Event Store)에서 활용.
