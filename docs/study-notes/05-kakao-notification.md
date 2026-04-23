# 공부 노트 05 — 카카오 "나에게 보내기" 채널

## 왜 "나에게 보내기"만 가능한가
카카오톡 정책: **상업/비즈니스 메시지**는 "카카오톡 채널 + 알림톡"(사전 심사 필요)으로만 가능.
"나에게 보내기"는 **본인 계정에 셀프 메시지**만 허용 — 개인 사이드 프로젝트/알림 봇에 적합.

| 카카오 API 종류 | 대상 | 비용 | 허용 여부 |
|---|---|---|---|
| 나에게 보내기 | 본인 | 무료 | ✅ 개인 가능 |
| 친구에게 보내기 | 친구(카카오톡 친구) | 무료 | △ 친구 동의 필요 |
| 알림톡 | 임의 번호(비즈니스) | 건당 ~15원 | ❌ 사업자 등록 + 채널 필요 |
| 친구톡 | 채널 친구 | 건당 ~15원 | ❌ 사업자 |

→ 개인 자동매매 봇엔 **"나에게 보내기"가 유일**한 무료 옵션.

## OAuth 플로우 요약
1. 카카오 개발자 사이트 → 애플리케이션 등록
2. "카카오 로그인" 활성화 + 도메인 등록 (localhost 가능)
3. **동의항목**에서 "카카오톡 메시지 전송(talk_message)" 활성화
4. OAuth로 access_token 획득:
   ```
   GET https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&scope=talk_message
   → code 받음
   POST https://kauth.kakao.com/oauth/token with code → access_token (6h) + refresh_token (60일)
   ```
5. access_token을 설정에 주입 (만료 전에 refresh_token으로 갱신)

## 이 구현의 단순화
- access_token을 **설정에서 직접** 받음
- 장기 운영시엔 `KakaoTokenManager`가 refresh_token으로 자동 갱신하는 편이 정석
- access_token 만료 시점에 401 → Dispatcher가 예외 catch → 알림 실패 (하지만 봇 로직엔 영향 X)

## API 요청 형식 (함정)
카카오 API는 특이하게 **form-urlencoded**에 **JSON 문자열을 `template_object` 필드**로 넣음:
```http
POST https://kapi.kakao.com/v2/api/talk/memo/default/send
Authorization: Bearer {access_token}
Content-Type: application/x-www-form-urlencoded

template_object={"object_type":"text","text":"hi","link":{...},"button_title":"..."}
```

### WebClient에서 form + JSON
```kotlin
val form = LinkedMultiValueMap<String, String>()
form.add("template_object", objectMapper.writeValueAsString(templateObject))

webClient.post()
    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
    .body(BodyInserters.fromFormData(form))
```

## 메시지 길이 제한
text 템플릿은 **200자 제한**. 메시지 잘림 방지:
```kotlin
"text" to event.format().take(MAX_TEXT_LENGTH)
```

## 실전 사용 조합
| 이벤트 | 채널 |
|---|---|
| 에러 (CRITICAL) | Telegram + Kakao + SMS |
| 주문 체결 (HIGH) | Telegram + Kakao |
| 일일 리포트 (NORMAL) | Telegram only (길이 제한 회피) |
| 디버그 (LOW) | Discord webhook |

## 테스트 전략
외부 HTTP 호출 테스트는 **MockWebServer**(OkHttp)로 가능하지만 이 프로젝트는 단순 유닛:
- `enabled` 판정
- 설정값 바인딩 (minPriority, linkUrl)
- 상수 검증 (MAX_TEXT_LENGTH)

실제 HTTP 동작은 **수동 테스트 + 로그 확인**으로 검증하는 편이 ROI 좋음.

## 면접 포인트
- **"카카오 API 정책"** 이해 (알림톡 vs 나에게 보내기 차이)
- **"OAuth access/refresh token 수명"** 관리 경험
- **"form-urlencoded + nested JSON"** 같은 이상한 API 패턴 대응
- **"채널별 길이/빈도 제한"** 인지 → Dispatcher에서 truncate
