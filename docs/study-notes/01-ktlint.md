# 공부 노트 01 — Ktlint 자동 포매터 도입

## 왜 필요한가
팀 프로젝트에서 **포매팅 논쟁을 없애기** 위해. Kotlin 공식 스타일 가이드를 자동 강제.
PR 리뷰에서 "들여쓰기 맞춰주세요" 같은 저부가가치 코멘트 제거.

## Ktlint vs 대안
| 도구 | 특징 |
|---|---|
| **Ktlint** | Kotlin 공식 style 기반, zero-config, 자동 수정(`ktlintFormat`) |
| Detekt | 정적 분석 + 포매팅. 더 강력하지만 복잡 |
| Spotless | 다언어 포매터. Java/Kotlin/XML 혼용 프로젝트에 |
| IntelliJ formatter | IDE 전용, CI 불가능 |

**선택**: Ktlint — Kotlin 전용 프로젝트에 가장 단순. 공식 `ktlint_official` 룰셋 따라감.

## 설정 핵심
```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

ktlint {
    version.set("1.5.0")          // ktlint 바이너리 버전
    ignoreFailures.set(false)     // CI에서 실패로 처리
}

tasks.named("check") {
    dependsOn("ktlintCheck")      // build 시 자동 검증
}
```

## 주요 명령어
- `./gradlew ktlintCheck` — 위반만 보고 (CI에서 사용)
- `./gradlew ktlintFormat` — 자동 수정
- `./gradlew check` — 테스트 + ktlintCheck (기본 build 파이프라인)

## 이번에 변경된 것
- **탭 → 4-space** 전체 변환 (Kotlin 공식은 space 4칸)
- **trailing comma 추가** (`val a: Int, val b: Int,`)
- data class 파라미터 줄바꿈 정규화
- import 정렬

## 실전 팁
- **pre-commit hook**: 로컬 커밋 전 자동 포맷
  ```bash
  ./gradlew addKtlintFormatGitPreCommitHook
  ```
- `.editorconfig`로 IDE도 맞춤 (ktlint가 읽음)
- 팀 합의 필요한 커스텀 룰은 `.editorconfig`에 `ktlint_*` 키로

## 면접 포인트
- "Python의 black, JS의 Prettier에 해당"
- "CI에서 `ktlintCheck` 실패시 merge 차단 → 리뷰 품질 관리"
- "자동 포맷은 가독성+속도 둘 다. 포맷 PR 코멘트 시간을 비즈니스 로직 리뷰에 쓸 수 있음"
