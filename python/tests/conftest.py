import pytest

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.model import Candle, Interval


class FakeExchange(AbstractExchange):
    def __init__(self, candles: list[Candle] = []) -> None:
        self._candles = list(candles)
        self.calls: list[tuple[str, Interval, int, int, int]] = []

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


@pytest.fixture
def sample_candles() -> list[Candle]:
    return [
        Candle(timestamp=1700000000, open="100", high="110", low="95", close="105", volume=10),
        Candle(timestamp=1700000060, open="105", high="120", low="104", close="118", volume=20),
    ]


@pytest.fixture
def make_exchange() -> type[FakeExchange]:
    return FakeExchange
