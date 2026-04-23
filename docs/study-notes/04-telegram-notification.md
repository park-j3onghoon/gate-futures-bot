# 공부 노트 04 — 알림 채널 추상화 + Telegram

## 아키텍처 결정
`NotificationChannel` **인터페이스** + `NotificationDispatcher` **팬아웃**.
Spring이 `List<NotificationChannel>` 주입 → **새 채널 추가는 클래스 하나 새로 쓰면 끝** (Open/Closed).

```
                   ┌─> TelegramChannel
                   ├─> KakaoChannel
NotificationDispatcher
  (priority 필터) ─┼─> SmsChannel
                   └─> DiscordChannel
```

## 핵심 설계

### 1. Priority 필터링
```kotlin
enum class NotificationPriority { LOW, NORMAL, HIGH, CRITICAL }
// 각 채널이 자신의 minimumPriority를 선언
// Dispatcher는 event.priority >= channel.minimumPriority인 채널에만 발송
```
→ CRITICAL만 SMS, 일반은 Telegram 같은 조합 가능.

### 2. 활성/비활성 동적 제어
```kotlin
override val enabled: Boolean
    get() = botToken.isNotBlank() && chatId.isNotBlank()
```
환경변수가 비어있으면 자동 disable → **부분 설정으로 부팅 가능**.

### 3. Fire-and-Forget + 격리
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
// 한 채널 장애가 다른 채널/봇 로직에 전파되지 않음
// SupervisorJob: 자식 실패가 형제에 전파 X
// CoroutineExceptionHandler: 최종 에러 포착
```

## Telegram Bot 설정법
1. **BotFather**(<https://t.me/botfather>)에서 `/newbot` → 이름 + username → **token** 수령
2. 생성한 봇에 메시지 1회 발송 (봇 활성화)
3. `https://api.telegram.org/bot<TOKEN>/getUpdates` 호출 → `result[].message.chat.id` 확인
4. application.yml:
   ```yaml
   notification:
     telegram:
       bot-token: ${TELEGRAM_BOT_TOKEN:}
       chat-id: ${TELEGRAM_CHAT_ID:}
       min-priority: NORMAL
   ```

## WebClient 선택 이유
| 대안 | 평가 |
|---|---|
| **WebClient** | non-blocking + 코루틴 `awaitBody` 통합, Spring Boot 기본 |
| RestTemplate | deprecated (Spring 6에서 유지보수 모드) |
| Ktor Client | Kotlin-first지만 Spring 의존 적음 |
| OkHttp | 로우레벨 수동 |

→ Spring Boot 환경에선 **WebClient가 사실상 기본**.

## HTTP 에러 처리
현재는 실패시 예외 throw → Dispatcher가 `catch (e: Exception)` → warn 로그.
실전에선 **Resilience4j Circuit Breaker** 추가 고려 (Telegram API 장애시 즉시 포기).

## 테스트 전략
- **WebClient mocking**은 번거로워 (`WebClient.Builder`) — 실제 HTTP 호출 테스트는 `MockWebServer` 추천
- 이번엔 **NotificationDispatcher 자체** 로직에 집중 (채널 구현체는 mockk로 fake)
- 5가지 테스트: 여러 채널 동시 발송, priority 필터, 비활성 스킵, 한 채널 실패 격리, 활성 목록

## 면접 포인트
- **"여러 채널을 추상화한 이유"**: 프로덕션에서 SMS 하나가 막히면 다른 채널로 복원력 확보
- **"Priority 설계"**: CRITICAL(돈 관련)은 SMS, NORMAL(주문 체결)은 Telegram, LOW(디버그)는 Discord
- **"fire-and-forget 선택"**: 봇의 매매 의사결정이 알림 전송 레이턴시에 영향받으면 안 됨
- **"WebClient vs RestTemplate"**: Spring 6 이후 WebClient가 표준 (비동기 + non-blocking)
