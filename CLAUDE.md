# Extended Thinking

이 프로젝트의 모든 세션에서 **ultrathink** 수준의 확장 사고를 사용한다.

# 프로젝트 개요

Gate.io 선물 자동매매 봇 학습 프로젝트. 같은 봇을 두 스택으로 구현하는 **모노레포**.
- `kotlin/` — Kotlin 2.1.x + Spring Boot 3.5.x + Gradle Kotlin DSL, JDK 21+, gate-api SDK 7.1.8 (레퍼런스 구현)
- `python/` — Python 3.12 + uv + 공식 gate-api SDK, Cosmic Python 구조 (진행 중)
- 개인 학습 프로젝트, 로컬 실행
- 모노레포 도입 배경: `docs/adr/0013-python-port-monorepo.md`

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

# 정책 결정 시 ADR 작성

코드 구현이 아닌 **아키텍처/정책 결정**이 내려질 때마다 `docs/adr/`에 ADR을 추가한다.

- 대상: 라이브러리/프레임워크 선택, 모듈 경계, 의존성 방향, 저장소·스키마, 보안 모델, 명시적 트레이드오프가 있는 패턴 채택
- 대상 아님: 단순 구현 디테일, 버그 픽스, 리팩터링, 일회성 선택
- 양식: `docs/adr/template.md` 복사 → 다음 번호로 새 파일 생성 → `docs/adr/README.md` 인덱스에 추가
- 한 번 Accepted된 ADR은 **수정하지 않는다**. 결정이 뒤집히면 새 ADR로 `Superseded` 처리
- 자세한 가이드: `docs/adr/README.md`

# 빌드/검증 명령어

## Kotlin (`kotlin/`에서 실행)
- 빌드: `cd kotlin && ./gradlew build`
- 테스트: `./gradlew test`
- 커버리지: `./gradlew jacocoTestReport`
- 실행: `./gradlew bootRun`
- 린트: Kotlin 컴파일러가 처리 (별도 린터 불필요)

## Python (`python/`에서 실행)
- 설치: `cd python && uv sync`
- 테스트: `uv run pytest`
- 실행(차트 조회): `uv run gatebot`
