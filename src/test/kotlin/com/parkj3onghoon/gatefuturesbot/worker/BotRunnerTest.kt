package com.parkj3onghoon.gatefuturesbot.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test

class BotRunnerTest {

    @Test
    fun `start launches orchestrator runAll`() {
        val orchestrator = mockk<WorkerOrchestrator>()
        coEvery { orchestrator.runAll(any()) } coAnswers { awaitCancellation() }

        val runner = BotRunner(orchestrator)
        runner.start()
        Thread.sleep(20)

        coVerify(exactly = 1) { orchestrator.runAll(any()) }
        runner.stop()
    }

    @Test
    fun `start is idempotent when called twice`() {
        val orchestrator = mockk<WorkerOrchestrator>()
        coEvery { orchestrator.runAll(any()) } coAnswers { awaitCancellation() }

        val runner = BotRunner(orchestrator)
        runner.start()
        runner.start()
        Thread.sleep(20)

        coVerify(exactly = 1) { orchestrator.runAll(any()) }
        runner.stop()
    }

    @Test
    fun `stop cancels running job`() {
        val orchestrator = mockk<WorkerOrchestrator>()
        coEvery { orchestrator.runAll(any()) } coAnswers { delay(Long.MAX_VALUE) }

        val runner = BotRunner(orchestrator)
        runner.start()
        Thread.sleep(20)
        runner.stop()
        // stop()이 blocking으로 cancelAndJoin 수행 → 완료 보장
    }

    @Test
    fun `stop is no-op when not started`() {
        val runner = BotRunner(mockk())
        runner.stop()
    }
}
