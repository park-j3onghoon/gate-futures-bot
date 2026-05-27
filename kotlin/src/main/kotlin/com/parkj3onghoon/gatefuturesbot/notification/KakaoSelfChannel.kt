package com.parkj3onghoon.gatefuturesbot.notification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * 카카오톡 "나에게 보내기" 채널.
 *
 * 카카오 개발자 등록 → 앱 생성 → "플랫폼: Web 도메인" 추가 → "카카오 로그인" 활성화
 * → 동의항목에서 "카카오톡 메시지 전송" 추가 → OAuth 로그인으로 access token 발급
 *
 * 간편성을 위해 access-token을 설정에 직접 주입 (장기 토큰 or 수동 갱신).
 * 운영 시엔 refresh-token으로 자동 갱신하는 KakaoTokenManager를 두는 편이 안전.
 *
 * 설정:
 * - notification.kakao.access-token: OAuth로 발급한 access token
 * - notification.kakao.min-priority: 기본 NORMAL
 *
 * 주의: 이 API는 "본인 계정에만" 전송 가능. 친구/타인에겐 별도 API 필요.
 */
@Component
class KakaoSelfChannel(
    @Value("\${notification.kakao.access-token:}") private val accessToken: String,
    @Value("\${notification.kakao.min-priority:NORMAL}") private val minPriority: NotificationPriority = NotificationPriority.NORMAL,
    @Value("\${notification.kakao.link-url:https://gate.io}") private val linkUrl: String = "https://gate.io",
    private val webClient: WebClient = WebClient.builder().build(),
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper = jacksonObjectMapper(),
) : NotificationChannel {
    private val logger = LoggerFactory.getLogger(KakaoSelfChannel::class.java)

    override val name: String = "kakao-self"

    override val minimumPriority: NotificationPriority
        get() = minPriority

    override val enabled: Boolean
        get() = accessToken.isNotBlank()

    /**
     * 카카오 API는 form-urlencoded + 'template_object'라는 JSON 문자열을 form 필드로 받음.
     * 템플릿 타입: "text" 선택 (가장 단순)
     */
    override suspend fun send(event: NotificationEvent) {
        if (!enabled) {
            logger.debug("Kakao 비활성 (access-token 누락)")
            return
        }
        val templateObject =
            mapOf(
                "object_type" to "text",
                "text" to event.format().take(MAX_TEXT_LENGTH),
                "link" to mapOf("web_url" to linkUrl, "mobile_web_url" to linkUrl),
                "button_title" to "확인",
            )
        val form = LinkedMultiValueMap<String, String>()
        form.add("template_object", objectMapper.writeValueAsString(templateObject))

        webClient
            .post()
            .uri("https://kapi.kakao.com/v2/api/talk/memo/default/send")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(form))
            .retrieve()
            .awaitBody<Map<String, Any>>()
    }

    companion object {
        // 카카오 text 템플릿 제한: 200자
        const val MAX_TEXT_LENGTH: Int = 200
    }
}
