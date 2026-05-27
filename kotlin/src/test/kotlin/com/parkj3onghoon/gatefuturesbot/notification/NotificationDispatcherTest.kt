package com.parkj3onghoon.gatefuturesbot.notification

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class NotificationDispatcherTest {
    @Test
    fun `dispatches to all active channels meeting minimum priority`() =
        runBlocking {
            val ch1 = fakeChannel("a", NotificationPriority.NORMAL, enabled = true)
            val ch2 = fakeChannel("b", NotificationPriority.HIGH, enabled = true)
            val dispatcher = NotificationDispatcher(listOf(ch1, ch2))

            val event = NotificationEvent("t", "m", NotificationPriority.HIGH)
            dispatcher.dispatch(event)

            delay(50) // fire-and-forget 코루틴이 완료되도록
            coVerify(exactly = 1) { ch1.send(event) }
            coVerify(exactly = 1) { ch2.send(event) }
        }

    @Test
    fun `skips channels below event priority`() =
        runBlocking {
            // 이벤트는 LOW, 채널은 HIGH 이상만 받음
            val strict = fakeChannel("strict", NotificationPriority.HIGH, enabled = true)
            val relaxed = fakeChannel("relaxed", NotificationPriority.LOW, enabled = true)
            val dispatcher = NotificationDispatcher(listOf(strict, relaxed))

            val event = NotificationEvent("t", "m", NotificationPriority.LOW)
            dispatcher.dispatch(event)
            delay(50)

            coVerify(exactly = 0) { strict.send(any()) }
            coVerify(exactly = 1) { relaxed.send(event) }
        }

    @Test
    fun `skips disabled channels`() =
        runBlocking {
            val off = fakeChannel("off", NotificationPriority.NORMAL, enabled = false)
            val on = fakeChannel("on", NotificationPriority.NORMAL, enabled = true)
            val dispatcher = NotificationDispatcher(listOf(off, on))

            dispatcher.dispatch(NotificationEvent("t", "m"))
            delay(50)

            coVerify(exactly = 0) { off.send(any()) }
            coVerify(exactly = 1) { on.send(any()) }
        }

    @Test
    fun `one channel failure does not stop others`() =
        runBlocking {
            val broken = mockk<NotificationChannel>()
            coEvery { broken.enabled } returns true
            coEvery { broken.minimumPriority } returns NotificationPriority.LOW
            coEvery { broken.name } returns "broken"
            coEvery { broken.send(any()) } throws RuntimeException("boom")

            val healthy = fakeChannel("healthy", NotificationPriority.LOW, enabled = true)

            val dispatcher = NotificationDispatcher(listOf(broken, healthy))
            dispatcher.dispatch(NotificationEvent("t", "m"))
            delay(50)

            coVerify(exactly = 1) { broken.send(any()) }
            coVerify(exactly = 1) { healthy.send(any()) }
        }

    @Test
    fun `activeChannels lists only enabled`() {
        val a = fakeChannel("a", NotificationPriority.NORMAL, enabled = true)
        val b = fakeChannel("b", NotificationPriority.NORMAL, enabled = false)
        val dispatcher = NotificationDispatcher(listOf(a, b))
        assert(dispatcher.activeChannels() == listOf("a"))
    }

    private fun fakeChannel(
        channelName: String,
        priority: NotificationPriority,
        enabled: Boolean,
    ): NotificationChannel =
        mockk<NotificationChannel>().also {
            coEvery { it.name } returns channelName
            coEvery { it.minimumPriority } returns priority
            coEvery { it.enabled } returns enabled
            coEvery { it.send(any()) } just Runs
        }
}
