from unittest.mock import MagicMock

import pytest
from gate_api import FuturesCandlestick
from gate_api.exceptions import ApiException, GateApiException

from gatebot.adapters.exchange import GateExchange
from gatebot.domain.exceptions import MarketDataError
from gatebot.domain.model import Interval


def _sdk_candle(
    t: float | None = 1700000000.0,
    o: str | None = "100",
    h: str | None = "110",
    low: str | None = "95",
    c: str | None = "105",
    v: str | None = "42",
) -> FuturesCandlestick:
    return FuturesCandlestick(t=t, o=o, h=h, l=low, c=c, v=v)


def test_get_candles_maps_sdk_to_domain():
    """SDK FuturesCandlestick를 도메인 Candle로 매핑한다 (타입 변환 포함)."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.return_value = [_sdk_candle()]
    exchange = GateExchange(api, settle="usdt")

    # when
    candles = exchange.get_candles("BTC_USDT", Interval.MIN_1, limit=5)

    # then
    assert len(candles) == 1
    candle = candles[0]
    assert candle.timestamp == 1700000000
    assert candle.close == "105"
    assert candle.close_price == 105.0
    assert candle.volume == 42
    api.list_futures_candlesticks.assert_called_once_with(
        "usdt", "BTC_USDT", interval="1m", limit=5
    )


def test_get_candles_passes_from_to_without_limit():
    """from/to를 주면 _from·to 인자로 전달한다 (limit 미전달)."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.return_value = []
    exchange = GateExchange(api, settle="usdt")

    # when
    exchange.get_candles("ETH_USDT", Interval.HOUR_1, from_sec=1700000000, to_sec=1700003600)

    # then
    api.list_futures_candlesticks.assert_called_once_with(
        "usdt", "ETH_USDT", interval="1h", _from=1700000000, to=1700003600
    )


def test_to_candle_raises_on_missing_field():
    """필수 필드(close)가 없으면 MarketDataError."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.return_value = [_sdk_candle(c=None)]
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(MarketDataError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)


def test_get_candles_wraps_sdk_exception():
    """SDK 예외를 도메인 MarketDataError로 감싼다."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.side_effect = GateApiException(
        label="RATE_LIMIT",
        message="too many requests",
        exp=ApiException(status=429, reason="Too Many Requests"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(MarketDataError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)
