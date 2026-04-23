package com.parkj3onghoon.gatefuturesbot.config

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "bot")
data class BotProperties(
    @field:NotEmpty val contracts: List<String> = listOf("BTC_USDT"),
    @field:Positive val leverage: Int = 5,
    @field:Positive val orderSize: Int = 1,
    @field:Positive val checkIntervalSec: Long = 60,
)
