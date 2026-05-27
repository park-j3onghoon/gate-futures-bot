import logging

import pytest

from gatebot.domain.exceptions import MarketDataError
from gatebot.domain.model import Interval
from gatebot.service_layer import market_data


def test_get_candles_returns_exchange_candles(make_exchange, sample_candles):
    """정상 응답이면 거래소 캔들을 그대로 반환하고 인자를 전달한다."""
    # given
    exchange = make_exchange(sample_candles)

    # when
    result = market_data.get_candles(exchange, "BTC_USDT", Interval.MIN_1, limit=10)

    # then
    assert result == sample_candles
    assert exchange.calls == [("BTC_USDT", Interval.MIN_1, 10, 0, 0)]


def test_get_candles_warns_on_empty(make_exchange, caplog):
    """빈 응답이면 warning 로그를 남기고 빈 리스트를 반환한다."""
    # given
    exchange = make_exchange([])

    # when
    with caplog.at_level(logging.WARNING):
        result = market_data.get_candles(exchange, "BTC_USDT", Interval.MIN_5)

    # then
    assert result == []
    assert "빈 캔들 응답" in caplog.text


def test_get_latest_price_returns_last_close(make_exchange, sample_candles):
    """최신 가격은 마지막 캔들의 종가다."""
    # given
    exchange = make_exchange(sample_candles)

    # when
    price = market_data.get_latest_price(exchange, "BTC_USDT")

    # then
    assert price == 118.0


def test_get_latest_price_raises_when_no_data(make_exchange):
    """캔들이 없으면 MarketDataError를 던진다."""
    # given
    exchange = make_exchange([])

    # when / then
    with pytest.raises(MarketDataError):
        market_data.get_latest_price(exchange, "BTC_USDT")
