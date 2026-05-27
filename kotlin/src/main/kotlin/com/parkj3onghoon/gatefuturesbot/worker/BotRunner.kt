package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.bootstrap.StrategyAssembler
import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.config.BotProperties
import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Spring 앱 시작/종료에 맞춰 Orchestrator를 구동한다.
 *
 * - ApplicationReadyEvent 수신 시 백그라운드 코루틴에서 runAll 실행
 * - 컨텍스트 종료 시 scope를 cancel하여 모든 워커를 정리
 * - contract별 전략은 StrategyAssembler가 application.yml 설정에서 생성
 * - 시작 시 MAINNET 감지/no-op contract 경고 로깅 (실거래 사고 방지)
 */
@Component
class BotRunner(
    private val orchestrator: WorkerOrchestrator,
    private val assembler: StrategyAssembler,
    private val apiProperties: ApiProperties,
    private val botProperties: BotProperties,
    private val strategyProperties: StrategyProperties,
) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)

    // IO dispatcher: 워커 루프는 대부분 HTTP 대기/delay라 IO 풀이 적합하다.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (job != null) {
            logger.warn("BotRunner가 이미 실행 중")
            return
        }
        logEnvironmentBanner()
        logger.info("BotRunner 시작")
        job =
            scope.launch {
                orchestrator.runAll { contract -> assembler.forContract(contract) }
            }
    }

    private fun logEnvironmentBanner() {
        val mode = if (apiProperties.isTestnet) "TESTNET" else "MAINNET"
        logger.info(
            "환경: host={}, mode={}, contracts={}, configuredStrategies={}",
            apiProperties.host,
            mode,
            botProperties.contracts,
            strategyProperties.contracts.keys,
        )
        if (apiProperties.isProdWithKey) {
            logger.warn(
                "MAINNET(실거래) API 키가 바인딩되었습니다. 전략이 미정의인 contract는 no-op이지만, " +
                    "설정된 전략은 실계좌에 실주문을 발생시킵니다.",
            )
        }
        val undefined = botProperties.contracts.filter { it !in strategyProperties.contracts }
        if (undefined.isNotEmpty()) {
            logger.warn(
                "bot.contracts 중 strategy 설정 없어 no-op 실행될 contract: {}",
                undefined,
            )
        }
    }

    @PreDestroy
    fun stop() {
        val current = job ?: return
        logger.info("BotRunner 종료")
        runBlocking { current.cancelAndJoin() }
        scope.cancel()
        job = null
    }
}
