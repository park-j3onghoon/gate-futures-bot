package com.parkj3onghoon.gatefuturesbot.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest
class PropertiesTest {
    @Autowired
    lateinit var apiProperties: ApiProperties

    @Autowired
    lateinit var botProperties: BotProperties

    @Test
    fun `should load api properties from application yml`() {
        assertEquals("https://api.gateio.ws/api/v4", apiProperties.host)
        assertEquals("usdt", apiProperties.settle)
    }

    @Test
    fun `should detect testnet from host url`() {
        assertFalse(apiProperties.isTestnet)

        val testnetProps = ApiProperties(host = "https://fx-api-testnet.gateio.ws/api/v4")
        assert(testnetProps.isTestnet)
    }

    @Test
    fun `should load bot properties from application yml`() {
        assertEquals(listOf("BTC_USDT"), botProperties.contracts)
        assertEquals(5, botProperties.leverage)
        assertEquals(1, botProperties.orderSize)
        assertEquals(60L, botProperties.checkIntervalSec)
    }
}
