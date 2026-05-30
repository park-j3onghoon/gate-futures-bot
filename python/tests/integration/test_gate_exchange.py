from unittest.mock import MagicMock

import pytest
from gate_api import FuturesCandlestick, FuturesOrder
from gate_api.exceptions import ApiException, GateApiException

from gatebot.adapters.exchange import GateExchange
from gatebot.domain.exceptions import (
    AuthenticationError,
    InsufficientBalanceError,
    MarketDataError,
    OrderError,
    RateLimitError,
)
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


def test_rate_limit_label_maps_to_rate_limit_error():
    """RATE_LIMIT 라벨 SDK 예외 → RateLimitError (Kotlin mapGateException 동일)."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.side_effect = GateApiException(
        label="RATE_LIMIT",
        message="too many requests",
        exp=ApiException(status=429, reason="Too Many Requests"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(RateLimitError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)


def test_invalid_key_label_maps_to_authentication_error():
    """INVALID_KEY 라벨 → AuthenticationError."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.side_effect = GateApiException(
        label="INVALID_KEY",
        message="bad key",
        exp=ApiException(status=401, reason="Unauthorized"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(AuthenticationError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)


def test_balance_not_enough_label_maps_to_insufficient_balance():
    """BALANCE_NOT_ENOUGH 라벨 → InsufficientBalanceError."""
    # given
    api = MagicMock()
    api.create_futures_order.side_effect = GateApiException(
        label="BALANCE_NOT_ENOUGH",
        message="not enough",
        exp=ApiException(status=400, reason="Bad Request"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then — leverage 호출은 통과 (mock 기본 동작)
    with pytest.raises(InsufficientBalanceError):
        exchange.create_order("BTC_USDT", size=1, leverage=3)


def test_unknown_label_maps_to_order_error():
    """알 수 없는 라벨 → OrderError (fallback)."""
    api = MagicMock()
    api.list_futures_candlesticks.side_effect = GateApiException(
        label="WEIRD_LABEL",
        message="something",
        exp=ApiException(status=500, reason="Internal"),
    )
    exchange = GateExchange(api, settle="usdt")
    with pytest.raises(OrderError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)


def test_volume_defaults_to_zero_when_missing():
    """volume(v)이 None이면 0으로 둔다."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.return_value = [_sdk_candle(v=None)]
    exchange = GateExchange(api, settle="usdt")

    # when
    candles = exchange.get_candles("BTC_USDT", Interval.MIN_1)

    # then
    assert candles[0].volume == 0


def test_raises_on_non_numeric_volume():
    """volume(v)이 정수로 변환 불가하면 MarketDataError (SDK 경계 밖으로 ValueError 누출 방지)."""
    # given
    api = MagicMock()
    api.list_futures_candlesticks.return_value = [_sdk_candle(v="abc")]
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(MarketDataError):
        exchange.get_candles("BTC_USDT", Interval.MIN_1)


# ---- get_position / create_order / close_position ----

def _sdk_position(
    size: str | None = "3",
    leverage: str | None = "3",
    contract: str = "BTC_USDT",
    entry_price: str = "100",
):
    p = MagicMock()
    p.size = size
    p.leverage = leverage
    p.contract = contract
    p.entry_price = entry_price
    p.unrealised_pnl = "0"
    p.realised_pnl = "0"
    return p


def _sdk_order(
    id_: int = 42,
    contract: str = "BTC_USDT",
    size: str = "1",
    # SDK FuturesOrder 모델이 status를 "open"|"finished"만 허용 (client-side validation)
    status: str = "finished",
) -> FuturesOrder:
    return FuturesOrder(
        id=id_,
        contract=contract,
        size=size,
        status=status,
        fill_price="100",
        create_time=1700000000.0,
        price="0",
    )


def test_get_position_maps_sdk_to_domain():
    """SDK Position을 도메인 Position으로 매핑 (size·leverage str→int)."""
    # given
    api = MagicMock()
    api.get_position.return_value = _sdk_position(size="3", leverage="5")
    exchange = GateExchange(api, settle="usdt")

    # when
    pos = exchange.get_position("BTC_USDT")

    # then
    assert pos is not None
    assert pos.size == 3
    assert pos.leverage == 5
    assert pos.entry_price == "100"
    api.get_position.assert_called_once_with("usdt", "BTC_USDT")


def test_get_position_returns_none_when_size_zero():
    """size=0이면 '포지션 없음'으로 보고 None 반환 (Kotlin 동일)."""
    api = MagicMock()
    api.get_position.return_value = _sdk_position(size="0", leverage="5")
    exchange = GateExchange(api, settle="usdt")
    assert exchange.get_position("BTC_USDT") is None


def test_get_position_returns_none_when_size_missing():
    """size=None도 None 반환."""
    api = MagicMock()
    api.get_position.return_value = _sdk_position(size=None, leverage="5")
    exchange = GateExchange(api, settle="usdt")
    assert exchange.get_position("BTC_USDT") is None


def test_get_position_returns_none_on_position_not_found_label():
    """POSITION_NOT_FOUND 라벨은 에러가 아니라 '포지션 없음' → None (예외로 새지 않는다)."""
    # given
    api = MagicMock()
    api.get_position.side_effect = GateApiException(
        label="POSITION_NOT_FOUND",
        message="no position found",
        exp=ApiException(status=400, reason="Bad Request"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then
    assert exchange.get_position("BTC_USDT") is None


def test_get_position_reraises_non_whitelisted_label():
    """화이트리스트 밖 라벨은 흡수하지 않고 도메인 예외로 매핑한다 (none_on_labels 분기 검증)."""
    # given
    api = MagicMock()
    api.get_position.side_effect = GateApiException(
        label="INVALID_KEY",
        message="invalid key",
        exp=ApiException(status=400, reason="Bad Request"),
    )
    exchange = GateExchange(api, settle="usdt")

    # when / then
    with pytest.raises(AuthenticationError):
        exchange.get_position("BTC_USDT")


def test_create_order_sets_leverage_then_sends_market_order():
    """leverage 먼저 설정 후 시장가 주문 (Kotlin GateExchangeAdapter 패턴)."""
    # given
    api = MagicMock()
    api.create_futures_order.return_value = _sdk_order(id_=42, size="3")
    exchange = GateExchange(api, settle="usdt")

    # when
    result = exchange.create_order("BTC_USDT", size=3, leverage=3)

    # then
    api.update_position_leverage.assert_called_once_with("usdt", "BTC_USDT", "3")
    api.create_futures_order.assert_called_once()
    call_args = api.create_futures_order.call_args
    assert call_args[0][0] == "usdt"
    order = call_args[0][1]
    assert order.contract == "BTC_USDT"
    assert order.size == "3"  # SDK는 str로 받음
    assert order.price == "0"  # market
    assert order.tif == "ioc"
    assert order.close is False
    assert result.id == 42
    assert result.size == 3


def test_close_position_sends_close_flag():
    """청산은 close=True FuturesOrder로."""
    # given
    api = MagicMock()
    api.create_futures_order.return_value = _sdk_order(id_=43, size="0")

    exchange = GateExchange(api, settle="usdt")

    # when
    result = exchange.close_position("BTC_USDT")

    # then
    api.create_futures_order.assert_called_once()
    order = api.create_futures_order.call_args[0][1]
    assert order.contract == "BTC_USDT"
    assert order.close is True
    assert order.price == "0"
    assert order.tif == "ioc"
    assert result.id == 43
    assert result.status == "finished"
