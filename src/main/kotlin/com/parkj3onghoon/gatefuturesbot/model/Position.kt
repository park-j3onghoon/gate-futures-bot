package com.parkj3onghoon.gatefuturesbot.model

import io.gate.gateapi.models.Position as GatePosition

data class Position(
    val contract: String,
    val size: Long,
    val entryPrice: String,
    val leverage: Int,
    val unrealisedPnl: String,
    val realisedPnl: String
) {
    companion object {
        fun from(pos: GatePosition): Position = Position(
            contract = pos.contract ?: "",
            size = pos.size ?: 0L,
            entryPrice = pos.entryPrice ?: "0",
            leverage = pos.leverage?.toIntOrNull() ?: 0,
            unrealisedPnl = pos.unrealisedPnl ?: "0",
            realisedPnl = pos.realisedPnl ?: "0"
        )
    }
}
