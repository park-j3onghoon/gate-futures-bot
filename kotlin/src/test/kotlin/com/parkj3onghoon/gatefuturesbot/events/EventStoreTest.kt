package com.parkj3onghoon.gatefuturesbot.events

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:eventstore-test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventStoreTest {
    @Autowired lateinit var store: EventStore

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbc.execute("DELETE FROM domain_events")
    }

    @Test
    fun `record persists an event and returns it with id`() {
        val result =
            store.record(
                type = EventTypes.POSITION_OPENED,
                contract = "BTC_USDT",
                payload = mapOf("size" to 1L, "price" to "50000"),
            )

        assertNotNull(result)
        assertNotNull(result.id)
        assertTrue(result.payload.contains("\"size\":1"))
    }

    @Test
    fun `findByContract returns events for that contract only`() {
        store.record(EventTypes.POSITION_OPENED, "BTC_USDT", mapOf("a" to 1))
        store.record(EventTypes.POSITION_OPENED, "ETH_USDT", mapOf("b" to 2))
        store.record(EventTypes.POSITION_CLOSED, "BTC_USDT", mapOf("c" to 3))

        val btc = store.findByContract("BTC_USDT")
        assertEquals(2, btc.size)
    }

    @Test
    fun `findByType returns events of that type across contracts`() {
        store.record(EventTypes.POSITION_OPENED, "BTC_USDT")
        store.record(EventTypes.POSITION_OPENED, "ETH_USDT")
        store.record(EventTypes.POSITION_CLOSED, "BTC_USDT")

        val opens = store.findByType(EventTypes.POSITION_OPENED)
        assertEquals(2, opens.size)
    }

    @Test
    fun `countByType`() {
        store.record(EventTypes.RISK_DENIED, "BTC_USDT")
        store.record(EventTypes.RISK_DENIED, "ETH_USDT")
        store.record(EventTypes.POSITION_OPENED, "BTC_USDT")

        assertEquals(2L, store.countByType(EventTypes.RISK_DENIED))
        assertEquals(1L, store.countByType(EventTypes.POSITION_OPENED))
    }

    @Test
    fun `order of findByContract is descending by occurredAt`() {
        store.record(EventTypes.POSITION_OPENED, "BTC_USDT", mapOf("idx" to 1))
        Thread.sleep(15)
        store.record(EventTypes.POSITION_CLOSED, "BTC_USDT", mapOf("idx" to 2))

        val events = store.findByContract("BTC_USDT")
        assertEquals(2, events.size)
        assertEquals(EventTypes.POSITION_CLOSED, events[0].type, "최신 이벤트가 먼저")
    }
}
