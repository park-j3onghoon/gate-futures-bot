# 공부 노트 02 — ArchUnit 레이어 경계 자동 검증

## 왜 필요한가
아키텍처 규칙(예: "strategy는 client에 의존 X")을 **리뷰어 기억력에만 의존**하면 시간이 지날수록 침식됨. 테스트로 강제하면 PR 단계에서 자동 차단.

## ArchUnit vs 대안
| 도구 | 특징 |
|---|---|
| **ArchUnit** | JVM 클래스 분석. 테스트로 표현. 러닝 작음 |
| SonarQube 아키텍처 룰 | 별도 서버 필요. GUI 설정 |
| jdepend / Structure101 | 오래됨, 부분적 기능 |
| Package-private 접근제어 | 언어 기능이라 강력하지만 Kotlin엔 없음 |

**선택**: ArchUnit — 다른 테스트와 동일한 방식으로 CI에서 자동 실행.

## 핵심 패턴
```kotlin
class ArchitectureTest {
    private val importedClasses =
        ClassFileImporter()
            .withImportOption(DO_NOT_INCLUDE_TESTS)
            .importPackages("com.parkj3onghoon.gatefuturesbot")

    @Test
    fun `strategy must not depend on SDK`() {
        noClasses()
            .that().resideInAPackage("..strategy..")
            .should().dependOnClassesThat().resideInAPackage("io.gate..")
            .because("strategy는 순수 도메인이어야 함")
            .check(importedClasses)
    }
}
```

## 이 프로젝트에 적용한 룰 8개
1. **strategy → Gate SDK 금지**: 도메인 순수성
2. **strategy → client 금지**: 도메인/인프라 분리
3. **SDK 사용은 client + config만**: SDK 격리 (config는 @Bean 등록용)
4. **model은 어느 패키지에도 의존 X**: 순수 값 객체
5. **누구도 worker에 의존 X**: worker는 최상위 오케스트레이션
6. **exception은 비즈니스 레이어 몰라야**: 독립 계층
7. **trading → strategy/worker 참조 금지**: 주문 실행만
8. **ratelimit는 유틸리티**: 독립

## 실전 팁
- **점진 도입**: 처음엔 룰 몇 개만. 기존 위반이 많으면 `@ArchIgnore`로 임시 예외
- **메시지에 "because"** 꼭: 미래의 팀원이 "왜 이 룰?"을 알 수 있게
- **`resideOutsideOfPackage`**: 역방향 룰 표현 (A 외의 곳은 X 금지)
- **`noClasses` vs `classes`**: 대부분 `noClasses().that().should().dependOnClassesThat()` 패턴으로 충분

## ArchUnit에서 자주 쓰는 DSL
```kotlin
// 클래스 이름 기반
classes().that().haveSimpleNameEndingWith("Service")
    .should().beAnnotatedWith(Service::class.java)

// 레이어 정의
layeredArchitecture()
    .consideringAllDependencies()
    .layer("Controller").definedBy("..controller..")
    .layer("Service").definedBy("..service..")
    .layer("Repository").definedBy("..repository..")
    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")

// 순환 의존 금지
slices().matching("..(*)..").should().beFreeOfCycles()
```

## 면접 포인트
- "레이어드 아키텍처의 **경계 침식**을 자동 방지"
- "코드 리뷰에서 '이 import 왜 여기에?' 수동 지적 불필요"
- "Hexagonal/Port-Adapter 도입 후 더 강한 룰 추가 예정 (Domain은 어떤 Adapter도 참조 X)"
