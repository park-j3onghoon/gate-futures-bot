import pytest

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.exceptions import OrderError, PositionError
from gatebot.domain.model import Candle, Interval, OrderResult, Position


class FakeExchange(AbstractExchange):
    def __init__(
        self,
        candles: list[Candle] | None = None,
        position: Position | None = None,
        last_prices: list[str] | None = None,
        entry_fill_price: str = "100",
        trigger_fail_on: str | None = None,
        close_fail: bool = False,
        cancel_fail: bool = False,
    ) -> None:
        self._candles = list(candles or [])
        self._position = position
        # get_last_price 호출 순서대로 소비 — 진입 검증가와 등록 직전 재조회가 다른 값일 수 있어 시퀀스 주입.
        self._last_prices = list(last_prices or [])
        self._entry_fill_price = entry_fill_price
        # "sl"/"tp"/"both" 중 해당 트리거 등록을 실패시킨다 (롤백 경로 검증용).
        self._trigger_fail_on = trigger_fail_on
        self._close_fail = close_fail  # 롤백의 close_position까지 실패하는 경로 검증용
        self._cancel_fail = cancel_fail  # 롤백 중 cancel 실패가 silent 흡수되지 않는지 검증용
        self.calls: list[tuple] = []
        self.orders: list[tuple] = []

    def get_candles(
        self,
        contract: str,
        interval: Interval,
        limit: int = 0,
        from_sec: int = 0,
        to_sec: int = 0,
    ) -> list[Candle]:
        self.calls.append((contract, interval, limit, from_sec, to_sec))
        return list(self._candles)

    def get_position(self, contract: str) -> Position | None:
        return self._position

    def get_last_price(self, contract: str) -> str:
        if self._last_prices:
            return self._last_prices.pop(0)
        return "100"

    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        self.orders.append(("create_order", contract, size, leverage))
        # 체결된 것처럼 포지션 갱신 — 후속 get_position 검증 시뮬레이션 용
        self._position = Position(
            contract=contract,
            size=size,
            entry_price="100",
            leverage=leverage,
            unrealised_pnl="0",
            realised_pnl="0",
        )
        return OrderResult(
            id=1,
            contract=contract,
            size=size,
            price="0",
            status="filled",
            fill_price=self._entry_fill_price,
            create_time=0.0,
        )

    def create_trigger_order(
        self,
        contract: str,
        trigger_price: float,
        is_long: bool,
        is_take_profit: bool,
        price_type: int = 0,
    ) -> int:
        self.orders.append(
            ("create_trigger_order", contract, trigger_price, is_long, is_take_profit)
        )
        kind = "tp" if is_take_profit else "sl"
        if self._trigger_fail_on in (kind, "both"):
            raise OrderError(f"트리거 등록 실패(가짜): {kind}")
        return 998 if is_take_profit else 999

    def cancel_trigger_order(self, trigger_id: int) -> None:
        self.orders.append(("cancel_trigger_order", trigger_id))
        if self._cancel_fail:
            raise PositionError("트리거 취소 실패(가짜)")

    def close_position(self, contract: str) -> OrderResult:
        self.orders.append(("close_position", contract))
        if self._close_fail:
            raise OrderError("청산 실패(가짜)")
        prev_size = self._position.size if self._position else 0
        self._position = None
        return OrderResult(
            id=2,
            contract=contract,
            size=prev_size,
            price="0",
            status="closed",
            fill_price="100",
            create_time=0.0,
        )


@pytest.fixture
def sample_candles() -> list[Candle]:
    return [
        Candle(timestamp=1700000000, open="100", high="110", low="95", close="105", volume=10),
        Candle(timestamp=1700000060, open="105", high="120", low="104", close="118", volume=20),
    ]


@pytest.fixture
def make_exchange() -> type[FakeExchange]:
    return FakeExchange
