# Extended Thinking

이 프로젝트의 모든 세션에서 **ultrathink** 수준의 확장 사고를 사용한다.

# 프로젝트 개요

Gate.io 선물 자동매매 Kotlin + Spring Boot 봇.
- Kotlin 2.1.x, Spring Boot 3.5.x, Gradle Kotlin DSL
- JDK 21+, gate-api SDK 7.1.8
- 개인 학습 프로젝트, 로컬 실행

# 일반 규칙

- 커밋 메시지, PR 제목/본문은 한글로 작성
- Git force push 금지
- 코드 내 주석은 한글 허용, 변수/함수/클래스명은 영어 (Kotlin camelCase)

# 코드 스타일

- 모듈은 SRP(단일 책임 원칙)를 준수하여 분리한다
- Kotlin 공식 코딩 컨벤션 준수
- 함수 내부 import 금지, 파일 최상단에 배치

# 아키텍처

- SDK 격리: gate-api 직접 사용은 GateClient에만
- Strategy는 순수 판단 (List<Candle>을 파라미터로 받음, 데이터 조회 안 함)
- EntryCondition / ExitCondition은 의미적으로 분리 (필드가 같아도 별도 클래스)
- ApiProperties / BotProperties 설정 분리
- 워커: Kotlin 코루틴 + supervisorScope (에러 격리)
- RateLimiter interface → InMemoryRateLimiter (향후 Redis 전환 가능)

# 테스팅

- JUnit 5 + MockK
- 테스트 메서드명은 영어 (backtick 형식), 한글 설명 포함 가능
- 커스텀 로직만 테스트 (SDK/프레임워크 빌트인 불필요)
- `./gradlew test` 로 실행
- `./gradlew jacocoTestReport` 로 커버리지 확인

# 보안

- API Key/Secret은 절대 코드에 하드코딩 금지
- application-dev.yml, application-prod.yml은 .gitignore 대상
- application.yml에는 환경변수 참조만 (${GATE_API_KEY:})

# 개발 워크플로우

1. Plan Mode에서 구현 계획 수립
2. /plan-review로 계획 리뷰
3. 구현
4. 테스트 작성 및 통과 확인 (./gradlew test)
5. /review로 코드 리뷰
6. 커밋 (한글 메시지)

# 빌드/검증 명령어

- 빌드: `./gradlew build`
- 테스트: `./gradlew test`
- 커버리지: `./gradlew jacocoTestReport`
- 실행: `./gradlew bootRun`
- 린트: Kotlin 컴파일러가 처리 (별도 린터 불필요)
