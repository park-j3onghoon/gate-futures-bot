package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.bootstrap.StrategyAssembler
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
 */
@Component
class BotRunner(
    private val orchestrator: WorkerOrchestrator,
    private val strategyFactory: StrategyAssembler
) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)
    // IO dispatcher: 워커 루프는 대부분 HTTP 대기/delay라 IO 풀이 적합하다.
    // GateClient의 Thread.sleep도 IO 풀에서 다른 워커에 미치는 영향이 줄어든다.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (job != null) {
            logger.warn("BotRunner가 이미 실행 중")
            return
        }
        logger.info("BotRunner 시작")
        job = scope.launch {
            orchestrator.runAll { contract -> strategyFactory.forContract(contract) }
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
