package com.parkj3onghoon.gatefuturesbot.position

/**
 * Position의 생명주기 상태.
 *
 * ```
 *  ┌──────────┐  open 시도  ┌─────────┐  체결 성공  ┌──────┐
 *  │  IDLE    │ ──────────> │ OPENING │ ─────────> │ OPEN │
 *  └──────────┘             └─────────┘            └──┬───┘
 *       ▲                        │                    │ close 시도
 *       │                        │ 실패               ▼
 *       │  리셋                  ▼                ┌─────────┐
 *       │                    ┌──────────┐        │ CLOSING │
 *       │                    │  FAILED  │        └────┬────┘
 *       │                    └──────────┘             │ 체결 성공
 *       │                                             ▼
 *       │                                         ┌────────┐
 *       └─────────────────────────────────────────│ CLOSED │
 *                                                 └────────┘
 * ```
 *
 * 유령 포지션 감지:
 * - OPENING 진입 후 2회 이상 iteration 동안 실제 거래소에 position이 안 보이면 FAILED
 * - OPEN 상태인데 거래소에 position이 사라졌으면 외부 청산 감지 → CLOSED로 전환
 */
enum class PositionState {
    IDLE,
    OPENING,
    OPEN,
    CLOSING,
    CLOSED,
    FAILED,
    ;

    fun canOpen(): Boolean = this == IDLE || this == CLOSED

    fun canClose(): Boolean = this == OPEN

    fun isTerminal(): Boolean = this == CLOSED || this == FAILED
}
