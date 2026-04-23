package com.parkj3onghoon.gatefuturesbot.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.parkj3onghoon.gatefuturesbot.events.EventStore
import com.parkj3onghoon.gatefuturesbot.events.EventTypes
import com.parkj3onghoon.gatefuturesbot.notification.NotificationDispatcher
import com.parkj3onghoon.gatefuturesbot.position.PositionTracker
import com.parkj3onghoon.gatefuturesbot.risk.RiskManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminController::class)
@Import(KillSwitch::class)
@TestPropertySource(properties = ["admin.token=secret123"])
class AdminControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var killSwitch: KillSwitch

    @MockkBean lateinit var positionTracker: PositionTracker

    @MockkBean lateinit var riskManager: RiskManager

    @MockkBean lateinit var eventStore: EventStore

    @MockkBean lateinit var notifier: NotificationDispatcher

    @org.junit.jupiter.api.BeforeEach
    fun reset() {
        killSwitch.reset()
    }

    @Test
    fun `status returns 401 without token`() {
        mockMvc
            .perform(get("/admin/status"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `status returns 401 with wrong token`() {
        mockMvc
            .perform(get("/admin/status").header("X-Admin-Token", "wrong"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `status returns current state with valid token`() {
        every { positionTracker.snapshot() } returns emptyMap()
        every { riskManager.snapshot() } returns RiskManager.Snapshot(0, 0.0, emptyMap())
        every { eventStore.countByType(EventTypes.POSITION_OPEN_FAILED) } returns 0
        every { eventStore.countByType(EventTypes.POSITION_OPENED) } returns 0

        mockMvc
            .perform(get("/admin/status").header("X-Admin-Token", "secret123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.killed").value(false))
    }

    @Test
    fun `stop trips the kill switch`() {
        every { eventStore.record(any(), any(), any()) } returns null
        every { notifier.dispatch(any()) } just Runs

        val body = objectMapper.writeValueAsString(mapOf("reason" to "market crash"))

        mockMvc
            .perform(
                post("/admin/stop")
                    .header("X-Admin-Token", "secret123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.killed").value(true))
            .andExpect(jsonPath("$.wasAlreadyTripped").value(false))

        verify { notifier.dispatch(any()) }
        verify { eventStore.record("KillSwitchTripped", any(), match { it["reason"] == "market crash" }) }
    }

    @Test
    fun `stop twice returns wasAlreadyTripped true`() {
        every { eventStore.record(any(), any(), any()) } returns null
        every { notifier.dispatch(any()) } just Runs
        val body = objectMapper.writeValueAsString(mapOf("reason" to "x"))

        mockMvc
            .perform(
                post("/admin/stop")
                    .header("X-Admin-Token", "secret123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isAccepted)

        mockMvc
            .perform(
                post("/admin/stop")
                    .header("X-Admin-Token", "secret123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.wasAlreadyTripped").value(true))
    }

    @Test
    fun `resume deactivates the kill switch`() {
        killSwitch.trip("manual")
        every { eventStore.record(any(), any(), any()) } returns null
        every { notifier.dispatch(any()) } just Runs

        mockMvc
            .perform(post("/admin/resume").header("X-Admin-Token", "secret123"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.resumed").value(true))
    }

    @Test
    fun `resetPosition calls tracker`() {
        every { positionTracker.resetToIdle("BTC_USDT") } just Runs
        every { eventStore.record(any(), any(), any()) } returns null

        mockMvc
            .perform(
                post("/admin/positions/BTC_USDT/reset")
                    .header("X-Admin-Token", "secret123"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("IDLE"))

        verify { positionTracker.resetToIdle("BTC_USDT") }
    }
}
