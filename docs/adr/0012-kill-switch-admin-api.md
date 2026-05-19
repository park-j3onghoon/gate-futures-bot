# ADR-0012 — Kill Switch + Admin API

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/10-kill-switch.md

## Context

새벽 3시에 시장이 폭락 중이고 봇이 의도와 다르게 동작한다. 운영자의 선택지는?

- `kill -9` → 진행 중인 주문 유실, 청산 타이밍 놓침, 어떤 상태에서 죽었는지 불명
- 거래소 UI에서 수동 청산 → 봇이 모르는 상태로 빠짐 (유령 포지션 — ADR-0010과 충돌)
- 코드 수정 후 재배포 → 너무 느림

원하는 동작: **신규 진입만 차단, 청산은 계속**. 즉 부분 정지.

## Decision

**전역 Kill Switch + 인증된 Admin API**를 도입한다.

### Kill Switch
- `AtomicBoolean tripped` + `AtomicReference<String?> reason`
- `trip(reason)`: `compareAndSet(false, true)` — 한 번만 효력, 중복 알림 차단
- `reset()`: 반대 방향 CAS
- 워커는 매 iteration 시작 시 `isTripped()` 체크 → trip 상태면 **진입만** skip (청산은 계속)

### Admin API
- 보호: `X-Admin-Token` 헤더 단일 토큰 (`admin.token` 환경변수)
  - `admin.token`이 blank면 **모든 요청 401** (fail-safe)
- 엔드포인트:
  - `GET  /admin/status` — 스냅샷 (kill, positions, risk, counters)
  - `POST /admin/stop` — kill trip + 이벤트 기록 + CRITICAL 알림
  - `POST /admin/resume` — kill reset
  - `POST /admin/positions/{contract}/reset` — FSM 수동 리셋 (FAILED→IDLE)

## Alternatives Considered

- **SIGTERM으로 graceful shutdown**: 표준적. → ❌ 청산까지 멈춤. 진입만 차단하고 싶은데 표현 불가.
- **Spring Security 본격 도입**: 견고. → ❌ 단일 운영자/단일 토큰 모델엔 과함.
- **OAuth2 / JWT**: → ❌ 인증 서버 추가, 토큰 회전 운영 등 부담.
- **CLI 도구만 (DB 직접 수정)**: → ❌ 거래소 호출과 분리되어 일관성 보장 불가.

## Consequences

### Positive
- 부분 정지(진입만 차단)가 깔끔하게 표현됨
- AtomicBoolean이라 락 없이 안전 (`@Volatile`은 compound action 미지원)
- Kill / Reset이 EventStore에 기록됨 → 사후 분석 가능
- CRITICAL 알림으로 모든 채널(텔레그램/카톡)에 즉시 통지
- 운영자가 FSM 상태도 수동 리셋 가능 (유령 복구)

### Negative
- 단일 토큰 인증 — 토큰 유출 시 즉시 전체 운영 권한 노출
- HTTPS 전제 (현재 로컬, 외부 노출 시 TLS 필수)
- `admin.token`을 환경변수로 안전하게 주입해야 함 (코드 하드코딩 금지)
- `@EnableJdbcRepositories`를 main class에 두면 `@WebMvcTest`와 충돌 — 별도 Config로 분리 필요했음 (해결됨)

### Neutral
- 워커가 매 iteration `isTripped()` 호출 — AtomicBoolean.get()은 거의 무료

## Follow-up

- 외부 노출 시 mTLS / IP allowlist 추가 (ADR 신규)
- Kill 자동 트리거 (Risk 한도 도달 시 자동 stop) 정책 검토
- 토큰 회전 자동화
