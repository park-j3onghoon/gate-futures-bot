# Architecture Decision Records (ADR)

이 디렉토리는 프로젝트의 **아키텍처 결정**을 기록한다.
"왜 다른 옵션이 아닌 이걸 선택했나"를 명시적으로 남겨, 시간이 지나도 그 맥락이 사라지지 않도록 한다.

## ADR이란

Architecture Decision Record — Michael Nygard가 제안한 가벼운 의사결정 문서 양식.

- 결정 시점의 **상황(Context)**, **결정(Decision)**, **대안(Alternatives)**, **결과(Consequences)** 를 한 페이지로 기록
- 한 번 수락(Accepted)되면 **수정하지 않는다**. 결정이 뒤집히면 새 ADR로 `Superseded` 처리
- 번호는 시간순으로 매기되 의미는 없다 (`0001`, `0002`, ...)

## 언제 ADR을 작성하는가

다음 중 하나라도 해당하면 ADR을 추가한다:

- 라이브러리/프레임워크/외부 의존성 선택
- 모듈 경계, 레이어, 의존성 방향 결정
- 데이터 저장소/포맷/스키마 결정
- 보안 모델, 인증 방식, 권한 모델 결정
- 명시적인 트레이드오프가 있는 패턴 채택 (FSM, Hexagonal, Event Sourcing 등)
- "왜 이렇게 했지?"가 6개월 뒤에 떠오를 만한 결정

**작성하지 않는 것**: 단순 구현 디테일, 버그 픽스, 리팩터링, 변수명, 일회성 선택.

## 워크플로우

1. 새 결정이 필요해지면 → `template.md` 복사 → 다음 번호로 새 ADR 생성
2. Status는 처음엔 `Proposed`. 결정 확정되면 `Accepted`로 변경
3. 나중에 결정이 뒤집히면 **새 ADR**을 만들고, 기존 ADR의 Status를 `Superseded by ADR-XXXX`로 업데이트
4. Status 외에는 기존 ADR을 수정하지 않는다 — 기록은 그 시점의 사고를 보존해야 한다

## Status 값

- `Proposed` — 제안됨, 아직 채택되지 않음
- `Accepted` — 채택되어 시행 중
- `Deprecated` — 더 이상 권장되지 않으나 코드에 남아있음
- `Superseded by ADR-XXXX` — 다른 ADR로 대체됨

## 인덱스

### 언어/플랫폼
- [ADR-0001 — Kotlin + Spring Boot 채택](0001-kotlin-spring-boot.md)

### 도메인 경계
- [ADR-0002 — Gate.io SDK 격리 (단일 진입점 GateClient)](0002-sdk-격리.md)
- [ADR-0003 — Strategy는 순수 판단 (List&lt;Candle&gt; 입력만)](0003-strategy-순수-판단.md)
- [ADR-0008 — Hexagonal: ExchangePort 추출](0008-hexagonal-port-adapter.md)

### 품질/검증
- [ADR-0004 — Ktlint 자동 포매팅](0004-ktlint.md)
- [ADR-0005 — ArchUnit 레이어 경계 검증](0005-archunit.md)

### 성능
- [ADR-0006 — Caffeine 기반 지표 캐시](0006-caffeine-cache.md)

### 운영/관측
- [ADR-0007 — Notification 다중 채널 아키텍처](0007-notification-multi-channel.md)
- [ADR-0011 — Event Sourcing 경량 (H2 append-only)](0011-event-sourcing-경량.md)
- [ADR-0012 — Kill Switch + Admin API](0012-kill-switch-admin-api.md)

### 도메인 규칙
- [ADR-0009 — Risk Manager 게이트키퍼 패턴](0009-risk-manager-게이트키퍼.md)
- [ADR-0010 — Position State Machine (FSM)](0010-position-fsm.md)

## 회고적 ADR에 대하여

ADR-0001 ~ ADR-0012는 결정 시점이 아닌 **사후에 회고적으로** 작성되었다.
프로젝트 초·중기 결정을 일관된 양식으로 정리하기 위함. 작성일은 2026-05-19로 통일했으나
실제 결정 시점은 각 ADR 본문 또는 commit history(`git log -- docs/study-notes/`)로 확인할 수 있다.
이 시점 이후의 ADR은 결정이 내려진 시점에 즉시 작성한다.
