# ADR-0006 — Caffeine 기반 지표 캐시

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/03-caffeine-cache.md

## Context

지표 계산(SMA, EMA, RSI 등)이 비싸다. 한 iteration에서 같은 캔들 리스트로 여러 지표를 계산하거나,
짧은 시간 안에 같은 캔들이 재사용되는 경우가 많다 (REST polling 주기 < 캔들 봉 길이).

처음엔 캐시 없이 매번 계산 → CPU 사용량과 응답 시간 모두 손해.

## Decision

**Caffeine 3.1.8** 기반 in-memory 캐시를 `IndicatorCache`로 래핑한다.

- Key: `(kind, period, candles.size, candles.first.t, candles.last.t)` — 캔들 시퀀스의 핑거프린트
- TTL: 5초 (캔들 봉 길이보다 짧게)
- `recordStats()` 활성화 → hit rate 관측
- 지표별 헬퍼: `sma(...)`, `ema(...)`, `rsi(...)`

## Alternatives Considered

- **Guava Cache**: 동일 API군. → ❌ Caffeine이 W-TinyLFU 알고리즘으로 더 높은 hit rate, Guava 후속작.
- **수동 HashMap**: 단순. → ❌ TTL/eviction/통계 부재.
- **Redis**: 분산 환경 대비. → ❌ 현재 단일 프로세스, 네트워크 오버헤드만 추가.

## Consequences

### Positive
- 같은 캔들 + 같은 파라미터에 대해 재계산 회피
- Hit rate를 metrics로 노출 가능 (이후 Micrometer 연동 시)
- W-TinyLFU 정책이 짧은 burst와 장기 접근 패턴 모두 잘 처리

### Negative
- 캔들 마지막 시각이 1ms라도 다르면 cache miss — 정확도 보장의 비용
- 메모리 footprint: 캐시 사이즈가 늘면 GC 영향
- Key 설계 실수 시 stale 데이터 위험 (현재는 timestamp가 key에 포함되어 안전)

### Neutral
- Spring Cache 추상화는 사용하지 않음 — Caffeine을 직접 래핑하여 도메인 메서드 명명

## Follow-up

- 분산 멀티 인스턴스 시 Redis Cache로 전환 검토 (ADR 신규 작성)
- Hit rate 모니터링하여 TTL/사이즈 튜닝
