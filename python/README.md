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

## 실행 (차트 조회)

캔들 조회는 public 엔드포인트라 **API 키 없이** 동작한다 ("딸깍" 실행).

```bash
uv run gatebot                       # BTC_USDT 1m 최근 10개
uv run gatebot ETH_USDT 5m --limit 20
```

testnet/실계좌나 매매(예정)에는 환경변수로 키를 주입한다:

```bash
export GATE_API_KEY=...
export GATE_API_SECRET=...
export GATE_API_HOST=https://fx-api-testnet.gateio.ws/api/v4   # testnet (생략 시 메인넷)
uv run gatebot
```

## 테스트

```bash
uv run pytest
```

## 구조 (Cosmic Python)

```
src/gatebot/
├── domain/          # 순수 도메인: Candle, Interval, 예외 (외부 의존 없음)
├── adapters/        # 포트 + 어댑터: AbstractExchange ← GateExchange (SDK 격리)
├── service_layer/   # 유스케이스: market_data (캔들 조회 / 최신가)
├── entrypoints/     # CLI ("딸깍" 실행)
├── bootstrap.py     # 의존성 조립 (DI)
└── config.py        # 환경변수 설정 (key/secret/host/settle)
```

Kotlin 레퍼런스(`../kotlin/`)의 `GateClient` / `ExchangePort` / `MarketDataService`와 대응한다.
SDK 격리는 [ADR-0002](../docs/adr/0002-sdk-격리.md), 포트/어댑터는 [ADR-0008](../docs/adr/0008-hexagonal-port-adapter.md) 참고.
