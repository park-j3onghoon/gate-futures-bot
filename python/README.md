# gatebot — Gate.io 선물 봇 (Python)

[Cosmic Python](https://www.cosmicpython.com/) 구조로 작성한 Python 포트. 도입 배경·스택 결정은 [ADR-0013](../docs/adr/0013-python-port-monorepo.md).

## 요구 사항

- Python 3.12+
- [uv](https://docs.astral.sh/uv/)

## 설치

```bash
cd python
uv sync
```

## 실행

서브커맨드 구조:
```bash
uv run gatebot fetch [BTC_USDT] [1m] [--limit 10]   # 캔들 조회 (인증 불필요)
uv run gatebot pos   [BTC_USDT]                      # 포지션 조회 (인증)
uv run gatebot buy   [BTC_USDT] --size N [--leverage 3]   # 롱 진입 (실거래)
uv run gatebot close [BTC_USDT]                      # 포지션 청산 (실거래)
```

### `--dry-run` 모드 (안전장치)
모든 명령 앞에 `--dry-run`을 붙이면 **write 호출(매수·청산·leverage)은 실제로 전송되지 않습니다.** read는 실제 호출 — 시장·포지션 상태는 진짜로 봅니다.
```bash
uv run gatebot --dry-run buy BTC_USDT --size 1   # 실제 주문 안 감, "요청 본문만" 출력
```

### 인증 (매매·포지션 조회용)
```bash
export GATE_API_KEY=...
export GATE_API_SECRET=...
export GATE_API_HOST=https://fx-api-testnet.gateio.ws/api/v4   # testnet (생략 시 mainnet)
uv run gatebot pos
```

### 안전 캡 (서비스 레이어)
- `size > 5` 또는 `leverage > 3`이면 `OrderError`로 거부 (코드 버그가 큰 손실로 번지는 걸 차단).
- 매수 직후 자동으로 `get_position` 호출해 의도한 size·entry로 들어갔는지 검증.

## 테스트

```bash
uv run pytest
```

## 구조 (Cosmic Python)

```
src/gatebot/
├── domain/          # 순수 도메인: Candle, Interval, Position, OrderResult, 예외 계층
├── adapters/        # 포트 + 어댑터: AbstractExchange ← GateExchange / DryRunExchange
├── service_layer/   # 유스케이스: market_data (조회), trading (매수·청산 + 안전 캡)
├── entrypoints/     # CLI (argparse 서브커맨드 + --dry-run)
├── bootstrap.py     # 의존성 조립 (DI, dry-run 데코레이터 wrapping)
└── config.py        # 환경변수 설정 (key/secret/host/settle)
```

Kotlin 레퍼런스(`../kotlin/`)의 `GateClient` / `ExchangePort` / `MarketDataService` / `FuturesTrader`와 대응한다.
SDK 격리는 [ADR-0002](../docs/adr/0002-sdk-격리.md), 포트/어댑터는 [ADR-0008](../docs/adr/0008-hexagonal-port-adapter.md) 참고.
