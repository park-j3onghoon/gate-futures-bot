package com.parkj3onghoon.gatefuturesbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gate.api")
data class ApiProperties(
    val key: String = "",
    val secret: String = "",
    val host: String = "https://api.gateio.ws/api/v4",
    val settle: String = "usdt"
) {
    val isTestnet: Boolean get() = host.contains("testnet")
}
