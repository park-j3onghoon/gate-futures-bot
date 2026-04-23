package com.parkj3onghoon.gatefuturesbot.admin

import com.parkj3onghoon.gatefuturesbot.events.EventStore
import com.parkj3onghoon.gatefuturesbot.events.EventTypes
import com.parkj3onghoon.gatefuturesbot.notification.NotificationDispatcher
import com.parkj3onghoon.gatefuturesbot.notification.NotificationEvent
import com.parkj3onghoon.gatefuturesbot.notification.NotificationPriority
import com.parkj3onghoon.gatefuturesbot.position.PositionTracker
import com.parkj3onghoon.gatefuturesbot.risk.RiskManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 봇 운영 관리 API.
 * 기본 보호: 환경변수 `ADMIN_TOKEN`으로 간단 인증. 설정되지 않으면 모든 요청이 401.
 *
 * 엔드포인트:
 * - GET  /admin/status   — 현재 상태 스냅샷 (kill switch, positions, risk, event count)
 * - POST /admin/stop     — kill switch ON (진입/청산 모두 중단)
 * - POST /admin/resume   — kill switch OFF
 * - POST /admin/positions/{contract}/reset — FAILED → IDLE 수동 리셋
 */
@RestController
@RequestMapping("/admin")
class AdminController(
    private val killSwitch: KillSwitch,
    private val positionTracker: PositionTracker,
    private val riskManager: RiskManager,
    private val eventStore: EventStore,
    private val notifier: NotificationDispatcher,
    @Value("\${admin.token:}") private val adminToken: String,
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    @GetMapping("/status")
    fun status(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
    ): StatusResponse {
        authenticate(token)
        return StatusResponse(
            killed = killSwitch.isTripped(),
            killReason = killSwitch.tripReason(),
            positions = positionTracker.snapshot().mapValues { it.value.state.name },
            risk = riskManager.snapshot(),
            orderFailedCount = eventStore.countByType(EventTypes.POSITION_OPEN_FAILED),
            orderSucceededCount = eventStore.countByType(EventTypes.POSITION_OPENED),
        )
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun stop(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @RequestBody req: StopRequest,
    ): Map<String, Any> {
        authenticate(token)
        val toggled = killSwitch.trip(req.reason)
        if (toggled) {
            logger.warn("KILL SWITCH ACTIVATED: {}", req.reason)
            eventStore.record("KillSwitchTripped", payload = mapOf("reason" to req.reason))
            notifier.dispatch(
                NotificationEvent(
                    title = "🛑 Kill Switch 활성화",
                    message = "봇이 긴급 정지되었습니다.",
                    priority = NotificationPriority.CRITICAL,
                    tags = mapOf("reason" to req.reason),
                ),
            )
        }
        return mapOf("killed" to true, "wasAlreadyTripped" to !toggled)
    }

    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resume(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
    ): Map<String, Any> {
        authenticate(token)
        val toggled = killSwitch.reset()
        if (toggled) {
            logger.info("KILL SWITCH RESET")
            eventStore.record("KillSwitchReset")
            notifier.dispatch(
                NotificationEvent(
                    title = "▶️ Kill Switch 해제",
                    message = "봇 재개.",
                    priority = NotificationPriority.HIGH,
                ),
            )
        }
        return mapOf("resumed" to true, "wasAlreadyActive" to !toggled)
    }

    @PostMapping("/positions/{contract}/reset")
    fun resetPosition(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @org.springframework.web.bind.annotation.PathVariable contract: String,
    ): Map<String, Any> {
        authenticate(token)
        positionTracker.resetToIdle(contract)
        eventStore.record("PositionStateManualReset", contract)
        return mapOf("contract" to contract, "state" to "IDLE")
    }

    private fun authenticate(token: String?) {
        if (adminToken.isBlank()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "admin.token 미설정")
        }
        if (token == null || token != adminToken) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "잘못된 관리자 토큰")
        }
    }

    data class StatusResponse(
        val killed: Boolean,
        val killReason: String?,
        val positions: Map<String, String>,
        val risk: RiskManager.Snapshot,
        val orderFailedCount: Long,
        val orderSucceededCount: Long,
    )

    data class StopRequest(
        val reason: String,
    )
}
