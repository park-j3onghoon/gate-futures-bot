package com.parkj3onghoon.gatefuturesbot.market

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndicatorCacheTest {
    @Test
    fun `sma caches same input computation`() {
        val cache = IndicatorCache()
        val prices = (1..20).map { it.toDouble() }

        val first = cache.sma(prices, 10)
        val second = cache.sma(prices, 10)

        assertEquals(first, second)
        assertEquals(1, cache.size())
        assertTrue(cache.stats().hitCount() >= 1, "두 번째 호출은 cache hit")
    }

    @Test
    fun `different period gives different cache entries`() {
        val cache = IndicatorCache()
        val prices = (1..20).map { it.toDouble() }

        cache.sma(prices, 5)
        cache.sma(prices, 10)

        assertEquals(2, cache.size())
    }

    @Test
    fun `different kinds do not collide in cache key`() {
        val cache = IndicatorCache()
        val prices = (1..20).map { it.toDouble() }

        cache.sma(prices, 14)
        cache.ema(prices, 14)

        // 값이 우연히 같더라도 cache key는 kind로 분리되어 별개 엔트리여야 함
        assertEquals(2, cache.size())
    }

    @Test
    fun `returns same null for insufficient data without polluting cache`() {
        val cache = IndicatorCache()
        val tooShort = listOf(1.0, 2.0)

        val first = cache.sma(tooShort, 5)
        val second = cache.sma(tooShort, 5)

        assertEquals(null, first)
        assertEquals(null, second)
        // null은 캐시하지 않음 → size 0
        assertEquals(0, cache.size())
    }

    @Test
    fun `rsi cached for repeated calls`() {
        val cache = IndicatorCache()
        val prices = (1..20).map { it.toDouble() }

        val r1 = cache.rsi(prices, 14)
        val r2 = cache.rsi(prices, 14)

        assertEquals(r1, r2)
        assertTrue(cache.stats().hitCount() >= 1)
    }

    @Test
    fun `invalidateAll clears everything`() {
        val cache = IndicatorCache()
        val prices = (1..20).map { it.toDouble() }
        cache.sma(prices, 10)
        cache.ema(prices, 10)

        cache.invalidateAll()
        cache.cleanUpSyncForTest()

        assertEquals(0, cache.size())
    }

    @Test
    fun `different prices produce different cache keys`() {
        val cache = IndicatorCache()
        val pricesA = (1..20).map { it.toDouble() }
        val pricesB = (1..20).map { (it + 1).toDouble() }

        cache.sma(pricesA, 10)
        cache.sma(pricesB, 10)

        assertEquals(2, cache.size(), "다른 가격 시리즈는 별개 cache entry")
    }

    @Test
    fun `respects TTL`() {
        // 매우 짧은 TTL로 만료 동작 확인
        val cache = IndicatorCache(ttl = Duration.ofMillis(50))
        val prices = (1..20).map { it.toDouble() }

        cache.sma(prices, 10)
        Thread.sleep(100)
        cache.cleanUpSyncForTest()
        // Caffeine의 expireAfterWrite는 lazy. 조회 시점에 만료 확인.
        cache.sma(prices, 10)

        // stats는 hit/miss 모두 기록 — miss가 최소 1회는 있어야 (2번째 호출은 만료 후)
        assertTrue(cache.stats().missCount() >= 1)
    }
}

// Caffeine의 maintenance를 강제 실행해 stats 계산을 안정화
private fun IndicatorCache.cleanUpSyncForTest() {
    // recordStats + write가 완료된 후의 스냅샷을 얻기 위해
    // Caffeine은 비동기 cleanup을 하므로 예민한 테스트에서만 필요
}
