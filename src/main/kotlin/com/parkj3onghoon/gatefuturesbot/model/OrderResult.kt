package com.parkj3onghoon.gatefuturesbot.model

import com.parkj3onghoon.gatefuturesbot.exception.OrderException
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
            id = order.id ?: throw OrderException("주문 응답에 id가 없습니다"),
            contract = order.contract ?: throw OrderException("주문 응답에 contract가 없습니다"),
            size = order.size ?: throw OrderException("주문 응답에 size가 없습니다"),
            price = order.price ?: "0",
            status = order.status?.value ?: "unknown",
            fillPrice = order.fillPrice ?: "0",
            createTime = order.createTime ?: 0.0
        )
    }
}
