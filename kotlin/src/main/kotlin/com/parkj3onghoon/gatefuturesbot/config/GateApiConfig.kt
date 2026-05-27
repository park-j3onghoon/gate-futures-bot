package com.parkj3onghoon.gatefuturesbot.config

import io.gate.gateapi.ApiClient
import io.gate.gateapi.api.FuturesApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GateApiConfig {
    @Bean
    fun futuresApi(apiProperties: ApiProperties): FuturesApi {
        val client = ApiClient()
        client.setBasePath(apiProperties.host)
        client.setApiKeySecret(apiProperties.key, apiProperties.secret)
        return FuturesApi(client)
    }
}
