package com.parkj3onghoon.gatefuturesbot

import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.config.BotProperties
import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import com.parkj3onghoon.gatefuturesbot.risk.RiskProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@SpringBootApplication
@EnableConfigurationProperties(
    ApiProperties::class,
    BotProperties::class,
    StrategyProperties::class,
    RiskProperties::class,
)
@EnableJdbcRepositories
class GateFuturesBotApplication

fun main(args: Array<String>) {
    runApplication<GateFuturesBotApplication>(*args)
}
