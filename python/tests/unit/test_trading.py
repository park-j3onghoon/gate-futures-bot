import pytest

from gatebot.domain.exceptions import OrderError, PositionError
from gatebot.domain.model import OrderCommand, Position, Side
from gatebot.service_layer import trading


def _long(size=1, leverage=3, tp=None, sl=None) -> OrderCommand:
    return OrderCommand("BTC_USDT", Side.LONG, size, leverage, tp, sl)


def _short(size=1, leverage=3, tp=None, sl=None) -> OrderCommand:
    return OrderCommand("BTC_USDT", Side.SHORT, size, leverage, tp, sl)


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


# ---- open_position (명령어 매매: 롱/숏 + TP/SL 트리거) ----

def test_open_position_short_sends_negative_size(make_exchange):
    """숏 진입: side로 받은 양수 size를 음수로 변환해 create_order."""
    exchange = make_exchange()
    placed = trading.open_position(exchange, _short(size=2))
    assert ("create_order", "BTC_USDT", -2, 3) in exchange.orders
    assert placed.filled is True


def test_open_position_registers_sl_before_tp(make_exchange):
    """진입 후 SL 먼저, TP 나중 등록 (부분 성공 시 손절 우선 존재)."""
    exchange = make_exchange()
    placed = trading.open_position(exchange, _long(tp=110.0, sl=90.0))
    assert exchange.orders == [
        ("create_order", "BTC_USDT", 1, 3),
        ("create_trigger_order", "BTC_USDT", 90.0, True, False),  # SL
        ("create_trigger_order", "BTC_USDT", 110.0, True, True),  # TP
    ]
    assert placed.sl_trigger_id == 999
    assert placed.tp_trigger_id == 998


def test_open_position_with_only_tp_registers_single_trigger(make_exchange):
    """tp만 주면 트리거 1건(TP)만 등록, sl_trigger_id는 None."""
    exchange = make_exchange()
    placed = trading.open_position(exchange, _long(tp=110.0))
    assert placed.tp_trigger_id == 998
    assert placed.sl_trigger_id is None
    triggers = [o for o in exchange.orders if o[0] == "create_trigger_order"]
    assert len(triggers) == 1


def test_open_position_with_only_sl_registers_single_trigger(make_exchange):
    """sl만 주면 트리거 1건(SL)만 등록."""
    exchange = make_exchange()
    placed = trading.open_position(exchange, _long(sl=90.0))
    assert placed.sl_trigger_id == 999
    assert placed.tp_trigger_id is None


def test_open_position_unfilled_by_fill_price_skips_triggers(make_exchange):
    """[SR-1] fill_price='0'(IOC 미체결)이면 — create_order가 _position을 세팅(get_position 양성)해도
    응답 fill_price를 권위로 미체결 판정 → 트리거 미등록, filled=False."""
    exchange = make_exchange(entry_fill_price="0")
    placed = trading.open_position(exchange, _long(tp=110.0, sl=90.0))
    assert exchange.get_position("BTC_USDT") is not None  # create_order가 포지션을 세팅함
    assert placed.filled is False
    assert placed.sl_trigger_id is None and placed.tp_trigger_id is None
    assert not any(o[0] == "create_trigger_order" for o in exchange.orders)


def test_open_position_rejects_tp_sl_wrong_side_of_entry(make_exchange):
    """진입가 대비 방향 위반(롱 tp<=현재가) → create_order 전에 PositionError."""
    exchange = make_exchange(last_prices=["100"])
    with pytest.raises(PositionError):
        trading.open_position(exchange, _long(tp=95.0, sl=90.0))
    assert exchange.orders == []


def test_open_position_rejects_single_sided_tp_below_entry(make_exchange):
    """[SR-4] tp만 줘도 진입가 검증 (롱 tp<=현재가 → 거부)."""
    exchange = make_exchange(last_prices=["100"])
    with pytest.raises(PositionError):
        trading.open_position(exchange, _long(tp=95.0))
    assert exchange.orders == []


