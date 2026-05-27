package com.parkj3onghoon.gatefuturesbot.notification

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KakaoSelfChannel은 외부 HTTP 호출이 주 로직이라
 * 여기선 enabled 판정과 메시지 길이 제한 정도만 단위 테스트.
 * 통합 테스트는 MockWebServer로 가능하지만 이 단계에선 생략.
 */
class KakaoSelfChannelTest {
    @Test
    fun `disabled when token is blank`() {
        val channel = KakaoSelfChannel(accessToken = "", minPriority = NotificationPriority.NORMAL)
        assertFalse(channel.enabled)
    }

    @Test
    fun `enabled when token provided`() {
        val channel = KakaoSelfChannel(accessToken = "xxx", minPriority = NotificationPriority.NORMAL)
        assertTrue(channel.enabled)
    }

    @Test
    fun `respects configured minimum priority`() {
        val channel = KakaoSelfChannel(accessToken = "t", minPriority = NotificationPriority.HIGH)
        assertEquals(NotificationPriority.HIGH, channel.minimumPriority)
    }

    @Test
    fun `name is kakao-self`() {
        val channel = KakaoSelfChannel(accessToken = "t")
        assertEquals("kakao-self", channel.name)
    }

    @Test
    fun `MAX_TEXT_LENGTH is 200 chars (kakao limit)`() {
        assertEquals(200, KakaoSelfChannel.MAX_TEXT_LENGTH)
    }
}
