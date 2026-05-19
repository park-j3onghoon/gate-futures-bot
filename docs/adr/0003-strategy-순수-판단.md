# ADR-0003 — Strategy는 순수 판단만 한다

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성)

## Context

전략(Strategy)을 작성할 때 자연스러운 형태는 "전략이 알아서 캔들도 조회하고 지표도 계산하고 판단까지" 다 하는 형태다.
하지만 이 구조는 다음을 어렵게 만든다:

1. **백테스팅**: 전략이 직접 거래소 API를 호출하면 과거 데이터 주입 불가
2. **단위 테스트**: 외부 호출 모킹이 필요 → 테스트 setup 비대해짐
3. **재사용**: 같은 전략을 다른 데이터 소스(실시간/캐시/저장된 데이터)에 적용 불가
4. **결정론**: 같은 입력에 항상 같은 결정을 내는지 검증 불가

## Decision

**Strategy 인터페이스는 `List<Candle>`을 파라미터로 받고, 그 외엔 어떤 외부도 호출하지 않는다.**

```kotlin
interface Strategy {
    fun shouldEnter(candles: List<Candle>): EntryDecision
    fun shouldExit(candles: List<Candle>, position: Position): ExitDecision
}
```

데이터 조회는 **워커(CoinWorker)** 의 책임이고, 전략은 그 입력에 대해 **순수 함수**처럼 동작한다.

## Alternatives Considered

- **Strategy가 ExchangePort를 주입받아 직접 조회**: 자유도 높음. → ❌ 백테스팅·결정론 깨짐.
- **Strategy가 캐시(IndicatorCache)를 직접 사용**: 성능 좋음. → ❌ 캐시 hit 여부에 따라 동작 달라질 수 있음. 캐시는 호출자(워커)가 관리.

## Consequences

### Positive
- 전략 단위 테스트가 데이터 준비만으로 충분 (모킹 불필요)
- 같은 전략을 라이브 / 백테스트 / 시뮬레이션에 그대로 재사용
- 결정 재현 가능 — 같은 캔들 시퀀스 → 같은 결정 (디버깅 단순)

### Negative
- 호출자가 캔들 데이터를 매번 준비해야 함 (인터페이스 표면이 약간 늘어남)
- 전략이 "n개 캔들이 필요한가"를 호출자에게 알릴 별도 메커니즘 필요 (예: `Strategy.requiredCandles`)

### Neutral
- EntryCondition과 ExitCondition은 필드가 같아도 **별도 클래스**로 분리 (의미 분리)

## Follow-up

- 백테스팅 모듈 도입 시 이 결정이 자연스럽게 활용됨
- 다중 타임프레임(1m + 5m + 1h)을 함께 보는 전략 도입 시 `Map<Timeframe, List<Candle>>` 형태로 확장 검토
