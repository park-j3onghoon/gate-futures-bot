package com.parkj3onghoon.gatefuturesbot.model

/**
 * Gate.io 선물 주문 결과 도메인 모델.
 * SDK 타입(FuturesOrder)과의 매핑은 GateClient가 담당한다.
 */
data class OrderResult(
    val id: Long,
    val contract: String,
    val size: Long,
    val price: String,
    val status: String,
    val fillPrice: String,
    val createTime: Double
)
