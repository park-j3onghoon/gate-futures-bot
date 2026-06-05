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
uv run gatebot order "<명령 문자열>"                 # 슬래시 명령 매매: 롱/숏 + TP/SL (실거래)
uv run gatebot close [BTC_USDT]                      # 포지션 청산 (실거래)
```

### `order` 슬래시 명령 매매
슬래시(`/`)로 구분한 한 줄 명령으로 롱/숏 시장가 진입 + 진입 후 TP(익절)/SL(손절) 가격 트리거 청산을 등록합니다.
```bash
uv run gatebot order "BTC_USDT / 시장가 / 롱 / 9배 / tp:77777 / sl:66666 / size:2"
```
- 토큰은 위치 무관(순서 자유), `롱`/`long`/`buy` · `숏`/`short`/`sell` 동의어, `9배`/`9x` 모두 인식.
- 필수: 컨트랙트·방향·레버리지·`size:N`. 선택: `tp:`·`sl:`(둘 다 없으면 ⚠️ 네이키드 경고).
- 알 수 없거나 중복된 토큰은 거부(`ParseError`) — 오타로 손절이 조용히 누락되는 사고를 막습니다.
- 실주문 직전 파싱 결과를 1줄로 echo — 오타가 다른 심볼로 둔갑하지 않았는지 눈으로 확인하세요.

### `--dry-run` 모드 (안전장치)
모든 명령 앞에 `--dry-run`을 붙이면 **write 호출(매수·청산·leverage)은 실제로 전송되지 않습니다.** read는 실제 호출 — 시장·포지션 상태는 진짜로 봅니다.
```bash
uv run gatebot --dry-run buy BTC_USDT --size 1   # 실제 주문 안 감, "요청 본문만" 출력
```

### 인증 (매매·포지션 조회용)
**`python/.env` 파일에 키를 두면 자동 로드됩니다** (쉘 export 불필요).

```bash
cp .env.example .env
# .env 편집:
# GATE_API_KEY=...
# GATE_API_SECRET=...
uv run gatebot pos
```

`.env`는 `.gitignore` 대상이라 커밋되지 않습니다. 쉘 `export`로 주입한 환경변수가 있으면 그게 우선이고, 없을 때만 `.env`가 채웁니다.

### 안전 캡 (서비스 레이어)
- `|size| > 5` 또는 `leverage > 10`이면 `OrderError`로 거부 (코드 버그가 큰 손실로 번지는 걸 차단). 기본 레버리지는 3.
- `order`는 진입 응답의 `fill_price`로 체결을 판정 — 미체결(IOC)이면 트리거를 걸지 않고 명시 보고.
- TP/SL 트리거 중 하나라도 등록 실패하면 성공 트리거 취소 + 포지션 청산으로 롤백(네이키드 포지션 방지).

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
