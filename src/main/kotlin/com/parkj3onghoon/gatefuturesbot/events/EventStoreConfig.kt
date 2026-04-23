package com.parkj3onghoon.gatefuturesbot.events

import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

/**
 * Event Sourcing 관련 Spring Data JDBC 설정.
 *
 * `@EnableJdbcRepositories`를 메인 애플리케이션 클래스 대신 여기에 둔 이유:
 * `@WebMvcTest`는 main class 상의 annotation은 그대로 처리하지만
 * 이 설정 클래스는 Web layer가 아니므로 WebMvcTest의 TypeExcludeFilter가 제외해준다.
 */
@Configuration
@EnableJdbcRepositories(basePackageClasses = [DomainEventRepository::class])
class EventStoreConfig
