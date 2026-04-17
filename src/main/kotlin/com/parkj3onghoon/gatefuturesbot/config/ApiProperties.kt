package com.parkj3onghoon.gatefuturesbot.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "gate.api")
data class ApiProperties(
    val key: String = "",
    val secret: String = "",
    @field:NotBlank val host: String = "https://api.gateio.ws/api/v4",
    @field:NotBlank val settle: String = "usdt"
) {
    val isTestnet: Boolean get() = host.contains("testnet")

    /** 공식 메인넷 host에 실제 API key가 바인딩된 상태를 의미한다. 실거래 경보용. */
    val isProdWithKey: Boolean get() = !isTestnet && key.isNotBlank() && secret.isNotBlank()
}
