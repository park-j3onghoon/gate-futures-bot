package com.parkj3onghoon.gatefuturesbot.ratelimit

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Component

/**
 * 단일 프로세스 Token Bucket 기반 RateLimiter.
 * 모든 워커(코루틴)가 공유한다.
 */
@Component
class InMemoryRateLimiter(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val refillPerSec: Double = DEFAULT_REFILL_PER_SEC,
    private val nowNanos: () -> Long = System::nanoTime
) : RateLimiter {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
        require(refillPerSec > 0) { "refillPerSec must be positive: $refillPerSec" }
    }

    private val mutex = Mutex()
    private var tokens: Double = capacity.toDouble()
    private var lastRefillNanos: Long = nowNanos()

    override suspend fun acquire() {
        while (true) {
            // null = 토큰 획득 성공, non-null = 다음 토큰까지 기다려야 할 밀리초
            val waitMillis: Long? = mutex.withLock {
                refill()
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    null
                } else {
                    val needed = 1.0 - tokens
                    (needed / refillPerSec * 1_000).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMillis == null) return
            delay(waitMillis)
        }
    }

    private fun refill() {
        val now = nowNanos()
        val elapsed = now - lastRefillNanos
        if (elapsed <= 0) return
        val refilled = elapsed.toDouble() / NANOS_PER_SEC * refillPerSec
        tokens = (tokens + refilled).coerceAtMost(capacity.toDouble())
        lastRefillNanos = now
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 20
        const val DEFAULT_REFILL_PER_SEC: Double = 5.0
        private const val NANOS_PER_SEC: Double = 1_000_000_000.0
    }
}
