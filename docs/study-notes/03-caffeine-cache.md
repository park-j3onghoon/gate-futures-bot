# 공부 노트 03 — Caffeine 지표 계산 캐시

## 왜 필요한가
워커가 매 iteration마다 같은 candleCache로 여러 지표/조건을 평가. 같은 `(kind, period, prices)` 입력이 반복되면 계산 낭비.
특히 전략이 성장해 "long: RSI(14) < 30, short: RSI(14) > 70"처럼 <strong>같은 (RSI, 14)</strong>를 두 번 계산하는 경우 캐시 hit.

## Caffeine 선택 근거
| 라이브러리 | 특징 |
|---|---|
| **Caffeine** | Google Guava Cache의 후속. W-TinyLFU 알고리즘으로 hit rate 높음. Spring 기본 |
| Guava Cache | Caffeine이 deprecate화 |
| ConcurrentHashMap | Eviction/만료 직접 구현 필요 |
| Redis | 분산 캐시. 프로세스 간 공유가 필요할 때 (현재 단일 프로세스) |

**선택**: Caffeine — in-memory + 단일 프로세스 + ms 지연 요구 → 정답.

## 핵심 구현
```kotlin
class IndicatorCache(
    maxSize: Long = 1_000,
    ttl: Duration = Duration.ofSeconds(5),
) {
    private val cache: Cache<Key, Double> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
        .recordStats()
        .build()

    fun sma(prices: List<Double>, period: Int): Double? =
        getOrCompute(SMA, period, prices) { calculateSma(prices, period) }
}
```

## Cache Key 설계 (가장 중요한 결정)
후보들:
1. **`prices.hashCode()`** — O(N). 클 때 부담
2. **`(size, first, last)` 조합** — O(1), 선택
3. **candle timestamps의 마지막** — 시계열 전제

선택: **(kind, period, size, first, last)** — 일반적으로 prices가 뒤에만 append되므로 `last`만 다르면 충분. `first`도 포함해 "슬라이딩 윈도우로 인해 앞이 변한 경우"도 감지.

## TTL vs Invalidation
- TTL 5초: `updateCandles()`가 새 캔들을 추가하면 5초 이내에 캐시 자연 소멸
- 명시적 invalidate 필요하면 `cache.invalidateAll()` (이벤트 기반)
- 이 프로젝트엔 write가 1분 간격이라 5초 TTL이 보수적

## null 처리 함정
Caffeine은 **null을 캐시하지 않음**(정책).
```kotlin
// ❌ Caffeine.get(key, loader): loader가 null 반환시 예외
// ✅ 직접 체크
val cached = cache.getIfPresent(key)
if (cached != null) return cached
val computed = compute() ?: return null
cache.put(key, computed)
```

## Hit Rate 측정
```kotlin
cache.recordStats()   // 활성화
cache.stats().hitRate()  // 0.0 ~ 1.0
cache.stats().hitCount() / missCount() / evictionCount()
```
Actuator + Micrometer 연동시 `/actuator/metrics/cache.gets` 자동 노출.

## 안티패턴
1. **너무 긴 TTL**: stale 데이터로 잘못된 판단
2. **너무 많은 maxSize**: OOM
3. **복잡한 cache key**: hashCode 계산 자체가 병목
4. **Mutable Object을 key로**: equals/hashCode 변하면 get/put 깨짐

## 면접 포인트
- "Python의 `functools.lru_cache`의 분산/운영 친화 버전"
- "W-TinyLFU로 `LRU + LFU`의 좋은 점 결합"
- "`recordStats()`로 hit rate 모니터링 → TTL/size 튜닝"
- "Spring Cache 추상화(@Cacheable) vs 직접 Caffeine 주입: 전자는 declarative하지만 method 경계만 캐시 가능. 지표처럼 내부 함수 결과를 캐시하려면 직접 주입이 더 유연"
