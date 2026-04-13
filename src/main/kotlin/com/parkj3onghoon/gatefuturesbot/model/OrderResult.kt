package com.parkj3onghoon.gatefuturesbot.model

import io.gate.gateapi.models.FuturesOrder

data class OrderResult(
    val id: Long,
    val contract: String,
    val size: Long,
    val price: String,
    val status: String,
    val fillPrice: String,
    val createTime: Double
) {
    companion object {
        fun from(order: FuturesOrder): OrderResult = OrderResult(
            id = order.id ?: 0L,
            contract = order.contract ?: "",
            size = order.size ?: 0L,
            price = order.price ?: "0",
            status = order.status?.value ?: "unknown",
            fillPrice = order.fillPrice ?: "0",
            createTime = order.createTime ?: 0.0
        )
    }
}
