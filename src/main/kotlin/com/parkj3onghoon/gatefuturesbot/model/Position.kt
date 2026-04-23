package com.parkj3onghoon.gatefuturesbot.model

/**
 * 포지션 도메인 모델.
 * SDK 타입과의 매핑은 GateClient가 담당한다.
 */
data class Position(
    val contract: String,
    val size: Long,
    val entryPrice: String,
    val leverage: Int,
    val unrealisedPnl: String,
    val realisedPnl: String,
)
