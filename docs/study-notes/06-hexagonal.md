# 공부 노트 06 — Port/Adapter (Hexagonal Architecture)

## 변경 전 / 후
### Before (Layered)
```
FuturesTrader  →  GateClient  →  FuturesApi (Gate.io SDK)
MarketDataService  →  GateClient
```
- 도메인 서비스가 **GateClient(infrastructure) 구체 클래스**를 직접 참조
- Gate 아닌 다른 거래소로 바꾸려면 여러 클래스 수정

### After (Hexagonal)
```
trading.FuturesTrader  →  trading.ExchangePort (interface)
                                 ↑ 구현
                            client.GateExchangeAdapter  →  GateClient  →  SDK
```
- 도메인은 **interface**만 본다
- 거래소 교체 = Adapter 새로 쓰기 (Binance 추가시 `BinanceExchangeAdapter`)

## "Port"와 "Adapter" 용어
- **Port**: 도메인이 외부와 통신하는 **추상 계약** (interface). trading 패키지에 위치.
- **Adapter**: Port의 구현체. client/infrastructure에 위치.
- **Outgoing Port**: 도메인 → 외부 (ExchangePort, NotificationPort)
- **Incoming Port**: 외부 → 도메인 (API Controller, CLI, Event listener)

## 왜 Port가 `trading` 안에 있나?
Port는 **도메인 소유**다. "이 도메인이 무엇을 필요로 하는지"의 명세. 그래서:
- Port 위치: 도메인 패키지 (trading)
- Adapter 위치: infrastructure 패키지 (client)

ArchUnit 룰: `trading/market/worker는 client를 직접 참조 금지 (Adapter 제외)`

## 실질적 이점
1. **테스트 단순화**: `mockk<ExchangePort>()` 하나로 getPosition/createOrder/getCandles 전부 stub. 이전엔 GateClient의 20개 메서드를 모두 mock.
2. **의존성 그래프 단순**: 도메인이 SDK 예외/타입을 몰라도 됨
3. **교체 가능성**: 거래소 추가가 `@Profile("binance")` BinanceAdapter 추가로 해결
4. **Adapter가 "번역" 책임 맡음**: `createOrder(contract, size, leverage)` → `updateLeverage + createOrder` 2 호출을 Adapter가 처리

## 주의한 것
- `ExchangePort.createOrder(contract, size, leverage)`로 단일 호출로 통합 — 이전엔 도메인이 `updateLeverage → createOrder` 순서를 알아야 했음. 이제 Adapter가 조합.
- `getLatestPrice`도 Port에 넣음 — "최근 1개 캔들 fetch"를 도메인 의도로 표현하고 Adapter가 구체적 limit=1 처리

## 대안 검토
| 접근 | 평가 |
|---|---|
| **Hexagonal (Port/Adapter)** | 채택. 계약이 명시되고 테스트 쉬움 |
| 전통 레이어드 | 단순하지만 경계가 흐릿 |
| Clean Architecture (더 엄격) | UseCase 레이어까지 도입. 이 규모엔 과함 |
| Onion Architecture | Hexagonal과 유사. 용어만 다름 |

## ArchUnit으로 강제
```kotlin
@Test
fun `trading market worker depend on ExchangePort not client directly`() {
    noClasses()
        .that().resideInAnyPackage("..trading..", "..market..", "..worker..")
        .and().haveSimpleNameNotEndingWith("Adapter")
        .should().dependOnClassesThat().resideInAPackage("..client..")
        .check(importedClasses)
}
```
→ 누가 실수로 `trading.NewService`에 `GateClient`를 import하면 테스트 실패.

## 면접 포인트
- **"왜 Port는 도메인 내부에 있는가?"**: 도메인이 "나에게 필요한 외부 능력"을 스스로 정의. 외부 시스템이 도메인을 결정하지 않음.
- **"멀티 거래소 확장"**: `@Primary` + `@Qualifier("binance")` 등으로 조건부 주입. `List<ExchangePort>` 주입해 라우터도 가능.
- **"테스트 vs E2E"**: Port mock으로 단위 테스트 + Adapter는 WireMock/MockServer로 통합 테스트.
- **"Layered vs Hexagonal"**: 레이어드는 "수직" 계층 (Controller → Service → Repo). Hexagonal은 "내부/외부" (Domain 중심, 외부는 Adapter로 교체 가능).
