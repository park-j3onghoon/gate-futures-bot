package com.parkj3onghoon.gatefuturesbot.notification

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class NotificationEventTest {
    @Test
    fun `format prefixes priority and includes title and message`() {
        val event =
            NotificationEvent(
                title = "주문 체결",
                message = "BTC_USDT long 1 at 50000",
                priority = NotificationPriority.HIGH,
            )
        val formatted = event.format()
        assertTrue(formatted.startsWith("[HIGH] 주문 체결"))
        assertTrue(formatted.contains("BTC_USDT long 1 at 50000"))
    }

    @Test
    fun `format includes tags as bullet list`() {
        val event =
            NotificationEvent(
                title = "t",
                message = "m",
                tags = mapOf("contract" to "BTC_USDT", "pnl" to "+5.2%"),
            )
        val formatted = event.format()
        assertTrue(formatted.contains("• contract: BTC_USDT"))
        assertTrue(formatted.contains("• pnl: +5.2%"))
    }

    @Test
    fun `format without tags omits bullet section`() {
        val event = NotificationEvent("t", "m")
        val formatted = event.format()
        assertTrue(!formatted.contains("•"))
    }
}
