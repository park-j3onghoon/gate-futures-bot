package com.parkj3onghoon.gatefuturesbot.admin

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 전역 긴급 정지 플래그.
 *
 * CoinWorker가 매 iteration 시작 시 `isTripped()`를 확인해 trip 상태면 진입/청산 모두 skip.
 * `trip()`은 사람이 Admin API로 호출.
 *
 * 단순한 AtomicBoolean이지만 **의도를 명명한 도메인 객체**로 포장해
 * 다른 "boolean flag"들과 혼동 방지 + 테스트 주입 용이.
 */
@Component
class KillSwitch {
    private val tripped = AtomicBoolean(false)
    private val reasonRef =
        java.util.concurrent.atomic
            .AtomicReference<String?>(null)

    fun isTripped(): Boolean = tripped.get()

    fun tripReason(): String? = reasonRef.get()

    /** 긴급 정지 활성화. 이미 켜져 있으면 false 반환. */
    fun trip(reason: String): Boolean {
        reasonRef.set(reason)
        return tripped.compareAndSet(false, true)
    }

    /** 복구 (수동 재개). 이미 꺼져 있으면 false. */
    fun reset(): Boolean {
        reasonRef.set(null)
        return tripped.compareAndSet(true, false)
    }
}