def test_open_position_rolls_back_when_sl_trigger_fails(make_exchange):
    """SL 트리거 등록 실패 → 성공 트리거 없음(cancel 없음) + close_position 롤백 + 예외."""
    exchange = make_exchange(trigger_fail_on="sl")
    with pytest.raises(OrderError):
        trading.open_position(exchange, _long(tp=110.0, sl=90.0))
    assert ("close_position", "BTC_USDT") in exchange.orders
    assert not any(o[0] == "cancel_trigger_order" for o in exchange.orders)


def test_open_position_rolls_back_when_tp_trigger_fails(make_exchange):
    """TP 실패 → 이미 등록된 SL(999) cancel + close_position 롤백."""
    exchange = make_exchange(trigger_fail_on="tp")
    with pytest.raises(OrderError):
        trading.open_position(exchange, _long(tp=110.0, sl=90.0))
    assert ("cancel_trigger_order", 999) in exchange.orders
    assert ("close_position", "BTC_USDT") in exchange.orders


def test_open_position_rolls_back_on_post_fill_price_cross(make_exchange):
    """체결 후 가격 급변으로 tp/sl이 현재가와 교차 → 롤백 + OrderError (트리거 미등록)."""
    exchange = make_exchange(last_prices=["100", "120"])  # entry=100 통과, 재조회 120
    with pytest.raises(OrderError):
        trading.open_position(exchange, _long(tp=110.0, sl=90.0))  # tp=110 <= 120 교차
    assert ("create_order", "BTC_USDT", 1, 3) in exchange.orders
    assert ("close_position", "BTC_USDT") in exchange.orders
    assert not any(o[0] == "create_trigger_order" for o in exchange.orders)


def test_open_position_raises_manual_intervention_when_rollback_fails(make_exchange):
    """트리거 실패 후 롤백의 close_position마저 실패 → '수동 개입' OrderError."""
    exchange = make_exchange(trigger_fail_on="sl", close_fail=True)
    with pytest.raises(OrderError, match="수동 개입"):
        trading.open_position(exchange, _long(tp=110.0, sl=90.0))


def test_open_position_escalates_when_rollback_cancel_fails(make_exchange):
    """[review CRITICAL] 롤백 중 cancel이 PositionError로 실패하면 close가 안 돌았으니
    '롤백 성공'으로 삼키지 말고 '수동 개입'으로 escalate해야 한다."""
    exchange = make_exchange(trigger_fail_on="tp", cancel_fail=True)  # SL 등록(999) 후 TP 실패 → SL cancel 시도
    with pytest.raises(OrderError, match="수동 개입"):
        trading.open_position(exchange, _long(tp=110.0, sl=90.0))


def test_open_position_accepts_leverage_up_to_cap(make_exchange):
    """레버리지 캡 10까지 허용."""
    exchange = make_exchange()
    placed = trading.open_position(exchange, _long(leverage=10))
    assert placed.filled is True


def test_open_position_rejects_leverage_over_cap(make_exchange):
    """레버리지 11(캡 10 초과) 거부, 주문 미발생."""
    exchange = make_exchange()
    with pytest.raises(OrderError):
        trading.open_position(exchange, _long(leverage=11))
    assert exchange.orders == []


def test_open_position_rejects_size_over_cap_by_magnitude(make_exchange):
    """숏 6계약(절대값 6 > 캡 5) 거부."""
    exchange = make_exchange()
    with pytest.raises(OrderError):
        trading.open_position(exchange, _short(size=6))
    assert exchange.orders == []


def test_rollback_skips_dry_run_sentinel_trigger_id(make_exchange):
    """dry-run sentinel(-1) 트리거 id는 cancel 시도하지 않고 close만."""
    exchange = make_exchange()
    trading._rollback(exchange, "BTC_USDT", [-1, None])
    assert not any(o[0] == "cancel_trigger_order" for o in exchange.orders)
    assert ("close_position", "BTC_USDT") in exchange.orders
