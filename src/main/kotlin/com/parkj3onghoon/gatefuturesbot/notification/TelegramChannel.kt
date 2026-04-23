package com.parkj3onghoon.gatefuturesbot.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Telegram Bot API를 통한 알림 채널.
 *
 * 설정:
 * - notification.telegram.bot-token: BotFather에서 발급
 * - notification.telegram.chat-id: 봇에 /start 후 https://api.telegram.org/bot<TOKEN>/getUpdates 에서 확인
 * - notification.telegram.min-priority: 이 우선순위 이상만 발송 (기본 NORMAL)
 *
 * bot-token이 비어있으면 enabled=false로 동작 (no-op).
 */
@Component
class TelegramChannel(
    @Value("\${notification.telegram.bot-token:}") private val botToken: String,
    @Value("\${notification.telegram.chat-id:}") private val chatId: String,
    @Value("\${notification.telegram.min-priority:NORMAL}") private val minPriority: NotificationPriority,
    private val webClient: WebClient = defaultClient(),
) : NotificationChannel {
    private val logger = LoggerFactory.getLogger(TelegramChannel::class.java)

    override val name: String = "telegram"

    override val minimumPriority: NotificationPriority
        get() = minPriority

    override val enabled: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()

    override suspend fun send(event: NotificationEvent) {
        if (!enabled) {
            logger.debug("Telegram 비활성 (토큰 또는 chat-id 누락)")
            return
        }
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val body =
            mapOf(
                "chat_id" to chatId,
                "text" to event.format(),
                "parse_mode" to "HTML",
            )
        webClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .awaitBody<Map<String, Any>>()
    }

    companion object {
        private fun defaultClient(): WebClient = WebClient.builder().build()
    }
}
