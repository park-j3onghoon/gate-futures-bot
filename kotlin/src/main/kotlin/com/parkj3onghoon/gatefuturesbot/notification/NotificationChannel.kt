package com.parkj3onghoon.gatefuturesbot.notification

import java.time.Instant

/**
 * 알림 채널 추상화 (Telegram, Kakao, SMS, Email 등).
 * 각 구현체는 자신이 지원하는 최소 priority를 반환 → Dispatcher가 필터링.
 */
interface NotificationChannel {
    /** 이 채널이 수신하는 최소 우선순위. 이보다 낮은 이벤트는 skip. */
    val minimumPriority: NotificationPriority

    /** 이 채널이 현재 활성화되어 있는지 (설정값 누락시 false). */
    val enabled: Boolean

    /** 채널 식별자 (로그용). */
    val name: String

    /** 알림 전송. 예외는 상위가 알림 실패로 처리. */
    suspend fun send(event: NotificationEvent)
}

enum class NotificationPriority { LOW, NORMAL, HIGH, CRITICAL }

/**
 * 알림 이벤트. 도메인 이벤트와 구분하기 위해 "Notification" 접두어.
 * 비즈니스 도메인 이벤트(OrderCreated 등)가 이것으로 변환되어 채널에 전달됨.
 */
data class NotificationEvent(
    val title: String,
    val message: String,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val tags: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now(),
) {
    fun format(): String =
        buildString {
            append("[")
            append(priority.name)
            append("] ")
            append(title)
            append("\n")
            append(message)
            if (tags.isNotEmpty()) {
                append("\n")
                tags.forEach { (k, v) -> append("• $k: $v\n") }
            }
        }
}
