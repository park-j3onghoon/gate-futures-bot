# ADR-0005 — ArchUnit으로 레이어 경계 검증

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/02-archunit.md

## Context

ADR-0002(SDK 격리)와 ADR-0008(Hexagonal Port/Adapter)이 정의한 의존성 방향이 **시간이 지나면 깨진다**.
누군가 "잠깐만 import 하나만 추가하면 되는데"로 시작해 결국 SDK 타입이 도메인 전체에 스며든다.
PR 리뷰로 막는 것은 휴먼 에러에 취약. **컴파일 가능한 검증이 필요**하다.

## Decision

**ArchUnit 1.3.0** 으로 다음 8개 규칙을 테스트로 강제한다.

1. `client/` 외부에서 Gate.io SDK(`io.gate..`) 임포트 금지
2. `strategy/`는 거래소·DB·외부 시스템에 의존 금지 (순수 판단 — ADR-0003)
3. `trading/`은 `client/` 구현체 직접 의존 금지 (Port만 — ADR-0008)
4. `notification/`은 단방향 — 알림 외부로만 흐름
5. 패키지 cyclic dependency 금지
6. `..config..`는 다른 도메인에 의존 가능, 역방향 금지
7. 도메인 패키지에서 `org.springframework.web..` 임포트 금지 (controller 외)
8. `events/` 패키지는 비즈니스 로직에 영향 주지 않음 (write-only)

테스트는 `./gradlew test`에 포함 — 위반 시 빌드 실패.

## Alternatives Considered

- **PR 리뷰로 검증**: 가장 가벼움. → ❌ 휴먼 에러 + 새 멤버 합류 시 컨텍스트 없음.
- **모듈 분리 (Gradle multi-module)**: 컴파일러가 경계 강제. → ❌ 빌드 복잡도 증가. 개인 프로젝트 규모엔 과함.
- **Detekt 커스텀 규칙**: 가능. → ❌ ArchUnit이 의존성 그래프 룰에 더 직관적.

## Consequences

### Positive
- 위반이 빌드 단계에서 즉시 발견 — 리뷰어 부담 없음
- 신규 개발자가 잘못된 임포트를 시도하면 IDE/빌드가 알려줌
- 의존성 그래프가 문서화된 효과 (테스트 코드 자체가 가독성 있는 spec)

### Negative
- 새 모듈 추가 시 규칙도 같이 업데이트해야 함 (관성)
- 처음 규칙 작성 시 현실과 안 맞는 케이스 가능 — 초기엔 더 느슨하게 시작

### Neutral
- 테스트 실행 시간 약간 추가 (수백 ms)

## Follow-up

- Port/Adapter 도입(ADR-0008) 후 추가 규칙 등록 — "trading/는 client/Adapter에 직접 의존 금지"
- 모듈 추가 시 README에 의존성 방향 다이어그램 갱신
