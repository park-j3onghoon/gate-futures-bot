package com.parkj3onghoon.gatefuturesbot.model

enum class Interval(
    val code: String,
) {
    SEC_10("10s"),
    MIN_1("1m"),
    MIN_5("5m"),
    MIN_15("15m"),
    MIN_30("30m"),
    HOUR_1("1h"),
    HOUR_4("4h"),
    HOUR_8("8h"),
    DAY_1("1d"),
    DAY_7("7d"),
    DAY_30("30d"),
}
