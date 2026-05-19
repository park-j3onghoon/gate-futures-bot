# ADR-0008 — Hexagonal: ExchangePort 추출

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/06-hexagonal.md
- **연관**: ADR-0002 (SDK 격리는 전제 조건)

## Context

ADR-0002로 SDK를 `client/GateClient`에 격리했지만, `trading/FuturesTrader`는 여전히 `GateClient`를 **직접** 의존하고 있었다.

문제:
1. 거래소 추가/교체 시 trading 계층 수정 필요
2. 단위 테스트에서 `GateClient`를 mock하면 HTTP/SDK 호출 시뮬레이션이 너무 디테일
3. "비즈니스가 인프라에 의존" — Dependency Inversion 원칙 위배

## Decision

**Hexagonal Architecture(Ports & Adapters)** 의 Port 패턴을 도입한다.

- `trading/ExchangePort` 인터페이스 — 도메인이 거래소에 요구하는 능력 정의
  ```kotlin
  interface ExchangePort {
      suspend fun createOrder(contract: String, size: Int, leverage: Int): OrderResult
      suspend fun closePosition(contract: String): OrderResult
      suspend fun getPosition(contract: String): Position?
      suspend fun getCandles(contract: String, interval: String, limit: Int): List<Candle>
      suspend fun getLatestPrice(contract: String): Double
  }
  ```
- `client/GateExchangeAdapter` — `ExchangePort` 구현, 내부적으로 `GateClient` 호출
- `FuturesTrader`는 `ExchangePort`만 의존 (구체 어댑터 모름)

## Alternatives Considered

- **GateClient를 그대로 사용**: 변경 비용 0. → ❌ 거래소 추가 시 도메인 계층 광범위 수정.
- **Adapter만 두고 Port 인터페이스는 없이**: 단순. → ❌ 컴파일 타임 격리 불가, 테스트에서 mock 어려움.
- **추상 클래스 사용**: → ❌ Kotlin에선 interface가 더 자연스러움, 다중 구현도 가능.

## Consequences

### Positive
- 거래소 추가 시 새 Adapter만 작성 (`BinanceExchangeAdapter` 등) — 도메인 수정 0
- 단위 테스트에서 `ExchangePort` mock이 간단 (5개 메서드)
- ArchUnit 규칙 강화 가능: "trading은 client/Adapter 구체 클래스 의존 금지"

### Negative
- 한 단계 인디렉션 추가 — 코드 추적 시 "Port → Adapter → SDK" 3단계
- Port 인터페이스 설계가 일찍 굳어지면 새 거래소 추가 시 어색할 수 있음 (예: Gate.io 특화 파라미터)

### Neutral
- `leverage`와 `createOrder`를 한 메서드에 결합 (Gate.io는 별도 API지만 도메인은 "주문 = 레버리지 적용 + 주문"으로 추상화)

## Follow-up

- 두 번째 거래소 Adapter 도입 시 Port 인터페이스 재검토 — 거래소별 특화 옵션 처리 방식 결정
- 백테스팅용 `FakeExchangeAdapter` 작성 시 이 결정이 즉시 활용됨
