# ADR-0001 — Kotlin + Spring Boot 채택

- **Status**: Accepted
- **Date**: 2026-05-19 (회고 작성, 실제 결정: 프로젝트 초기 setup)

## Context

Gate.io 선물 자동매매 봇을 새로 만들어야 한다. 요구사항:
- Gate.io REST API + WebSocket 호출
- 1초~분 단위로 캔들 조회 → 전략 판단 → 주문 (다수 코인 동시)
- 24/7 데몬으로 동작, 장애 시 재시작·관측 필요
- 단일 개발자(개인 학습 프로젝트)지만, 실제 자금이 오가는 시스템

## Decision

**Kotlin 2.1.x + Spring Boot 3.5.x + JDK 21 + Gradle Kotlin DSL** 을 채택한다.

## Alternatives Considered

- **Python + FastAPI/asyncio**: 학습자료 풍부, 빠른 프로토타이핑. → ❌ 거래소 SDK 품질이 JVM 대비 약함. 멀티스레드/타입 안정성에서 손해.
- **Java + Spring**: Spring 생태계 그대로. → ❌ Kotlin 대비 boilerplate 과다 (data class, null safety, 코루틴 부재).
- **Go**: 단일 바이너리, 가벼움. → ❌ Gate.io 공식 SDK가 Java만큼 완전하지 않음. 개인 학습 목표(JVM 생태계)와 맞지 않음.
- **TypeScript + Node.js**: ccxt 등 통합 거래소 라이브러리 풍부. → ❌ 타입 안전성이 Kotlin보다 약하고, 장기 실행 데몬으로서 JVM의 모니터링/프로파일링 도구가 더 성숙.

## Consequences

### Positive
- Spring DI/Config/Web/Data 등 검증된 생태계 즉시 활용
- Kotlin null safety + data class + 코루틴 → 멀티 워커(코인별) 작성 깔끔
- Gate.io 공식 Java SDK(`io.gate:gate-api`)를 그대로 사용 가능
- 학습 자료: 이 프로젝트는 Python → Kotlin 전환을 학습 목표 중 하나로 포함

### Negative
- JVM startup 시간 (수 초). 로컬 개발 사이클 다소 느림
- 메모리 footprint 비교적 큼 (Python 봇 대비 수배)
- 개인 학습 프로젝트 단계에선 Python 자료가 더 많음 (학습 노트로 보완 — `docs/study-notes/`)

### Neutral
- Gradle Kotlin DSL을 build script로 사용 → IDE 자동완성 강함, 다만 Groovy 자료보다 적음
- JDK 21 (가상 스레드 사용 가능하지만 현재는 코루틴 위주)

## Follow-up

- 컨테이너화 시 GraalVM Native Image 검토 (startup/메모리 개선)
- 멀티 인스턴스 분산이 필요해지면 Redis 기반 RateLimiter/락 도입 (ADR 신규 작성)
