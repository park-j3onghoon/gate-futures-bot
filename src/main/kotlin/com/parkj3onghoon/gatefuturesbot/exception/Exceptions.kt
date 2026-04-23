package com.parkj3onghoon.gatefuturesbot.exception

sealed class GateFuturesException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)

class InsufficientBalanceException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)

class OrderException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)

class RateLimitException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)

class PositionException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)

class MarketDataException(
    message: String,
    cause: Throwable? = null,
) : GateFuturesException(message, cause)
