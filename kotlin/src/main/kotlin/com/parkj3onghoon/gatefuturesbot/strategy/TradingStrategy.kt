package com.parkj3onghoon.gatefuturesbot.strategy

import com.parkj3onghoon.gatefuturesbot.model.Candle
import com.parkj3onghoon.gatefuturesbot.model.Position

sealed class EntrySignal {
    data object None : EntrySignal()

    data class Long(
        val matched: List<EntryCondition>,
    ) : EntrySignal()

    data class Short(
        val matched: List<EntryCondition>,
    ) : EntrySignal()
}

/**
 * 롱/숏 진입 조건을 평가해 시그널을 반환한다.
 *
 * - 각 side(long/short)의 진입 조건들은 AND로 결합된다.
 * - longEntries와 shortEntries가 동시에 충족되면 Long을 우선한다 (전략 설계 오류일 수 있음).
 * - 조건 리스트가 비어있으면 해당 side는 시그널을 내지 않는다.
 * - 청산 조건(exitConditions)은 OR로 결합된다: 하나라도 충족 시 Close 시그널.
 * - Spring 의존 없는 순수 클래스: 테스트/백테스트에서 자유롭게 생성 가능.
 */
class TradingStrategy(
    private val longEntries: List<EntryCondition> = emptyList(),
    private val shortEntries: List<EntryCondition> = emptyList(),
    private val exitConditions: List<ExitCondition> = emptyList(),
) {
    fun evaluateEntry(candles: List<Candle>): EntrySignal {
        if (candles.isEmpty()) return EntrySignal.None
        val prices = candles.map { it.closePrice }

        if (longEntries.isNotEmpty() && longEntries.all { it.evaluate(prices) }) {
            return EntrySignal.Long(longEntries)
        }
        if (shortEntries.isNotEmpty() && shortEntries.all { it.evaluate(prices) }) {
            return EntrySignal.Short(shortEntries)
        }
        return EntrySignal.None
    }

    fun evaluateExit(
        candles: List<Candle>,
        position: Position,
    ): ExitSignal {
        if (candles.isEmpty() || exitConditions.isEmpty()) return ExitSignal.None
        val prices = candles.map { it.closePrice }
        val triggered = exitConditions.filter { it.evaluate(prices, position) }
        return if (triggered.isNotEmpty()) ExitSignal.Close(triggered) else ExitSignal.None
    }
}
