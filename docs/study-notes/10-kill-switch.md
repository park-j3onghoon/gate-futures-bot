# 공부 노트 10 — Kill Switch + Admin API

## 왜 필요한가
새벽 3시, 봇이 미쳐 날뛰고 있다. 시장은 폭락 중. SSH 접속해서 프로세스를 kill하면?
- 진행 중인 주문이 유실될 수 있음
- 포지션 청산 타이밍 놓침
- 어떤 상태에서 죽었는지 알 수 없음

**Kill Switch**: 프로세스를 죽이지 않고 **신규 주문만 차단**한다. 이미 열린 포지션의 청산 로직은 계속 돌아감. 전역 플래그 하나로 "진입 중단"을 깔끔하게.

## 설계
```
AtomicBoolean tripped   ← 스레드 안전, CAS로 one-way trip
AtomicReference<String> reason ← 왜 꺼졌는지 기록
```

`compareAndSet(false, true)`로 **중복 trip 방지** — 이미 trip 상태면 `false` 반환해 재알림 스킵.

## 왜 AtomicBoolean인가
```kotlin
@Volatile var tripped: Boolean = false  // ❌
```
- Volatile은 read/write atomicity만 보장, compound action 불가
- `if (!tripped) tripped = true`는 race condition
- AtomicBoolean.compareAndSet은 한 번의 원자 연산

**도메인 객체로 감싼 이유**:
```kotlin
@Component
class KillSwitch {
    private val tripped = AtomicBoolean(false)
    fun isTripped(): Boolean = ...
}
```
- 의도 명명: `killSwitch.isTripped()` >> `flag.get()`
- 테스트 주입 용이
- 다른 Boolean 플래그와 혼동 방지

## Admin API 보안
```kotlin
@RequestHeader("X-Admin-Token") token: String?
if (token != adminToken) throw 401
```

가장 단순한 **shared-secret 인증**. Spring Security는 과함. 단, 주의:
- `adminToken`이 **blank면 전체 401** (fail-safe)
- 환경변수 `ADMIN_TOKEN`에서 주입, 코드에 하드코딩 금지
- HTTPS 전제 (로컬 프로젝트라 생략)

실제 프로덕션이면: mTLS, OAuth2, IP allowlist 추가.

## 엔드포인트 설계
| Method | Path | 역할 |
|---|---|---|
| GET  | /admin/status | 현재 상태 스냅샷 (kill, positions, risk, counters) |
| POST | /admin/stop | kill switch trip + 이벤트 기록 + 알림 |
| POST | /admin/resume | kill switch reset |
| POST | /admin/positions/{contract}/reset | 수동 상태 리셋 (FAILED → IDLE) |

**idempotency**: stop을 두 번 호출해도 안전. `wasAlreadyTripped: true`로 응답만 달라짐.

## 운영 액션은 이벤트로 남긴다
```kotlin
if (toggled) {
    eventStore.record("KillSwitchTripped", payload = mapOf("reason" to reason))
    notifier.dispatch(CRITICAL 알림)
}
```
- 누가/왜/언제 kill 했는지 DB에 영구 기록
- CRITICAL 알림으로 운영자 통지 (텔레그램/카톡)

## @WebMvcTest 함정 — JDBC 충돌
```
Cannot resolve reference to bean 'JdbcMappingContext'
```
**원인**: 메인 클래스에 `@EnableJdbcRepositories` 있으면 `@WebMvcTest`도 그것을 처리하려 함. 근데 JDBC autoconfig는 disable이라 `JdbcMappingContext` 빈이 없음.

**해결**: `@EnableJdbcRepositories`를 별도 `@Configuration` 클래스로 분리
```kotlin
@Configuration
@EnableJdbcRepositories(basePackageClasses = [DomainEventRepository::class])
class EventStoreConfig
```
→ `@WebMvcTest`의 TypeExcludeFilter가 이 non-web config를 제외해준다.

## 테스트 전략
| 계층 | 도구 |
|---|---|
| KillSwitch (순수 로직) | JUnit + assertion (Mock 불필요) |
| AdminController (웹 계층) | @WebMvcTest + MockMvc + @MockkBean |

`@Import(KillSwitch::class)`로 실제 KillSwitch를 주입 — 컨트롤러가 kill switch와 실제로 상호작용하는지 검증.
나머지 협력자(PositionTracker, RiskManager, EventStore, NotificationDispatcher)는 @MockkBean.

## Kill Switch + Worker 통합 (향후)
CoinWorker의 매 iteration 시작 부분에서:
```kotlin
if (killSwitch.isTripped()) {
    logger.debug("kill switch tripped, skipping entry")
    return
}
```
이렇게 하면 "이미 열린 포지션 청산은 계속, 신규 진입만 중단"이 깔끔하게 구현됨.

## 면접 예상 질문
- Q: 왜 프로세스 kill이 아니라 flag인가? → 진행 중인 주문/청산을 graceful하게. 상태 유실 방지.
- Q: AtomicBoolean vs @Volatile? → compound action에서 volatile은 race 있음, CAS만 원자성.
- Q: @WebMvcTest에서 JDBC 관련 빈이 왜 실패? → 메인 클래스의 @EnableJdbcRepositories가 그대로 적용되므로 @Configuration으로 분리해야 함.
- Q: 외부에서 Admin API 뚫리면? → 단일 토큰이라 뚫리면 끝. 프로덕션은 mTLS + IP allowlist 필수.
