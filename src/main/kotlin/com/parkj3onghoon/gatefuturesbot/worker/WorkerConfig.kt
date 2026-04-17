package com.parkj3onghoon.gatefuturesbot.worker

/**
 * CoinWorker의 런타임 설정값을 묶은 data class.
 * BotProperties(환경 설정)로부터 조립되며, CoinWorker의 생성자 파라미터 수를 줄인다.
 */
data class WorkerConfig(
    val orderSize: Int,
    val leverage: Int,
    val checkIntervalMillis: Long,
    val initialCandleLimit: Int = DEFAULT_INITIAL_CANDLE_LIMIT,
    val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE
) {
    init {
        require(orderSize > 0) { "orderSize must be positive: $orderSize" }
        require(leverage > 0) { "leverage must be positive: $leverage" }
        require(checkIntervalMillis > 0) { "checkIntervalMillis must be positive" }
        require(initialCandleLimit > 0) { "initialCandleLimit must be positive" }
        require(maxCacheSize >= initialCandleLimit) {
            "maxCacheSize($maxCacheSize) must be >= initialCandleLimit($initialCandleLimit)"
        }
    }

    companion object {
        const val DEFAULT_INITIAL_CANDLE_LIMIT: Int = 100
        const val DEFAULT_MAX_CACHE_SIZE: Int = 1000
    }
}
