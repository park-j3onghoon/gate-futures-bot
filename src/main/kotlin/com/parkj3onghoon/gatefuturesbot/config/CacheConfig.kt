package com.parkj3onghoon.gatefuturesbot.config

import com.parkj3onghoon.gatefuturesbot.market.IndicatorCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 프로세스 공유 캐시 Bean.
 * Caffeine 기반. 모든 워커가 동일 캐시 인스턴스를 공유해 hit rate 극대화.
 */
@Configuration
class CacheConfig {
    @Bean
    fun indicatorCache(): IndicatorCache = IndicatorCache()
}
