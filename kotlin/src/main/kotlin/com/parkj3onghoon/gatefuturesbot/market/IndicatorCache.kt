package com.parkj3onghoon.gatefuturesbot.market

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 같은 (지표, period, prices) 입력에 대해 계산 결과를 메모이즈한다.
 *
 * 지표 계산은 O(N) 순회지만 워커가 매 iteration마다 같은 candleCache로 SMA/EMA/RSI를 여러 조건에 대해
 * 반복 호출할 수 있다(다중 EntryCondition + 다중 ExitCondition + 다중 지표). 캐시로 중복 계산 제거.
 *
 * - Cache key: (kind, period, prices의 hashCode + size + 마지막 timestamp 대체로 마지막 원소)
 * - TTL 짧게(기본 5초)해 prices가 업데이트되면 이전 결과를 빠르게 버림
 * - Caffeine은 thread-safe + 높은 hit rate 알고리즘(W-TinyLFU)
 */
class IndicatorCache(
    maxSize: Long = DEFAULT_MAX_SIZE,
    ttl: Duration = DEFAULT_TTL,
) {
    private val cache: Cache<Key, Double> =
        Caffeine
            .newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
            .recordStats()
            .build()

    /**
     * compute가 null을 반환할 수도 있어 Caffeine의 null 제약을 우회하기 위해 Optional 패턴 대신
     * 음수 센티넬을 쓰지 않고 "있으면 return, 없으면 계산 후 put"의 단순 패턴 사용.
     */
    fun getOrCompute(
        kind: IndicatorKind,
        period: Int,
        prices: List<Double>,
        compute: () -> Double?,
    ): Double? {
        if (prices.isEmpty()) return compute()
        val key = Key(kind, period, prices.size, prices.last(), prices.first())
        val cached = cache.getIfPresent(key)
        if (cached != null) return cached
        val computed = compute() ?: return null
        cache.put(key, computed)
        return computed
    }

    fun sma(
        prices: List<Double>,
        period: Int,
    ): Double? = getOrCompute(IndicatorKind.SMA, period, prices) { calculateSma(prices, period) }

    fun ema(
        prices: List<Double>,
        period: Int,
    ): Double? = getOrCompute(IndicatorKind.EMA, period, prices) { calculateEma(prices, period) }

    fun rsi(
        prices: List<Double>,
        period: Int,
    ): Double? = getOrCompute(IndicatorKind.RSI, period, prices) { calculateRsi(prices, period) }

    fun stats() = cache.stats()

    fun size() = cache.estimatedSize()

    fun invalidateAll() = cache.invalidateAll()

    data class Key(
        val kind: IndicatorKind,
        val period: Int,
        val size: Int,
        val last: Double,
        val first: Double,
    )

    enum class IndicatorKind { SMA, EMA, RSI }

    companion object {
        const val DEFAULT_MAX_SIZE: Long = 1_000
        val DEFAULT_TTL: Duration = Duration.ofSeconds(5)
    }
}
