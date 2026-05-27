# ADR-0013 — Python 포트 도입 및 모노레포 재구성

- **Status**: Accepted
- **Date**: 2026-05-26
- **관련**: ADR-0001 (Kotlin 채택), ADR-0002 (SDK 격리), ADR-0008 (Hexagonal Port/Adapter)

## Context

학습 목적으로 동일한 Gate.io 선물 봇을 Python으로도 구현하려 한다.

ADR-0001은 Python을 **1차 구현 언어로는 반려**했다 (거래소 SDK 품질·타입 안정성 우려). 이 ADR은 그 결정을 **뒤집지 않는다**. Kotlin 구현을 레퍼런스(완성본)로 유지한 채, Python 포트를 나란히 두어 두 생태계를 비교 학습하는 것이 목적이다. ADR-0001이 우려한 SDK 품질 문제는 공식 `gate-api` Python SDK 사용으로 완화한다.

기존 repo는 루트가 곧 Kotlin 단일 프로젝트였다 (`build.gradle.kts`, `gradlew`, `src/`가 루트). 두 구현이 한 repo에서 공존하려면 명시적 경계가 필요하다.

## Decision

**모노레포로 재구성하고, Python 스택을 아래로 확정한다.**

- 구조: 기존 Kotlin 프로젝트를 `kotlin/`로 이동, 새 Python 구현을 `python/`에 둔다. `docs/`(ADR·학습 노트)는 루트에서 두 구현이 공유한다.
- 패키지/실행: **uv** (venv·lockfile·실행 통합 → "딸깍" 실행).
- Gate.io 접근: **공식 `gate-api` Python SDK**. 어댑터에 격리한다 (ADR-0002 SDK 격리, ADR-0008 포트/어댑터를 Python에서도 동일 적용). 캔들 조회는 public, 매매는 HMAC-SHA512 서명을 SDK가 처리.
- 아키텍처: **Cosmic Python** (*Architecture Patterns with Python*). 차트 조회 범위에는 도메인 모델 + 포트/어댑터 + 서비스 레이어 + bootstrap DI를 적용한다. Repository/Unit of Work는 영속화(포지션·이벤트 저장)가 생기는 시점까지 보류한다.

## Alternatives Considered

- **별도 repo로 분리**: 깔끔한 분리·단순한 툴링. → ❌ Kotlin 레퍼런스 대조와 `docs`/ADR 공유가 어려워진다. 학습 목적상 두 구현을 나란히 보는 가치가 분리 비용보다 크다.
- **브랜치로 분리**: → ❌ 두 구현이 장기 공존할 때 혼란스럽다.
- **Gate 접근을 raw HTTP(httpx)로**: public 캔들 GET은 매우 단순. → ❌ 매매 단계에서 HMAC 서명을 직접 구현해야 하고, SDK 격리 패턴(ADR-0002)과 어긋난다.
- **패키지 관리 poetry / pip+venv**: → ❌ uv가 venv·lock·실행을 단일 도구로 묶어 실행 진입장벽이 가장 낮다. (취향 차이로 큰 격차는 아님)
- **평면(flat) 구조로 시작**: → ❌ 최종 목표(매매·리스크·이벤트)까지 가면 레이어가 필요해진다. Cosmic Python이 기존 Kotlin 헥사고날(ADR-0008)과 1:1 대응돼 비교 학습에 유리하다.

## Consequences

### Positive
- 두 구현을 나란히 비교 학습 가능. `docs`/ADR을 공유해 의사결정 맥락이 한곳에 모인다.
- SDK 격리·포트/어댑터 패턴이 Kotlin·Python에서 일관된다.
- uv로 의존성·실행이 단순해 "딸깍" 실행 목표에 부합.

### Negative
- 루트에 두 빌드 생태계(Gradle + uv)가 공존 → 빌드/실행 명령이 디렉토리별로 갈리고, `.gitignore`·경로 설정이 분기된다.
- 도메인 로직이 두 언어로 중복 — 변경 시 양쪽 동기화 부담.

### Neutral
- ADR-0001을 대체하지 않는다. Kotlin이 1차/레퍼런스 구현으로 남는다.
- Cosmic Python·uv 학습 곡선.

## Follow-up

- 영속화(포지션/이벤트 저장)를 Python에 도입할 때 Repository / Unit of Work 채택 ADR을 신규 작성한다.
- Python 측 전략/리스크/워커 구현 시 Kotlin ADR(0003 순수 전략, 0009 Risk Manager, 0010 FSM)과의 정합을 재검토한다.
- CI 추가 시 `kotlin/`·`python/` 잡을 분리한다.
