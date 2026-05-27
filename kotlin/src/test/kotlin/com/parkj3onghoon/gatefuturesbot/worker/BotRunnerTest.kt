package com.parkj3onghoon.gatefuturesbot.worker

import com.parkj3onghoon.gatefuturesbot.bootstrap.StrategyAssembler
import com.parkj3onghoon.gatefuturesbot.config.ApiProperties
import com.parkj3onghoon.gatefuturesbot.config.BotProperties
import com.parkj3onghoon.gatefuturesbot.config.StrategyProperties
import com.parkj3onghoon.gatefuturesbot.strategy.TradingStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test

class BotRunnerTest {
    private fun newRunner(orchestrator: WorkerOrchestrator): BotRunner {
        val assembler = mockk<StrategyAssembler>()
        every { assembler.forContract(any()) } returns TradingStrategy()
        return BotRunner(
            orchestrator = orchestrator,
            assembler = assembler,
            apiProperties = ApiProperties(host = "https://fx-api-testnet.gateio.ws/api/v4"),
            botProperties = BotProperties(),
            strategyProperties = StrategyProperties(),
        )
    }

    @Test
    fun `start launches orchestrator runAll`() {
        val orchestrator = mockk<WorkerOrchestrator>()
        coEvery { orchestrator.runAll(any()) } coAnswers { awaitCancellation() }

        val runner = newRunner(orchestrator)
        runner.start()
        Thread.sleep(20)

        coVerify(exactly = 1) { orchestrator.runAll(any()) }
        runner.stop()
    }

    @Test
    fun `start is idempotent when called twice`() {
        val orchestrator = mockk<WorkerOrchestrator>()
        coEvery { orchestrator.runAll(any()) } coAnswers { awaitCancellation() }

        val runner = newRunner(orchestrator)
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

        val runner = newRunner(orchestrator)
        runner.start()
        Thread.sleep(20)
        runner.stop()
    }

    @Test
    fun `stop is no-op when not started`() {
        val runner = newRunner(mockk())
        runner.stop()
    }
}
