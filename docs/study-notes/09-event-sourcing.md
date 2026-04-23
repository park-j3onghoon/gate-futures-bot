# 공부 노트 09 — Event Sourcing 경량 (H2 append-only)

## 왜 필요한가
봇이 새벽에 알 수 없이 포지션을 열었다. 로그는 파일로 흩어져 있어 재구성 힘듦.
**도메인 이벤트를 DB에 append-only로 저장**하면:
- 시간 순서로 정확히 재생 가능
- contract별/type별 쿼리로 패턴 분석
- 포지션 상태 재구성 ("지금 상태는 어떻게 만들어졌나?")

## Full Event Sourcing vs 경량
| 접근 | 특징 |
|---|---|
| **Full ES** (Axon, EventStore) | 상태를 이벤트로만 저장. 재생으로 상태 복원. 복잡 |
| **경량 로그** (채택) | 기존 Service 위에 이벤트 로그만 append. 상태는 기존대로 계산 |

이 프로젝트는 이미 PositionTracker/RiskManager가 상태를 갖고 있으므로 **경량으로 충분**. Full ES는 운영 비용 큼.

## 기술 선택
| 옵션 | 평가 |
|---|---|
| **H2 embedded + Spring Data JDBC** (채택) | 외부 DB 불필요, 파일로 영속, Kotlin data class + @Table |
| JPA/Hibernate | overhead 크고 append-only에 오버킬 |
| 파일(JSON Lines) | 쿼리 불가능, 인덱스 없음 |
| PostgreSQL | 실제 프로덕션엔 좋지만 단일 프로세스 봇엔 과함 |

Spring Data JDBC는 JPA보다 단순 — 영속성 컨텍스트/dirty checking 없음. 데이터 접근에 **투명**.

## 스키마
```sql
CREATE TABLE domain_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    contract VARCHAR(32) NOT NULL,
    payload CLOB NOT NULL,  -- JSON 문자열
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_events_contract ON domain_events(contract);
CREATE INDEX idx_events_type ON domain_events(type);
CREATE INDEX idx_events_occurred_at ON domain_events(occurred_at DESC);
```

**왜 payload는 JSON?**: 이벤트 type마다 필드가 다름(`PositionOpened`는 size/price, `RiskDenied`는 reason). 모든 필드를 고정 컬럼으로 두면 NULL 지옥. JSONB(Postgres)나 TEXT/CLOB(H2)에 JSON 문자열로 저장이 유연.

## EventStore 패턴
```kotlin
@Service
class EventStore(private val repo: DomainEventRepository) {
    fun record(type: String, contract: String = "", payload: Map<String, Any?>): DomainEvent? =
        try {
            repo.save(DomainEvent(type = type, contract = contract, payload = json(payload)))
        } catch (e: Exception) {
            // 이벤트 저장 실패가 비즈니스 로직을 죽여선 안 됨
            logger.warn(...)
            null
        }
}
```

**장애 격리**: `try/catch`로 감싸서 이벤트 저장 실패가 매매 로직을 중단시키지 않도록.
- 이벤트는 "감사/디버깅" 용도, 비즈니스 정확성은 메모리 상태가 책임

## EventTypes 상수 객체
```kotlin
object EventTypes {
    const val POSITION_OPENED = "PositionOpened"
    const val RISK_DENIED = "RiskDenied"
    // ...
}
```
→ 문자열 오타 방지 + IDE 자동완성. enum은 DB 저장시 변환 필요해 불편.

## H2 identifier 대소문자 함정
```
jdbc:h2:...;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
```
H2 기본: identifier 대문자 자동 변환 → `domain_events` 테이블이 `DOMAIN_EVENTS`로 저장.
Spring Data JDBC 쿼리는 소문자 → **table not found**. 해결: `DATABASE_TO_UPPER=FALSE`.

## 테스트 전략
- `@SpringBootTest + in-memory H2` 조합
- `@DirtiesContext` 각 테스트 후 컨텍스트 초기화
- `@BeforeEach`에서 `DELETE FROM domain_events`로 격리
- 실제 DB로 통합 테스트 → 쿼리 문법 검증까지 가능

## 다음 단계 (향후 통합)
```kotlin
// CoinWorker.evaluateAndEnter
val decision = riskManager.canOpenPosition(contract)
if (decision is RiskDecision.Deny) {
    eventStore.record(EventTypes.RISK_DENIED, contract, mapOf("reason" to decision.reason))
    return
}
eventStore.record(EventTypes.POSITION_OPEN_ATTEMPT, contract, mapOf("size" to orderSize))
try {
    val result = trader.openLong(...)
    eventStore.record(EventTypes.POSITION_OPENED, contract, mapOf("orderId" to result.id))
} catch (e: Exception) {
    eventStore.record(EventTypes.POSITION_OPEN_FAILED, contract, mapOf("error" to e.message))
}
```

## 면접 포인트
- **"왜 Full Event Sourcing이 아닌가?"**: 쓰기 빈도 낮음, 상태 재구성 필요성 낮음. 로그 수준이면 충분.
- **"PostgreSQL로 옮길 때 뭘 바꿔야?"**: URL/username/password 변경만. JSONB로 payload 전환하면 쿼리 가능.
- **"Outbox 패턴과의 관계"**: Outbox는 "비즈니스 변경과 이벤트 발행을 원자적"이 목적. 이건 단순 로그 용도라 다름. Outbox 수준이 필요하면 `@Transactional` + Kafka publisher 추가.
- **"로그와 뭐가 다른가?"**: 로그는 텍스트, 이벤트는 **구조화 + 인덱싱 + 쿼리 가능**. contract별/type별 빠른 조회.
