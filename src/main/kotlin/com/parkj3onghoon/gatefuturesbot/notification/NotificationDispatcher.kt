package com.parkj3onghoon.gatefuturesbot.notification

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 여러 NotificationChannel에 동시에 발송.
 * - 채널별 minimumPriority로 필터
 * - 한 채널 장애가 다른 채널에 전파되지 않음 (supervisor + 예외 핸들러)
 * - fire-and-forget (봇 로직을 블록하지 않음)
 */
@Component
class NotificationDispatcher(
    private val channels: List<NotificationChannel>,
) {
    private val logger = LoggerFactory.getLogger(NotificationDispatcher::class.java)
    private val handler =
        CoroutineExceptionHandler { _, throwable ->
            logger.error("Notification 전송 실패 (channel scope)", throwable)
        }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)

    /** 이벤트를 해당 priority 이상을 허용한 활성 채널들로 fire-and-forget 발송. */
    fun dispatch(event: NotificationEvent) {
        val targets =
            channels.filter {
                it.enabled && it.minimumPriority.ordinal <= event.priority.ordinal
            }
        if (targets.isEmpty()) {
            logger.debug("알림 전송 대상 없음: priority={}", event.priority)
            return
        }
        targets.forEach { channel ->
            scope.launch {
                try {
                    channel.send(event)
                    logger.debug("알림 전송 성공: channel={}, priority={}", channel.name, event.priority)
                } catch (e: Exception) {
                    logger.warn("알림 전송 실패: channel={}, event={}, 원인={}", channel.name, event.title, e.message)
                }
            }
        }
    }

    /** 현재 활성 채널 요약 (부팅 배너용). */
    fun activeChannels(): List<String> = channels.filter { it.enabled }.map { it.name }
}
