# gate-futures-bot

Gate.io 선물(Futures) 자동매매 봇 학습 프로젝트. 같은 봇을 두 가지 스택으로 구현하며 비교한다.

목표: **딸깍 실행 → 차트(캔들) 조회 → 조건 충족 시 매수/매도.**

## 구조 (모노레포)

| 디렉토리 | 구현 | 상태 | 실행 가이드 |
|---|---|---|---|
| [`kotlin/`](kotlin/) | Kotlin + Spring Boot + Coroutines | 레퍼런스 (완성) | [`kotlin/README.md`](kotlin/README.md) |
| [`python/`](python/) | Python + uv + 공식 `gate-api` SDK (Cosmic Python 구조) | 진행 중 | [`python/README.md`](python/README.md) |

도입 배경과 스택 결정 근거: [ADR-0013](docs/adr/0013-python-port-monorepo.md)

## 공유 문서 (`docs/`)

- [ADR 인덱스](docs/adr/README.md) — "왜 이렇게 설계했나" 아키텍처 결정 기록
- `docs/study-notes/`, `docs/*.html` — 학습 노트 / HTML 가이드 (Python 개발자 관점의 Kotlin·Spring 설명)

## 보안 주의

- **API Key/Secret은 절대 커밋 금지.** 환경변수 또는 gitignore된 설정 파일로 주입한다.
- 실거래는 위험하다. testnet에서 충분히 검증 후 소액으로 시작한다.
- 개인 학습 프로젝트 — 실전 거래로 발생하는 손실에 책임지지 않는다.
