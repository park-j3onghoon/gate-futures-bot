package com.parkj3onghoon.gatefuturesbot.events

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface DomainEventRepository : CrudRepository<DomainEvent, Long> {
    fun findAllByContractOrderByOccurredAtDesc(contract: String): List<DomainEvent>

    fun findAllByTypeOrderByOccurredAtDesc(type: String): List<DomainEvent>

    @Query("SELECT * FROM domain_events WHERE occurred_at >= :since ORDER BY occurred_at DESC")
    fun findSince(since: Instant): List<DomainEvent>

    fun countByType(type: String): Long
}
