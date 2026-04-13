package com.parkj3onghoon.gatefuturesbot.exception

sealed class GateFuturesException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AuthenticationException(message: String) : GateFuturesException(message)

class InsufficientBalanceException(message: String) : GateFuturesException(message)

class OrderException(message: String, cause: Throwable? = null) : GateFuturesException(message, cause)

class RateLimitException(message: String) : GateFuturesException(message)

class PositionException(message: String) : GateFuturesException(message)
