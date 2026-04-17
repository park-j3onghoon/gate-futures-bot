package com.parkj3onghoon.gatefuturesbot.ratelimit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class InMemoryRateLimiterTest {

    @Test
    fun `acquire succeeds immediately when tokens available`() = runBlocking {
        val limiter = InMemoryRateLimiter(capacity = 5, refillPerSec = 100.0)
        val start = System.nanoTime()
        repeat(5) { limiter.acquire() }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs < 50, "expected fast acquires, got $elapsedMs ms")
    }

    @Test
    fun `acquire waits when tokens exhausted and refills over time`() = runBlocking {
        val limiter = InMemoryRateLimiter(capacity = 2, refillPerSec = 50.0)
        repeat(2) { limiter.acquire() }
        val start = System.nanoTime()
        limiter.acquire()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs in 10..200, "expected ~20ms wait, got $elapsedMs ms")
    }

    @Test
    fun `acquire uses injected clock to compute refill`() = runBlocking {
        var fakeNanos = 0L
        val limiter = InMemoryRateLimiter(
            capacity = 1,
            refillPerSec = 10.0,
            nowNanos = { fakeNanos }
        )
        limiter.acquire()
        fakeNanos += 200_000_000L
        val start = System.nanoTime()
        limiter.acquire()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs < 50, "refill via clock advance: $elapsedMs ms")
    }

    @Test
    fun `constructor rejects non-positive capacity`() {
        assertThrows<IllegalArgumentException> {
            InMemoryRateLimiter(capacity = 0, refillPerSec = 1.0)
        }
    }

    @Test
    fun `constructor rejects non-positive refillPerSec`() {
        assertThrows<IllegalArgumentException> {
            InMemoryRateLimiter(capacity = 10, refillPerSec = 0.0)
        }
    }
}
