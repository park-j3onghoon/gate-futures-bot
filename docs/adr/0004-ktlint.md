# ADR-0004 — Ktlint 자동 포매팅

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 commit: 2026-04-24)
- **관련**: docs/study-notes/01-ktlint.md

## Context

Kotlin 코딩 스타일을 손으로 맞추는 비용이 누적되고 있다. import 순서, indentation, trailing comma, max line length 등.
PR 리뷰에서 "스타일 코멘트"가 본질적 리뷰를 가린다.

## Decision

**Ktlint 1.5.0** 을 Gradle 플러그인(`org.jlleitschuh.gradle.ktlint` 12.1.1)으로 통합한다.

- `./gradlew build` 또는 `./gradlew check` 시 `ktlintCheck` 자동 실행 → 위반 있으면 빌드 실패
- 개발자가 손으로 고치지 않고 `./gradlew ktlintFormat`으로 일괄 정리
- 예외(`build/`, `generated/`)만 필터로 제외

## Alternatives Considered

- **Detekt**: 정적 분석까지 포함. → ❌ 지금 단계엔 과함. 스타일만 자동화하면 충분.
- **IntelliJ "Reformat Code"**: 개인 설정에 의존. → ❌ CI/CD나 다른 개발자가 다른 결과를 낼 수 있음.
- **Spotless**: 다중 언어 지원. → ❌ Kotlin만 쓰므로 ktlint 단일이 더 단순.

## Consequences

### Positive
- 스타일에 대한 논쟁/리뷰 코멘트 사라짐
- 새 파일 추가 시 자동으로 표준에 맞춰짐
- ktlint 공식 표준은 Kotlin 공식 코딩 컨벤션과 정렬

### Negative
- ktlint가 강제하는 규칙(예: `AtomicReference<String?>(null)`을 줄바꿈 강제)이 가독성을 해치는 케이스가 가끔 있음
- 외부 코드를 복사해 올 때 한 번 포매팅 필요

### Neutral
- Build 시간 소폭 증가 (수 초)

## Follow-up

- 위반이 반복되는 규칙은 `.editorconfig`로 미세 조정 검토
- 팀 합류 시 Detekt 추가 검토 (복잡도, 안티패턴 규칙)
