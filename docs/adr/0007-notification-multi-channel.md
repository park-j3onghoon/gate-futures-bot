# ADR-0007 — Notification 다중 채널 아키텍처

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/04-telegram-notification.md, docs/study-notes/05-kakao-notification.md

## Context

봇 운영 중 알림이 필요한 시점:
- 주문 실패 / 청산 실패 (즉시 — CRITICAL)
- 일일 손실 한도 도달 (즉시 — CRITICAL)
- 포지션 오픈/클로즈 (참고 — INFO)
- 정상 동작 heartbeat (참고 — LOW)

채널마다 적합한 우선순위가 다르다. 카톡으로 INFO를 다 보내면 알림 피로, CRITICAL이 묻힌다.
또한 채널 하나(Slack 같은)에만 의존하면 그 채널이 죽으면 운영자가 모름.

## Decision

**`NotificationChannel` 인터페이스 + `NotificationDispatcher` 팬아웃 패턴**을 채택한다.

```kotlin
interface NotificationChannel {
    val name: String
    val enabled: Boolean
    val minimumPriority: NotificationPriority
    suspend fun send(event: NotificationEvent)
}
```

- 채널: `TelegramChannel`, `KakaoSelfChannel` (카카오 "나에게 보내기")
- `NotificationDispatcher`가 enabled 채널들에 fan-out, 각 채널이 `minimumPriority` 필터링
- 채널 실패는 다른 채널에 영향 안 줌 (`SupervisorJob` + `IO Dispatcher`)
- 비동기 fire-and-forget — 비즈니스 로직 블로킹 금지

## Alternatives Considered

- **Slack만**: 가장 간단. → ❌ 단일 채널 의존, 모바일 푸시 약함.
- **이메일**: 보편적. → ❌ 실시간성 약함, 운영자가 즉시 못 볼 수 있음.
- **SMS**: 즉각적. → ❌ 비용, API 복잡, 한국 환경에서 카톡 우위.
- **Spring `ApplicationEventPublisher`만 사용**: 내부 이벤트. → ❌ 외부 알림 채널 매핑은 별도 책임.

## Consequences

### Positive
- 채널 추가 비용 낮음 — 인터페이스 구현 + 설정만 추가
- 채널 하나 죽어도 다른 채널은 계속 동작
- 우선순위 필터링으로 알림 피로 제어
- 채널이 비활성(token 미설정)이면 자동 비활성화 — 환경별 설정 차이 흡수

### Negative
- 채널마다 API 형태가 달라 어댑터 코드 분산 (Telegram: JSON, Kakao: form-urlencoded + template_object JSON)
- 카카오 "나에게 보내기"는 토큰이 자주 만료됨 — refresh 로직 별도 필요
- fire-and-forget이라 전송 실패가 호출자에게 즉시 전달되지 않음 (로그/이벤트로 보완)

### Neutral
- 메시지 포맷은 채널별로 다르게 (예: Telegram은 Markdown, Kakao는 200자 truncation)
- WebClient(WebFlux) 의존성 추가

## Follow-up

- 채널별 전송 실패율을 EventStore에 기록해 관측
- 카카오 토큰 refresh 자동화
- Discord/Slack 채널 필요 시 동일 인터페이스로 추가
