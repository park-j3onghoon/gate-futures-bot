package com.parkj3onghoon.gatefuturesbot.ratelimit

interface RateLimiter {
    suspend fun acquire()
}
