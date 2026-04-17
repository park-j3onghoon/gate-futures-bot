package com.parkj3onghoon.gatefuturesbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bot")
data class BotProperties(
    val contracts: List<String> = listOf("BTC_USDT"),
    val leverage: Int = 5,
    val orderSize: Int = 1,
    val checkIntervalSec: Long = 60
)
