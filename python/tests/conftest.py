import pytest

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.model import Candle, Interval, OrderResult, Position


class FakeExchange(AbstractExchange):
    def __init__(
        self,
        candles: list[Candle] = [],
        position: Position | None = None,
    ) -> None:
        self._candles = list(candles)
        self._position = position
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
            fill_price="100",
            create_time=0.0,
        )

    def close_position(self, contract: str) -> OrderResult:
        self.orders.append(("close_position", contract))
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
