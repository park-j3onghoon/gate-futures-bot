import pytest

from gatebot.domain.exceptions import OrderError, PositionError
from gatebot.domain.model import Position
from gatebot.service_layer import trading


def test_open_long_creates_order_when_no_position(make_exchange):
    """포지션 없는 상태에서 정상 매수 → create_order 호출 + 결과 반환."""
    # given
    exchange = make_exchange()

    # when
    result = trading.open_long(exchange, "BTC_USDT", size=1, leverage=3)

    # then
    assert exchange.orders == [("create_order", "BTC_USDT", 1, 3)]
    assert result.size == 1
    assert result.status == "filled"


def test_open_long_rejects_when_position_already_exists(make_exchange):
    """이미 포지션 있으면 PositionError, create_order 호출 안 함."""
    # given
    existing = Position("BTC_USDT", 2, "100", 3, "0", "0")
    exchange = make_exchange(position=existing)

    # when / then
    with pytest.raises(PositionError):
        trading.open_long(exchange, "BTC_USDT", size=1, leverage=3)
    assert exchange.orders == []


def test_open_long_rejects_size_over_cap(make_exchange):
    """안전 캡(max_size) 초과 size는 OrderError."""
    # given
    exchange = make_exchange()

    # when / then
    with pytest.raises(OrderError):
        trading.open_long(exchange, "BTC_USDT", size=100, leverage=3)
    assert exchange.orders == []


def test_open_long_rejects_leverage_over_cap(make_exchange):
    """안전 캡(max_leverage) 초과 leverage는 OrderError."""
    # given
    exchange = make_exchange()

    # when / then
    with pytest.raises(OrderError):
        trading.open_long(exchange, "BTC_USDT", size=1, leverage=100)
    assert exchange.orders == []


def test_open_long_rejects_non_positive_size(make_exchange):
    """size <= 0 거부."""
    exchange = make_exchange()
    with pytest.raises(OrderError):
        trading.open_long(exchange, "BTC_USDT", size=0, leverage=3)
    with pytest.raises(OrderError):
        trading.open_long(exchange, "BTC_USDT", size=-1, leverage=3)


def test_open_long_rejects_non_positive_leverage(make_exchange):
    """leverage <= 0 거부."""
    exchange = make_exchange()
    with pytest.raises(OrderError):
        trading.open_long(exchange, "BTC_USDT", size=1, leverage=0)


def test_close_calls_close_position_when_position_exists(make_exchange):
    """포지션 있으면 close_position 호출."""
    # given
    existing = Position("BTC_USDT", 1, "100", 3, "0", "0")
    exchange = make_exchange(position=existing)

    # when
    result = trading.close(exchange, "BTC_USDT")

    # then
    assert exchange.orders == [("close_position", "BTC_USDT")]
    assert result.status == "closed"


def test_close_raises_when_no_position(make_exchange):
    """포지션 없는데 청산 시도 → PositionError."""
    exchange = make_exchange()
    with pytest.raises(PositionError):
        trading.close(exchange, "BTC_USDT")
    assert exchange.orders == []
