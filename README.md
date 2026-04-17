# gate-futures-bot

Gate.io 선물(Futures) 자동매매 봇 — **Kotlin + Spring Boot + Kotlin Coroutines** 학습 프로젝트.

여러 코인에 대해 RSI/SMA/EMA 기반 진입 조건과 TakeProfit/StopLoss 청산 조건을 평가하여 자동으로 주문을 실행합니다.

## 요구 사항

| 항목 | 버전 |
|---|---|
| JDK | 21+ (Temurin 권장) |
| OS | macOS / Linux / Windows |
| 의존성 | Gate.io 계정 + API Key/Secret (testnet 권장) |

Gradle Wrapper를 포함하므로 Gradle 설치는 불필요합니다.

## 빠른 시작 (testnet)

### 1. API 키 발급
[Gate.io testnet](https://www.gate.io/testnet) 또는 실계정에서 API Key/Secret을 발급합니다.

### 2. 프로파일 설정 파일 생성
`src/main/resources/application-dev.yml` 을 만들고 아래 내용을 채웁니다 (git에 커밋되지 않음):

```yaml
gate:
  api:
    key: <발급받은 API Key>
    secret: <발급받은 API Secret>
    host: https://fx-api-testnet.gateio.ws/api/v4  # testnet
    settle: usdt

bot:
  contracts:
    - BTC_USDT
  leverage: 5
  order-size: 1
  check-interval-sec: 60

strategy:
  contracts:
    BTC_USDT:
      long-entries:
        - indicator: RSI
          operator: LT
          value: 30.0
          period: 14
      short-entries:
        - indicator: RSI
          operator: GT
          value: 70.0
          period: 14
      exit-conditions:
        - { type: TAKE_PROFIT_PCT, pct: 5.0 }
        - { type: STOP_LOSS_PCT, pct: 3.0 }

logging:
  level:
    com.parkj3onghoon.gatefuturesbot: DEBUG
```

### 3. 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

앱이 시작되면 `ApplicationReadyEvent`에서 `WorkerOrchestrator`가 각 contract별 코루틴을 띄워 주기적으로 전략을 평가합니다.

### 4. 종료

`Ctrl+C` 로 종료. `@PreDestroy`가 모든 워커를 `cancelAndJoin`으로 정리합니다.

## 개발

```bash
./gradlew build              # 컴파일 + 테스트
./gradlew test               # 테스트만
./gradlew jacocoTestReport   # 커버리지 리포트
./gradlew bootRun            # 실행 (기본 프로파일)
```

## 프로젝트 구조

```
src/main/kotlin/com/parkj3onghoon/gatefuturesbot/
├── GateFuturesBotApplication.kt   # @SpringBootApplication 진입점
├── config/
│   ├── ApiProperties.kt           # Gate.io API 인증 설정
│   ├── BotProperties.kt           # 매매 설정 (contracts, leverage 등)
│   ├── StrategyProperties.kt      # contract별 전략 설정
│   └── GateApiConfig.kt           # FuturesApi Bean
├── client/GateClient.kt           # SDK 래퍼 (SDK 의존 격리)
├── exception/Exceptions.kt        # sealed class 예외 계층
├── model/                         # Candle, Position, OrderResult, Interval
├── trading/FuturesTrader.kt       # 롱/숏/청산 진입점
├── market/
│   ├── MarketDataService.kt       # 캔들/최신 가격 조회
│   └── Indicators.kt              # SMA/EMA/RSI top-level 함수
├── strategy/
│   ├── EntryCondition.kt          # 진입 조건 (RSI/SMA/EMA/PRICE × LT/LTE/GT/GTE)
│   ├── ExitCondition.kt           # 청산 조건 (TakeProfit/StopLoss/IndicatorExit)
│   ├── TradingStrategy.kt         # evaluateEntry (AND) / evaluateExit (OR)
│   └── StrategyFactory.kt         # yml 설정 → 도메인 변환
├── worker/
│   ├── CoinWorker.kt              # 코인별 코루틴 워커
│   ├── WorkerOrchestrator.kt      # supervisorScope로 워커 관리
│   └── BotRunner.kt               # Spring 생명주기 연결
└── ratelimit/
    ├── RateLimiter.kt             # interface (suspend fun acquire)
    └── InMemoryRateLimiter.kt     # Token Bucket 구현
```

## 아키텍처 개요

```
BotRunner (ApplicationReadyEvent)
    └─ WorkerOrchestrator.runAll (supervisorScope)
         └─ CoinWorker.run (코인마다 1 코루틴)
              └─ rateLimiter.acquire → getPosition → updateCandles
                   └─ position 있음 → evaluateExit → closePosition
                   └─ position 없음 → evaluateEntry → openLong/openShort
```

레이어 분리:
- **Strategy**: 순수 판단 (Spring 무관, 데이터를 파라미터로)
- **Trading**: 주문 실행 (포지션 검증, 레버리지)
- **Client**: 유일한 SDK 사용자 (에러 매핑 + retry)
- **Worker**: 코루틴 + 캔들 캐시 + 루프

## 학습 가이드

Python/Django 개발자 관점에서 Kotlin + Spring Boot 스택을 설명하는 HTML 가이드:

[`docs/index.html`](docs/index.html)

- 00 환경 설정 / 01 Kotlin 기초 / 02 Gradle / 03 Spring Boot
- 04 매수/매도 (1단계) / 05 차트 데이터 / 06 자동 진입 / 07 자동 청산
- 08 코루틴 & 워커 (supervisorScope, Token Bucket, asyncio 비교)

## 테스트

JUnit 5 + MockK. 113개 테스트:

- **에러 매핑**: GateClient의 Gate.io 에러 라벨 → sealed 예외 변환
- **롱/숏 대칭**: 모든 진입/청산 시나리오의 롱·숏 양쪽
- **경계값**: RSI 0/50/100, TakeProfit 정확 경계, 빈 캔들, period 부족
- **워커 생명주기**: cancellation, 예외 격리, 델타 캐시, 전체 사이클

## 보안 주의

- **API Key/Secret**은 절대 커밋 금지. `application-dev.yml`, `application-prod.yml`은 `.gitignore` 대상
- **운영 환경**에서는 환경변수로 주입: `GATE_API_KEY=... GATE_API_SECRET=... ./gradlew bootRun`
- **실제 거래는 위험**합니다. 반드시 testnet에서 충분히 검증 후 소액으로 시작

## 라이선스

개인 학습 프로젝트. 실전 거래에 사용 시 발생하는 손실에 대해 책임지지 않습니다.
