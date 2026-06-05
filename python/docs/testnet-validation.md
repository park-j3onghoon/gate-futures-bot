# `order` 명령어 매매 — Gate testnet 검증 기록

실행일: 2026-06-05 · 대상 기능: 슬래시 DSL `order`(롱/숏 시장가 진입 + TP/SL 가격 트리거 청산 + 부분실패 롤백)
관련 커밋: `0542c1c`(기능) · `4162561`(testnet 거부 픽스 `close=True`)

## 환경

- **host**: `https://api-testnet.gateapi.io/api/v4` (`.env`의 `GATE_API_HOST`, `Settings.is_testnet == True`)
- 인증: `python/.env`의 `GATE_API_KEY`/`GATE_API_SECRET` (Gate **Futures TestNet APIKeys** 탭에서 발급, mainnet 키와 분리)
- 잔고: testnet 가짜 자금
- ⚠️ `GATE_API_HOST` 미설정 시 기본값은 **mainnet**(`api.gateio.ws`) — testnet 실험 중엔 이 줄을 지우지 말 것.

## 재현 방법

```bash
cd python
# 0) 연결·현재가 확인 (인증 불필요)
uv run gatebot fetch BTC_USDT 1m --limit 1
# 1) dry-run으로 요청 미리보기 (주문 안 나감)
uv run gatebot --dry-run order "BTC_USDT / 시장가 / 롱 / 3배 / tp:<현재가+α> / sl:<현재가-α> / size:1"
# 2) 실제 testnet 주문 (가짜 돈)
uv run gatebot order "BTC_USDT / 시장가 / 롱 / 3배 / tp:<...> / sl:<...> / size:1"
# 3) 포지션 확인 / 청산
uv run gatebot pos BTC_USDT
uv run gatebot close BTC_USDT
```
> 봇에는 트리거 목록 조회 CLI가 없어서, 트리거 등록/정리는 아래처럼 SDK로 직접 확인했다.
> `ex._futures_api.list_price_triggered_orders(ex._settle, status='open', contract='BTC_USDT')`

## 테스트 매트릭스 (전부 실제 testnet 실행)

| # | 케이스 | 입력 | 결과 |
|---|---|---|---|
| **A** | 롱 + TP/SL | `롱 / 3배 / tp:현재가+600 / sl:현재가-600 / size:1` | 진입 체결, SL(price=62321 **rule=2** ≤) + TP(price=63521 **rule=1** ≥) 등록, `close-long-position`. 정리 OK ✅ |
| **B** | 숏 + TP/SL | `숏 / 3배 / tp:현재가-600 / sl:현재가+600 / size:1` | **size=-1**(부호 변환), SL(63524 **rule=1**) + TP(62324 **rule=2**) 등록, `close-short-position`. 정리 OK ✅ |
| **C** | 네이키드(sl 없음) | `롱 / 3배 / tp:현재가+600 / size:1` | echo `sl=-`, `SL 트리거: 미설정`, **⚠️ 네이키드 경고** 출력, 트리거 1개(TP)만 등록 ✅ |
| **D** | **트리거 실제 발동** | `롱 / 3배 / tp:현재가+5 / sl:현재가-400 / size:1` | 진입 후 **25초 시점 가격이 tp(63008) 도달(63022.8) → TP 자동 발동, 포지션 청산**. SL은 **auto_cancelled**(open 트리거 0) ✅ |
| **E** | 검증/파싱 거부 (주문 미발생) | 아래 6종 | 전부 정확히 거부, 포지션 잔존 없음 ✅ |
| **F** | dry-run | `--dry-run ... 롱 / 5배 / tp:64000 / sl:62000 / size:1` | write 미전송, 요청 본문 + 트리거 rule/order_type만 출력, 트리거 id `dry-run` 표기 ✅ |

### Test E 상세 (전부 주문 안 나감)
| 케이스 | 입력 | 에러 |
|---|---|---|
| E1 방향 위반 | 롱 `tp<현재가` | `PositionError: 롱 tp(62937)는 현재가(63037)보다 높아야 합니다` |
| E2 레버리지 캡 | `11배` | `OrderError: leverage(11)가 허용 범위(1~10) 밖` |
| E3 size 캡 | `size:6` | `OrderError: size(6) 절대값이 안전 캡(5) 초과` |
| E4 size 누락 | `size:` 없음 | `ParseError: 필수 항목 누락: ['size']` |
| E5 미소비 토큰 | `sl;62000`(`:` 대신 `;` 오타) | `ParseError: 알 수 없는 토큰: 'sl;62000'` — **손절 침묵 누락 방지(Sec10)** |
| E6 소수 레버리지 | `9.5배` | `ParseError: 알 수 없는 토큰: '9.5배'` |

## 핵심 발견

1. **버그 발견·수정 (`4162561`)**: `FuturesInitialOrder`에 `close=True`가 없으면 Gate가
   `invalid argument: ... either isclose must be set or auto_size must be non-empty`로 트리거 등록을 거부.
   `is_close=True`는 wire에 `close:False`가 같이 실려 실패 → working하던 `close_position`과 동일하게 **`close=True`**가 정답.
   (이 페이로드 가정이 단위테스트로 못 잡히는 유일한 부분이었고, testnet 스모크가 정확히 잡음.)

2. **트리거 rule 매핑 라이브 확정**:

   | 방향 | TP rule | SL rule | order_type |
   |---|---|---|---|
   | 롱 | **1** (계산가 ≥ trigger, trigger > 현재가) | **2** (계산가 ≤ trigger, trigger < 현재가) | `close-long-position` |
   | 숏 | **2** | **1** | `close-short-position` |

3. **부분실패 롤백 실거래 확인**: `close=True` 적용 전, SL 등록이 거부되자
   `보호 트리거 등록 실패 — 롤백 완료`와 함께 진입 포지션이 자동 청산됨 → **네이키드 포지션 안 남음**.

4. **체결 판정 = 응답 fill_price**: 진입 응답 `status=finished, fill_price=...`로 체결 확인, get_position 의존 안 함.

## SDK 주의사항 (봇 영향 없음, 도구용)

`list_price_triggered_orders(status='finished')`는 testnet이 돌려주는 `finish_as='auto_cancelled'`를
gate-api 모델(`FuturesPriceTriggeredOrder`)이 거부(`ValueError`, 허용값 `cancelled/succeeded/failed/expired`)하여 **크래시**한다.
봇은 이 호출을 안 쓰므로 무관하나, 향후 "트리거 목록" CLI를 추가하면 `status='open'`만 쓰거나 예외 처리 필요.

## 아직 미검증

- **tick size 정렬**: tp/sl이 컨트랙트 `order_price_round`에 안 맞으면 Gate가 거부할 수 있음. 이번엔 정수 가격을 써서 안 걸림.
- **IOC 완전 미체결**: testnet 시장가는 항상 체결돼 미체결 분기는 라이브로 못 재현(단위테스트로 커버).
- **숏 진입 + 고배율 leverage 경로**: 3배만 확인.
